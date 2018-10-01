package homecontroller.domain.service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;

import javax.annotation.PostConstruct;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import homecontroller.dao.ModelDAO;
import homecontroller.database.mapper.BigDecimalRowMapper;
import homecontroller.database.mapper.TimestampRowMapper;
import homecontroller.domain.model.HeatingModel;
import homecontroller.domain.model.Hint;
import homecontroller.domain.model.HistoryModel;
import homecontroller.domain.model.HouseModel;
import homecontroller.domain.model.Intensity;
import homecontroller.domain.model.SwitchModel;
import homecontroller.service.HomematicAPI;
import homecontroller.service.PushService;

@Component
public class HouseService {

	private final static BigDecimal TARGET_TEMPERATURE_INSIDE = new BigDecimal("22");

	private final static BigDecimal SUN_INTENSITY_NO = new BigDecimal("3");
	private final static BigDecimal SUN_INTENSITY_LOW = new BigDecimal("8");
	private final static BigDecimal SUN_INTENSITY_MEDIUM = new BigDecimal("15");

	private final static BigDecimal HEATING_CONTROL_MODE_BOOST = new BigDecimal(3);

	private final static Object REFRESH_MONITOR = new Object();
	private final static long REFRESH_TIMEOUT = 5 * 1000; // 5 sec

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private HomematicAPI api;

	@Autowired
	private PushService pushService;

	@PostConstruct
	public void init() {

		try {
			refreshHouseModel(false);
			refreshHistoryModelComplete();
		} catch (Exception e) {
			LogFactory.getLog(HouseService.class).error("Could not initialize HouseService completly.", e);
		}
	}

	@Scheduled(fixedDelay = (1000 * 60))
	private void scheduledRefreshHouseModel() {
		refreshHouseModel(false);
	}

	public void refreshHouseModel(boolean notify) {

		HouseModel oldModel = ModelDAO.getInstance().readHouseModel();

		HouseModel newModel = refreshModel();
		calculateConclusion(newModel);
		ModelDAO.getInstance().write(newModel);

		if (notify) {
			synchronized (REFRESH_MONITOR) {
				REFRESH_MONITOR.notify();
			}
		}

		pushService.send(oldModel, newModel);
	}

	@Scheduled(cron = "5 0 0 * * *")
	private void refreshHistoryModelComplete() {

		HistoryModel newModel = new HistoryModel();
		List<Timestamp> timestamps = jdbcTemplate.query(
				"select formatdatetime(ts, 'yyyy_MM') as month, max(ts) as last FROM D_BIDCOS_RF_NEQ0861520_1_ENERGY_COUNTER group by month order by month asc;",
				new Object[] {}, new TimestampRowMapper("last"));
		for (Timestamp timestamp : timestamps) {
			BigDecimal value = jdbcTemplate.queryForObject(
					"select value FROM D_BIDCOS_RF_NEQ0861520_1_ENERGY_COUNTER where ts = ?;",
					new Object[] { timestamp }, new BigDecimalRowMapper("value"));
			newModel.getMonthlyPowerConsumption().put(timestamp.getTime(), value);
		}
		ModelDAO.getInstance().write(newModel);
	}

	@Scheduled(cron = "0 2/3 * * * *")
	private void refreshHistoryModel() {

		HistoryModel model = ModelDAO.getInstance().readHistoryModel();
		if (model == null) {
			return;
		}

		if (model.getMonthlyPowerConsumption() == null || model.getMonthlyPowerConsumption().isEmpty()) {
			refreshHistoryModelComplete();
			return;
		}

		Timestamp timestamp = jdbcTemplate.queryForObject(
				"select max(ts) as time from D_BIDCOS_RF_NEQ0861520_1_ENERGY_COUNTER;",
				new TimestampRowMapper("time"));

		Entry<Long, BigDecimal> lastElement = null;
		LinkedHashMap<Long, BigDecimal> map = (LinkedHashMap<Long, BigDecimal>) model
				.getMonthlyPowerConsumption();
		Iterator<Entry<Long, BigDecimal>> iterator = map.entrySet().iterator();
		HashMap<Long, BigDecimal> newMap = new LinkedHashMap<Long, BigDecimal>();
		while (iterator.hasNext()) {
			lastElement = iterator.next();
			if (iterator.hasNext()) {
				newMap.put(lastElement.getKey(), lastElement.getValue());
			}
		}

		if (timestamp.getTime() > lastElement.getKey()) {
			BigDecimal value = jdbcTemplate.queryForObject(
					"select value FROM D_BIDCOS_RF_NEQ0861520_1_ENERGY_COUNTER where ts = ?;",
					new Object[] { timestamp }, new BigDecimalRowMapper("value"));
			if (isSameMonth(timestamp, new Date(lastElement.getKey()))) {
				newMap.put(timestamp.getTime(), value);
				model.setMonthlyPowerConsumption(newMap);
			} else {
				model.getMonthlyPowerConsumption().put(timestamp.getTime(), value);
			}
		}
	}

	private boolean isSameMonth(Date date1, Date date2) {

		Calendar cal1 = Calendar.getInstance();
		cal1.setTime(date1);
		Calendar cal2 = Calendar.getInstance();
		cal2.setTime(date2);

		if (cal1 == null || cal2 == null) {
			return false;
		}

		return cal1.get(Calendar.ERA) == cal2.get(Calendar.ERA)
				&& cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR)
				&& cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH);
	}

	private HouseModel refreshModel() {

		api.refresh();

		HouseModel newModel = new HouseModel();

		newModel.setBathRoomTemperature(readActTemperature("BidCos-RF.OEQ0854602", "4"));
		newModel.setBathRoomHeating(readHeating("BidCos-RF.OEQ0854602", "4", "ThermostatBad"));

		newModel.setKidsRoomTemperature(readActTemperature("HmIP-RF.000E97099314A3", "1"));
		newModel.setKidsRoomHumidity(readHumidity("HmIP-RF.000E97099314A3", "1"));

		newModel.setLivingRoomTemperature(readActTemperature("HmIP-RF.000E97099312D5", "1"));
		newModel.setLivingRoomHumidity(readHumidity("HmIP-RF.000E97099312D5", "1"));

		newModel.setBedRoomTemperature(readActTemperature("HmIP-RF.000E97099314C4", "1"));
		newModel.setBedRoomHumidity(readHumidity("HmIP-RF.000E97099314C4", "1"));

		newModel.setTerraceTemperature(readTemperature("BidCos-RF.OEQ0801741", "2"));
		newModel.setTerraceSunHeatingDiff(readTemperature("BidCos-RF.OEQ0801741", "3"));

		newModel.setEntranceTemperature(readTemperature("BidCos-RF.OEQ0801807", "2"));
		newModel.setEntranceSunHeatingDiff(readTemperature("BidCos-RF.OEQ0801807", "3"));

		newModel.setKitchenWindowLightSwitch(readSwitchState("BidCos-RF.OEQ0712456", "1"));

		newModel.setHouseElectricalPowerConsumption(readPowerConsumption("BidCos-RF.NEQ0861520", "1"));

		checkLowBattery(newModel, "Thermostad Badezimmer", "BidCos-RF.OEQ0854602", "0");

		return newModel;
	}

	public void calculateConclusion(HouseModel newModel) {

		if (newModel.getTerraceTemperature().compareTo(newModel.getEntranceTemperature()) < 0) {
			// min
			newModel.setConclusionFacadeMinTemp(newModel.getTerraceTemperature());
			newModel.setConclusionFacadeMinTempName("Terrasse");
			// max
			newModel.setConclusionFacadeMaxTemp(newModel.getEntranceTemperature());
			newModel.setConclusionFacadeMaxTempName("Einfahrt");
			newModel.setConclusionFacadeMaxTempSunHeating(newModel.getEntranceSunHeatingDiff());
		} else {
			// min
			newModel.setConclusionFacadeMinTemp(newModel.getEntranceTemperature());
			newModel.setConclusionFacadeMinTempName("Einfahrt");
			// max
			newModel.setConclusionFacadeMaxTemp(newModel.getTerraceTemperature());
			newModel.setConclusionFacadeMaxTempName("Terrasse");
			newModel.setConclusionFacadeMaxTempSunHeating(newModel.getTerraceSunHeatingDiff());
		}

		newModel.setConclusionFacadeSidesDifference(
				newModel.getConclusionFacadeMaxTemp().subtract(newModel.getConclusionFacadeMinTemp()).abs());

		newModel.setConclusionFacadeMaxTempSunIntensity(
				lookupIntensity(newModel.getConclusionFacadeMaxTempSunHeating()));
		newModel.setConclusionFacadeMaxTempHeatingIntensity(
				lookupIntensity(newModel.getConclusionFacadeSidesDifference()));

		newModel.setConclusionHintKidsRoom(
				new Hint(lookupHint(newModel.getKidsRoomTemperature(), newModel.getEntranceTemperature(),
						lookupIntensity(newModel.getEntranceSunHeatingDiff())), "Kinderzimmer"));
		newModel.setConclusionHintBathRoom(
				new Hint(lookupHint(newModel.getBathRoomTemperature(), newModel.getEntranceTemperature(),
						lookupIntensity(newModel.getEntranceSunHeatingDiff())), "Badezimmer"));
		newModel.setConclusionHintBedRoom(
				new Hint(lookupHint(newModel.getBedRoomTemperature(), newModel.getTerraceTemperature(),
						lookupIntensity(newModel.getTerraceSunHeatingDiff())), "Schlafzimmer"));
		newModel.setConclusionHintLivingRoom(
				new Hint(lookupHint(newModel.getLivingRoomTemperature(), newModel.getTerraceTemperature(),
						lookupIntensity(newModel.getTerraceSunHeatingDiff())), "Wohnzimmer"));

	}

	private String lookupHint(BigDecimal insideTemperature, BigDecimal outsideTemperature,
			Intensity sunIntensity) {

		if (insideTemperature == null) {
			return null;
		} else if (insideTemperature.compareTo(TARGET_TEMPERATURE_INSIDE) < 0) {
			// TODO: using sun heating in the winter for warming up rooms
			return null;
		} else if (insideTemperature.compareTo(TARGET_TEMPERATURE_INSIDE) > 0
				&& outsideTemperature.compareTo(insideTemperature) < 0
				&& sunIntensity.ordinal() <= Intensity.LOW.ordinal()) {
			return "Fenster öffnen";
		} else if (insideTemperature.compareTo(TARGET_TEMPERATURE_INSIDE) > 0
				&& sunIntensity.ordinal() > Intensity.LOW.ordinal()) {
			return "Rolladen schließen";
		}

		return null;
	}

	private Intensity lookupIntensity(BigDecimal value) {
		if (value.compareTo(SUN_INTENSITY_NO) < 0) {
			return Intensity.NO;
		} else if (value.compareTo(SUN_INTENSITY_LOW) < 0) {
			return Intensity.LOW;
		} else if (value.compareTo(SUN_INTENSITY_MEDIUM) < 0) {
			return Intensity.MEDIUM;
		} else {
			return Intensity.HIGH;
		}
	}

	public void toggle(String devIdVar) throws Exception {
		api.toggleBooleanState(devIdVar);
		refreshHouseModel(false);
	}

	public synchronized void heatingBoost(String prefix) throws Exception {
		api.runProgram(prefix + "Boost");
		synchronized (REFRESH_MONITOR) {
			REFRESH_MONITOR.wait(REFRESH_TIMEOUT);
		}
	}

	// needs to be synchronized because of using ccu-systemwide temperature
	// variable
	public synchronized void heatingManual(String prefix, String temperature) throws Exception {
		temperature = StringUtils.replace(temperature, ",", "."); // decimalpoint
		api.changeValue(prefix + "Temperature", temperature);
		api.runProgram(prefix + "Manual");
		synchronized (REFRESH_MONITOR) {
			REFRESH_MONITOR.wait(REFRESH_TIMEOUT);
		}
	}

	private BigDecimal readTemperature(String device, String chanel) {
		return api.getAsBigDecimal(device + ":" + chanel + ".TEMPERATURE");
	}

	private BigDecimal readActTemperature(String device, String chanel) {
		return api.getAsBigDecimal(device + ":" + chanel + ".ACTUAL_TEMPERATURE");
	}

	private BigDecimal readHumidity(String device, String chanel) {
		return api.getAsBigDecimal(device + ":" + chanel + ".HUMIDITY");
	}

	private HeatingModel readHeating(String device, String chanel, String programNamePrefix) {
		HeatingModel model = new HeatingModel();
		model.setBoostActive(api.getAsBigDecimal(device + ":" + chanel + ".CONTROL_MODE")
				.compareTo(HEATING_CONTROL_MODE_BOOST) == 0);
		model.setBoostMinutesLeft(api.getAsBigDecimal(device + ":" + chanel + ".BOOST_STATE").intValue());
		model.setTargetTemperature(api.getAsBigDecimal(device + ":" + chanel + ".SET_TEMPERATURE"));
		model.setProgramNamePrefix(programNamePrefix);
		return model;
	}

	private SwitchModel readSwitchState(String device, String chanel) {
		SwitchModel switchModel = new SwitchModel();
		String devIdVar = device + ":" + chanel + ".STATE";
		switchModel.setState(api.getAsBoolean(devIdVar));
		switchModel.setDeviceIdVar(devIdVar);
		return switchModel;
	}

	private int readPowerConsumption(String device, String chanel) {
		return api.getAsBigDecimal(device + ":" + chanel + ".POWER").intValue();
	}

	private void checkLowBattery(HouseModel model, String name, String device, String chanel) {
		Boolean state = api.getAsBoolean(device + ":" + chanel + ".LOWBAT");
		if (state != null && state == false) {
			model.getLowBatteryDevices().add(name);
		}

	}
}

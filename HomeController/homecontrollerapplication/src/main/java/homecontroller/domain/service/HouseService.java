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
import homecontroller.domain.model.Datapoint;
import homecontroller.domain.model.Device;
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
				"select formatdatetime(ts, 'yyyy_MM') as month, max(ts) as last FROM "
						+ Device.STROMZAEHLER.accessKeyHistorian(Datapoint.ENERGY_COUNTER)
						+ " group by month order by month asc;",
				new Object[] {}, new TimestampRowMapper("last"));
		for (Timestamp timestamp : timestamps) {
			BigDecimal value = jdbcTemplate.queryForObject("select value FROM "
					+ Device.STROMZAEHLER.accessKeyHistorian(Datapoint.ENERGY_COUNTER) + " where ts = ?;",
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
				"select max(ts) as time from "
						+ Device.STROMZAEHLER.accessKeyHistorian(Datapoint.ENERGY_COUNTER) + ";",
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
			BigDecimal value = jdbcTemplate.queryForObject("select value FROM "
					+ Device.STROMZAEHLER.accessKeyHistorian(Datapoint.ENERGY_COUNTER) + " where ts = ?;",
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

		newModel.setBathRoomTemperature(readActTemperature(Device.THERMOSTAT_BAD));
		newModel.setBathRoomHeating(readHeating(Device.THERMOSTAT_BAD));

		newModel.setKidsRoomTemperature(readActTemperature(Device.THERMOMETER_KINDERZIMMER));
		newModel.setKidsRoomHumidity(readHumidity(Device.THERMOMETER_KINDERZIMMER));

		newModel.setLivingRoomTemperature(readActTemperature(Device.THERMOMETER_WOHNZIMMER));
		newModel.setLivingRoomHumidity(readHumidity(Device.THERMOMETER_WOHNZIMMER));

		newModel.setBedRoomTemperature(readActTemperature(Device.THERMOMETER_SCHLAFZIMMER));
		newModel.setBedRoomHumidity(readHumidity(Device.THERMOMETER_SCHLAFZIMMER));

		newModel.setTerraceTemperature(readTemperature(Device.DIFFERENZTEMPERATUR_TERRASSE_AUSSEN));
		newModel.setTerraceSunHeatingDiff(readTemperature(Device.DIFFERENZTEMPERATUR_TERRASSE_DIFF));

		newModel.setEntranceTemperature(readTemperature(Device.DIFFERENZTEMPERATUR_EINFAHRT_AUSSEN));
		newModel.setEntranceSunHeatingDiff(readTemperature(Device.DIFFERENZTEMPERATUR_EINFAHRT_DIFF));

		newModel.setKitchenWindowLightSwitch(readSwitchState(Device.SCHALTER_KUECHE_LICHT));

		newModel.setHouseElectricalPowerConsumption(readPowerConsumption(Device.STROMZAEHLER));

		checkLowBattery(newModel, Device.THERMOSTAT_BAD);
		checkLowBattery(newModel, Device.THERMOMETER_SCHLAFZIMMER);
		checkLowBattery(newModel, Device.THERMOMETER_KINDERZIMMER);
		checkLowBattery(newModel, Device.THERMOMETER_WOHNZIMMER);
		checkLowBattery(newModel, Device.DIFFERENZTEMPERATUR_EINFAHRT_AUSSEN);
		checkLowBattery(newModel, Device.DIFFERENZTEMPERATUR_TERRASSE_AUSSEN);
		checkLowBattery(newModel, Device.STROMZAEHLER);

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
				new Hint(
						lookupHint(newModel.getKidsRoomTemperature(), newModel.getEntranceTemperature(),
								lookupIntensity(newModel.getEntranceSunHeatingDiff()), null),
						"Kinderzimmer"));
		newModel.setConclusionHintBathRoom(new Hint(
				lookupHint(newModel.getBathRoomTemperature(), newModel.getEntranceTemperature(),
						lookupIntensity(newModel.getEntranceSunHeatingDiff()), newModel.getBathRoomHeating()),
				"Badezimmer"));
		newModel.setConclusionHintBedRoom(
				new Hint(lookupHint(newModel.getBedRoomTemperature(), newModel.getTerraceTemperature(),
						lookupIntensity(newModel.getTerraceSunHeatingDiff()), null), "Schlafzimmer"));
		newModel.setConclusionHintLivingRoom(
				new Hint(lookupHint(newModel.getLivingRoomTemperature(), newModel.getTerraceTemperature(),
						lookupIntensity(newModel.getTerraceSunHeatingDiff()), null), "Wohnzimmer"));

	}

	private String lookupHint(BigDecimal insideTemperature, BigDecimal outsideTemperature,
			Intensity sunIntensity, HeatingModel heating) {

		// SELECT max(ts) FROM D_BIDCOS_RF_OEQ0854602_4_CONTROL_MODE where value
		// = 3;

		if (insideTemperature == null) {
			return null;
		} else if (insideTemperature.compareTo(TARGET_TEMPERATURE_INSIDE) < 0) {
			// TODO: using sun heating in the winter for warming up rooms
			return null;
		} else if (insideTemperature.compareTo(TARGET_TEMPERATURE_INSIDE) > 0
				&& outsideTemperature.compareTo(insideTemperature) < 0
				&& sunIntensity.ordinal() <= Intensity.LOW.ordinal()) {
			if (heating != null && (heating.isBoostActive()
					|| heating.getTargetTemperature().compareTo(TARGET_TEMPERATURE_INSIDE) > 0)) {
				return null;
			} else {
				return "Fenster öffnen";
			}
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

	private BigDecimal readTemperature(Device device) {
		return api.getAsBigDecimal(device.accessKeyXmlApi(Datapoint.TEMPERATURE));
	}

	private BigDecimal readActTemperature(Device device) {
		return api.getAsBigDecimal(device.accessKeyXmlApi(Datapoint.ACTUAL_TEMPERATURE));
	}

	private BigDecimal readHumidity(Device device) {
		return api.getAsBigDecimal(device.accessKeyXmlApi(Datapoint.HUMIDITY));
	}

	private HeatingModel readHeating(Device device) {
		HeatingModel model = new HeatingModel();
		model.setBoostActive(api.getAsBigDecimal(device.accessKeyXmlApi(Datapoint.CONTROL_MODE))
				.compareTo(HEATING_CONTROL_MODE_BOOST) == 0);
		model.setBoostMinutesLeft(
				api.getAsBigDecimal(device.accessKeyXmlApi(Datapoint.BOOST_STATE)).intValue());
		model.setTargetTemperature(api.getAsBigDecimal(device.accessKeyXmlApi(Datapoint.SET_TEMPERATURE)));
		model.setProgramNamePrefix(device.programNamePrefix());
		return model;
	}

	private SwitchModel readSwitchState(Device device) {
		SwitchModel switchModel = new SwitchModel();
		switchModel.setState(api.getAsBoolean(device.accessKeyXmlApi(Datapoint.STATE)));
		switchModel.setDeviceIdVar(device.accessKeyXmlApi(Datapoint.STATE));
		return switchModel;
	}

	private int readPowerConsumption(Device device) {
		return api.getAsBigDecimal(device.accessKeyXmlApi(Datapoint.POWER)).intValue();
	}

	private void checkLowBattery(HouseModel model, Device device) {

		Boolean state = null;
		if (device.isHomematic()) {
			state = api.getAsBoolean(device.accessMainDeviceKeyXmlApi(Datapoint.LOWBAT));
		} else if (device.isHomematicIP()) {
			state = api.getAsBoolean(device.accessMainDeviceKeyXmlApi(Datapoint.LOW_BAT));
		}

		if (state == null) {
			LogFactory.getLog(HouseService.class).error("Error reading Battery state: " + device.name());
		} else if (state == true) {
			model.getLowBatteryDevices().add(device.getDescription());
		}
	}
}

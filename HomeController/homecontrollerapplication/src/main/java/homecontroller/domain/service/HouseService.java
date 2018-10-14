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
import homecontroller.domain.model.OutdoorClimate;
import homecontroller.domain.model.RoomClimate;
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

		newModel.setClimateBathRoom(readRoomClimate(Device.THERMOSTAT_BAD, Device.THERMOSTAT_BAD));
		newModel.setClimateKidsRoom(readRoomClimate(Device.THERMOMETER_KINDERZIMMER));
		newModel.setClimateLivingRoom(readRoomClimate(Device.THERMOMETER_WOHNZIMMER));
		newModel.setClimateBedRoom(readRoomClimate(Device.THERMOMETER_SCHLAFZIMMER));

		newModel.setClimateTerrace(readOutdoorClimate(Device.DIFFERENZTEMPERATUR_TERRASSE_AUSSEN,
				Device.DIFFERENZTEMPERATUR_TERRASSE_DIFF));
		newModel.setClimateEntrance(readOutdoorClimate(Device.DIFFERENZTEMPERATUR_EINFAHRT_AUSSEN,
				Device.DIFFERENZTEMPERATUR_EINFAHRT_DIFF));

		newModel.setKitchenWindowLightSwitch(readSwitchState(Device.SCHALTER_KUECHE_LICHT));

		newModel.setHouseElectricalPowerConsumption(readPowerConsumption(Device.STROMZAEHLER));

		for (Device device : Device.values()) {
			checkLowBattery(newModel, device);
		}

		return newModel;
	}

	public void calculateConclusion(HouseModel newModel) {

		if (newModel.getClimateTerrace().getTemperature()
				.compareTo(newModel.getClimateEntrance().getTemperature()) < 0) {
			newModel.setConclusionClimateFacadeMin(newModel.getClimateTerrace());
			newModel.setConclusionClimateFacadeMax(newModel.getClimateEntrance());
		} else {
			newModel.setConclusionClimateFacadeMin(newModel.getClimateEntrance());
			newModel.setConclusionClimateFacadeMax(newModel.getClimateTerrace());
		}

		BigDecimal sunShadeDiff = newModel.getConclusionClimateFacadeMax().getTemperature()
				.subtract(newModel.getConclusionClimateFacadeMin().getTemperature()).abs();
		newModel.getConclusionClimateFacadeMax()
				.setSunHeatingInContrastToShadeIntensity(lookupIntensity(sunShadeDiff));

		lookupHint(newModel.getClimateKidsRoom(), newModel.getClimateEntrance());
		lookupHint(newModel.getClimateBathRoom(), newModel.getClimateEntrance());
		lookupHint(newModel.getClimateBedRoom(), newModel.getClimateTerrace());
		lookupHint(newModel.getClimateLivingRoom(), newModel.getClimateTerrace());

	}

	private void lookupHint(RoomClimate room, OutdoorClimate outdoor) {

		// SELECT max(ts) FROM D_BIDCOS_RF_OEQ0854602_4_CONTROL_MODE where value
		// = 3;

		if (room.getTemperature() == null) {
			return;
		} else if (room.getTemperature().compareTo(TARGET_TEMPERATURE_INSIDE) < 0) {
			// TODO: using sun heating in the winter for warming up rooms
			return;
		} else if (room.getTemperature().compareTo(TARGET_TEMPERATURE_INSIDE) > 0
				&& outdoor.getTemperature().compareTo(room.getTemperature()) < 0
				&& outdoor.getSunBeamIntensity().ordinal() <= Intensity.LOW.ordinal()) {
			if (room.getHeating() != null && (room.getHeating().isBoostActive()
					|| room.getHeating().getTargetTemperature().compareTo(TARGET_TEMPERATURE_INSIDE) > 0)) {
				return;
			} else {
				room.setHint(new Hint("Fenster öffnen"));
			}
		} else if (room.getTemperature().compareTo(TARGET_TEMPERATURE_INSIDE) > 0
				&& outdoor.getSunBeamIntensity().ordinal() > Intensity.LOW.ordinal()) {
			room.setHint(new Hint("Rolladen schließen"));
		}

		return;
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

	private OutdoorClimate readOutdoorClimate(Device outside, Device diff) {
		OutdoorClimate outdoorClimate = new OutdoorClimate();
		outdoorClimate.setTemperature(api.getAsBigDecimal(outside.accessKeyXmlApi(Datapoint.TEMPERATURE)));
		outdoorClimate.setSunBeamIntensity(
				lookupIntensity(api.getAsBigDecimal(diff.accessKeyXmlApi(Datapoint.TEMPERATURE))));
		outdoorClimate.setPlaceName(outside.getPlaceName());
		return outdoorClimate;
	}

	private RoomClimate readRoomClimate(Device thermometer) {
		RoomClimate roomClimate = new RoomClimate();
		roomClimate.setTemperature(
				api.getAsBigDecimal(thermometer.accessKeyXmlApi(Datapoint.ACTUAL_TEMPERATURE)));
		roomClimate.setHumidity(api.getAsBigDecimal(thermometer.accessKeyXmlApi(Datapoint.HUMIDITY)));
		roomClimate.setPlaceName(thermometer.getPlaceName());
		return roomClimate;
	}

	private RoomClimate readRoomClimate(Device thermometer, Device heating) {
		RoomClimate roomClimate = readRoomClimate(thermometer);
		HeatingModel heatingModel = new HeatingModel();
		heatingModel.setBoostActive(api.getAsBigDecimal(heating.accessKeyXmlApi(Datapoint.CONTROL_MODE))
				.compareTo(HEATING_CONTROL_MODE_BOOST) == 0);
		heatingModel.setBoostMinutesLeft(
				api.getAsBigDecimal(heating.accessKeyXmlApi(Datapoint.BOOST_STATE)).intValue());
		heatingModel.setTargetTemperature(
				api.getAsBigDecimal(heating.accessKeyXmlApi(Datapoint.SET_TEMPERATURE)));
		heatingModel.setProgramNamePrefix(heating.programNamePrefix());
		roomClimate.setHeating(heatingModel);
		roomClimate.setPlaceName(thermometer.getPlaceName());

		return roomClimate;
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

		if (state != null && state == true) {
			model.getLowBatteryDevices().add(device.getDescription());
		}
	}
}

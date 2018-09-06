package homecontroller.domain;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.PostConstruct;

import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import homecontroller.service.HomematicAPI;

@Component
public class HouseService {

	private final static int TARGET_TEMPERATURE_INSIDE = 22;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private HomematicAPI api;

	private Map<String, String> viewKeyToDevice;

	@PostConstruct
	public void init() {

		viewKeyToDevice = new HashMap<>();
		viewKeyToDevice.put("tempBathroom_boost", "Vorbereitung Dusche");
		viewKeyToDevice.put("switchKitchen", "BidCos-RF.OEQ0712456:1");
		try {
			refreshHouseModel();
			refreshHistoryModelComplete();
		} catch (Exception e) {
			LogFactory.getLog(HouseService.class).error("Could not initialize HouseService completly.", e);
		}
	}

	public void scheduledRefreshHouseModel() {
		refreshHouseModel();
	}

	@Scheduled(fixedDelay = (1000 * 60))
	private void refreshHouseModel() {

		HouseModel newModel = refreshModel();
		calculateConclusion(newModel);
		ModelDAO.getInstance().write(newModel);
	}

	@Scheduled(cron = "5 0 0 * * *")
	private void refreshHistoryModelComplete() {

		HistoryModel newModel = new HistoryModel();
		List<Timestamp> timestamps = jdbcTemplate.query(
				"select formatdatetime(ts, 'yyyy_MM') as month, max(ts) as last FROM D_BIDCOS_RF_NEQ0861520_1_ENERGY_COUNTER group by month order by month asc;", new Object[] {},
				new TimestampRowMapper("last"));
		for (Timestamp timestamp : timestamps) {
			BigDecimal value = jdbcTemplate.queryForObject("select value FROM D_BIDCOS_RF_NEQ0861520_1_ENERGY_COUNTER where ts = ?;", new Object[] { timestamp },
					new BigDecimalRowMapper("value"));
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

		Timestamp timestamp = jdbcTemplate.queryForObject("select max(ts) as time from D_BIDCOS_RF_NEQ0861520_1_ENERGY_COUNTER;", new TimestampRowMapper("time"));

		Entry<Long, BigDecimal> lastElement = null;
		LinkedHashMap<Long, BigDecimal> map = (LinkedHashMap<Long, BigDecimal>) model.getMonthlyPowerConsumption();
		Iterator<Entry<Long, BigDecimal>> iterator = map.entrySet().iterator();
		HashMap<Long, BigDecimal> newMap = new LinkedHashMap<Long, BigDecimal>();
		while (iterator.hasNext()) {
			lastElement = iterator.next();
			if (iterator.hasNext()) {
				newMap.put(lastElement.getKey(), lastElement.getValue());
			}
		}

		if (timestamp.getTime() > lastElement.getKey() && isSameMonth(timestamp, new Date(lastElement.getKey()))) {
			BigDecimal value = jdbcTemplate.queryForObject("select value FROM D_BIDCOS_RF_NEQ0861520_1_ENERGY_COUNTER where ts = ?;", new Object[] { timestamp },
					new BigDecimalRowMapper("value"));
			newMap.put(timestamp.getTime(), value);
			model.setMonthlyPowerConsumption(newMap);
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

		return cal1.get(Calendar.ERA) == cal2.get(Calendar.ERA) && cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) && cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH);
	}

	public HouseModel refreshModel() {

		api.refresh();

		HouseModel newModel = new HouseModel();

		newModel.setBathRoomTemperature(readActTemperature("BidCos-RF.OEQ0854602", "4"));
		newModel.setBathRoomBoost(readBoost("Vorbereitung Dusche"));

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

		newModel.setKitchenLightSwitchState(readSwitchState("BidCos-RF.OEQ0712456", "1"));

		newModel.setHouseElectricalPowerConsumption(readPowerConsumption("BidCos-RF.NEQ0861520", "1"));

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

		newModel.setConclusionFacadeSidesDifference(newModel.getConclusionFacadeMaxTemp().subtract(newModel.getConclusionFacadeMinTemp()).abs());

		newModel.setConclusionFacadeMaxTempSunIntensity(lookupIntensity(newModel.getConclusionFacadeMaxTempSunHeating(), 3));
		newModel.setConclusionFacadeMaxTempHeatingIntensity(lookupIntensity(newModel.getConclusionFacadeSidesDifference(), 1));

		newModel.setConclusionHintKidsRoom(
				lookupHint(newModel.getKidsRoomTemperature(), newModel.getEntranceTemperature(), lookupIntensity(newModel.getEntranceSunHeatingDiff(), 3)));
		newModel.setConclusionHintBathRoom(
				lookupHint(newModel.getBathRoomTemperature(), newModel.getEntranceTemperature(), lookupIntensity(newModel.getEntranceSunHeatingDiff(), 3)));
		newModel.setConclusionHintBedRoom(lookupHint(newModel.getBedRoomTemperature(), newModel.getTerraceTemperature(), lookupIntensity(newModel.getTerraceSunHeatingDiff(), 3)));
		newModel.setConclusionHintLivingRoom(
				lookupHint(newModel.getLivingRoomTemperature(), newModel.getTerraceTemperature(), lookupIntensity(newModel.getTerraceSunHeatingDiff(), 3)));

	}

	private String lookupHint(BigDecimal insideTemperature, BigDecimal outsideTemperature, Intensity sunIntensity) {

		if (insideTemperature == null) {
			return null;
		} else if (insideTemperature.intValue() <= TARGET_TEMPERATURE_INSIDE) {
			// TODO: using sun heating in the winter for warming up rooms
			return null;
		} else if (insideTemperature.compareTo(new BigDecimal(TARGET_TEMPERATURE_INSIDE)) > 0 && outsideTemperature.compareTo(insideTemperature) < 0
				&& sunIntensity.ordinal() <= Intensity.LOW.ordinal()) {
			return "Fenster öffnen";
		} else if (insideTemperature.compareTo(new BigDecimal(TARGET_TEMPERATURE_INSIDE)) > 0 && sunIntensity.ordinal() > Intensity.LOW.ordinal()) {
			return "Rolladen schließen";
		}

		return null;
	}

	private Intensity lookupIntensity(BigDecimal value, int noIntensityGate) {
		if (value.compareTo(new BigDecimal(noIntensityGate)) < 0) {
			return Intensity.NO;
		} else if (value.compareTo(new BigDecimal(6)) < 0) {
			return Intensity.LOW;
		} else if (value.compareTo(new BigDecimal(13)) < 0) {
			return Intensity.MEDIUM;
		} else {
			return Intensity.HIGH;
		}
	}

	public void toggle(String key) throws Exception {
		api.toggleBooleanState(viewKeyToDevice.get(key));
		refreshHouseModel();
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

	private boolean readBoost(String var) {
		return api.getAsBoolean(var);
	}

	private boolean readSwitchState(String device, String chanel) {
		return api.getAsBoolean(device + ":" + chanel + ".STATE");
	}

	private int readPowerConsumption(String device, String chanel) {
		return api.getAsBigDecimal(device + ":" + chanel + ".POWER").intValue();
	}
}

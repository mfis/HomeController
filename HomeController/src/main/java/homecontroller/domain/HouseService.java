package homecontroller.domain;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.annotation.PostConstruct;

import org.apache.commons.lang3.SerializationUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import homecontroller.service.HomematicAPI;

@Component
public class HouseService {

	private final static int TARGET_TEMPERATURE_INSIDE = 22;

	private HouseModel house;

	@Autowired
	private HomematicAPI api;

	private Map<String, String> viewKeyToDevice;

	private ReadWriteLock readWriteLock;

	@PostConstruct
	public void init() {
		readWriteLock = new ReentrantReadWriteLock();
		viewKeyToDevice = new HashMap<>();
		viewKeyToDevice.put("tempBathroom_boost", "Vorbereitung Dusche");
		viewKeyToDevice.put("switchKitchen", "BidCos-RF.OEQ0712456:1");
		refreshAll();
	}

	@Scheduled(fixedDelay = (1000 * 60))
	public void scheduledRefresh() {
		refreshAll();
	}

	private void refreshAll() {
		HouseModel newModel = refreshModel();
		calculateConclusion(newModel);
		readWriteLock.writeLock().lock();
		house = newModel;
		readWriteLock.writeLock().unlock();
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
		refreshAll();
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

	public HouseModel getHouse() {
		readWriteLock.readLock().lock();
		HouseModel hm = SerializationUtils.clone(house);
		readWriteLock.readLock().unlock();
		return hm;
	}

}

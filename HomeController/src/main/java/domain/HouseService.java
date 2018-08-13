package domain;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import homecontroller.HomematicAPI;

public class HouseService {

	private final static int TARGET_TEMPERATURE_INSIDE = 22;

	private HouseModel house;

	private HomematicAPI api;

	private Map<String, String> viewKeyToDevice;

	public HouseService(HomematicAPI api) {
		this.api = api;
		viewKeyToDevice = new HashMap<>();
		viewKeyToDevice.put("tempBathroom_boost", "Vorbereitung Dusche");
		viewKeyToDevice.put("switchKitchen", "BidCos-RF.OEQ0712456:1");
	}

	public void refreshModel() {

		api.refresh();

		house = new HouseModel();

		house.setBathRoomTemperature(readActTemperature("BidCos-RF.OEQ0854602", "4"));
		house.setBathRoomBoost(readBoost("Vorbereitung Dusche"));

		house.setKidsRoomTemperature(readActTemperature("HmIP-RF.000E97099314A3", "1"));
		house.setKidsRoomHumidity(readHumidity("HmIP-RF.000E97099314A3", "1"));

		house.setLivingRoomTemperature(readActTemperature("HmIP-RF.000E97099312D5", "1"));
		house.setLivingRoomHumidity(readHumidity("HmIP-RF.000E97099312D5", "1"));

		house.setBedRoomTemperature(readActTemperature("HmIP-RF.000E97099314C4", "1"));
		house.setBedRoomHumidity(readHumidity("HmIP-RF.000E97099314C4", "1"));

		house.setTerraceTemperature(readTemperature("BidCos-RF.OEQ0801741", "2"));
		house.setTerraceSunHeatingDiff(readTemperature("BidCos-RF.OEQ0801741", "3"));

		house.setEntranceTemperature(readTemperature("BidCos-RF.OEQ0801807", "2"));
		house.setEntranceSunHeatingDiff(readTemperature("BidCos-RF.OEQ0801807", "3"));

		house.setKitchenLightSwitchState(readSwitchState("BidCos-RF.OEQ0712456", "1"));

		house.setHouseElectricalPowerConsumption(readPowerConsumption("BidCos-RF.NEQ0861520", "1"));

	}

	public void calculateConclusion() {

		if (house.getTerraceTemperature().compareTo(house.getEntranceTemperature()) < 0) {
			// min
			house.setConclusionFacadeMinTemp(house.getTerraceTemperature());
			house.setConclusionFacadeMinTempName("Terrasse");
			// max
			house.setConclusionFacadeMaxTemp(house.getEntranceTemperature());
			house.setConclusionFacadeMaxTempName("Einfahrt");
			house.setConclusionFacadeMaxTempSunHeating(house.getEntranceSunHeatingDiff());
		} else {
			// min
			house.setConclusionFacadeMinTemp(house.getEntranceTemperature());
			house.setConclusionFacadeMinTempName("Einfahrt");
			// max
			house.setConclusionFacadeMaxTemp(house.getTerraceTemperature());
			house.setConclusionFacadeMaxTempName("Terrasse");
			house.setConclusionFacadeMaxTempSunHeating(house.getTerraceSunHeatingDiff());
		}

		house.setConclusionFacadeSidesDifference(house.getConclusionFacadeMaxTemp().subtract(house.getConclusionFacadeMinTemp()).abs());

		house.setConclusionFacadeMaxTempSunIntensity(lookupIntensity(house.getConclusionFacadeMaxTempSunHeating(), 3));
		house.setConclusionFacadeMaxTempHeatingIntensity(lookupIntensity(house.getConclusionFacadeSidesDifference(), 1));

		house.setConclusionHintKidsRoom(lookupHint(house.getKidsRoomTemperature(), house.getEntranceTemperature(), lookupIntensity(house.getEntranceSunHeatingDiff(), 3)));
		house.setConclusionHintBathRoom(lookupHint(house.getBathRoomTemperature(), house.getEntranceTemperature(), lookupIntensity(house.getEntranceSunHeatingDiff(), 3)));
		house.setConclusionHintBedRoom(lookupHint(house.getBedRoomTemperature(), house.getTerraceTemperature(), lookupIntensity(house.getTerraceSunHeatingDiff(), 3)));
		house.setConclusionHintLivingRoom(lookupHint(house.getLivingRoomTemperature(), house.getTerraceTemperature(), lookupIntensity(house.getTerraceSunHeatingDiff(), 3)));

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
		return house;
	}

}

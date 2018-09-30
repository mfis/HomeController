package homecontroller.domain.model;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

public class HouseModel implements Serializable {

	private static final long serialVersionUID = 1L;

	private long dateTime;

	private BigDecimal kidsRoomTemperature;

	private BigDecimal kidsRoomHumidity;

	private BigDecimal bathRoomTemperature;

	private HeatingModel bathRoomHeating;

	private BigDecimal bedRoomTemperature;

	private BigDecimal bedRoomHumidity;

	private BigDecimal livingRoomTemperature;

	private BigDecimal livingRoomHumidity;

	private BigDecimal terraceTemperature;

	private BigDecimal terraceSunHeatingDiff;

	private BigDecimal entranceTemperature;

	private BigDecimal entranceSunHeatingDiff;

	private SwitchModel kitchenWindowLightSwitch;

	private int houseElectricalPowerConsumption;

	// ----------

	private BigDecimal conclusionFacadeMinTemp;

	private String conclusionFacadeMinTempName;

	private BigDecimal conclusionFacadeSidesDifference;

	private BigDecimal conclusionFacadeMaxTemp;

	private BigDecimal conclusionFacadeMaxTempSunHeating;

	private String conclusionFacadeMaxTempName;

	private Intensity conclusionFacadeMaxTempSunIntensity;

	private Intensity conclusionFacadeMaxTempHeatingIntensity;

	// ----------

	private Hint conclusionHintKidsRoom;

	private Hint conclusionHintBathRoom;

	private Hint conclusionHintBedRoom;

	private Hint conclusionHintLivingRoom;

	// ----------

	public HouseModel() {
		super();
		dateTime = new Date().getTime();
	}

	public List<Hint> lookupHints() {

		Field[] fields = this.getClass().getDeclaredFields();
		List<Hint> results = new LinkedList<Hint>();
		try {
			for (Field field : fields) {
				if (field.getType().equals(Hint.class)) {
					results.add((Hint) field.get(this));
				}
			}
		} catch (IllegalArgumentException | IllegalAccessException e) {
			throw new RuntimeException("Exception collecting hints:", e);
		}
		return results;
	}

	public BigDecimal getKidsRoomTemperature() {
		return kidsRoomTemperature;
	}

	public void setKidsRoomTemperature(BigDecimal kidsRoomTemperature) {
		this.kidsRoomTemperature = kidsRoomTemperature;
	}

	public BigDecimal getKidsRoomHumidity() {
		return kidsRoomHumidity;
	}

	public void setKidsRoomHumidity(BigDecimal kidsRoomHumidity) {
		this.kidsRoomHumidity = kidsRoomHumidity;
	}

	public BigDecimal getBathRoomTemperature() {
		return bathRoomTemperature;
	}

	public void setBathRoomTemperature(BigDecimal bathRoomTemperature) {
		this.bathRoomTemperature = bathRoomTemperature;
	}

	public BigDecimal getBedRoomTemperature() {
		return bedRoomTemperature;
	}

	public void setBedRoomTemperature(BigDecimal bedRoomTemperature) {
		this.bedRoomTemperature = bedRoomTemperature;
	}

	public BigDecimal getBedRoomHumidity() {
		return bedRoomHumidity;
	}

	public void setBedRoomHumidity(BigDecimal bedRoomHumidity) {
		this.bedRoomHumidity = bedRoomHumidity;
	}

	public BigDecimal getLivingRoomTemperature() {
		return livingRoomTemperature;
	}

	public void setLivingRoomTemperature(BigDecimal livingRoomTemperature) {
		this.livingRoomTemperature = livingRoomTemperature;
	}

	public BigDecimal getLivingRoomHumidity() {
		return livingRoomHumidity;
	}

	public void setLivingRoomHumidity(BigDecimal livingRoomHumidity) {
		this.livingRoomHumidity = livingRoomHumidity;
	}

	public BigDecimal getTerraceTemperature() {
		return terraceTemperature;
	}

	public void setTerraceTemperature(BigDecimal terraceTemperature) {
		this.terraceTemperature = terraceTemperature;
	}

	public BigDecimal getTerraceSunHeatingDiff() {
		return terraceSunHeatingDiff;
	}

	public void setTerraceSunHeatingDiff(BigDecimal terraceSunHeatingDiff) {
		this.terraceSunHeatingDiff = terraceSunHeatingDiff;
	}

	public BigDecimal getEntranceTemperature() {
		return entranceTemperature;
	}

	public void setEntranceTemperature(BigDecimal entranceTemperature) {
		this.entranceTemperature = entranceTemperature;
	}

	public BigDecimal getEntranceSunHeatingDiff() {
		return entranceSunHeatingDiff;
	}

	public void setEntranceSunHeatingDiff(BigDecimal entranceSunHeatingDiff) {
		this.entranceSunHeatingDiff = entranceSunHeatingDiff;
	}

	public int getHouseElectricalPowerConsumption() {
		return houseElectricalPowerConsumption;
	}

	public void setHouseElectricalPowerConsumption(int houseElectricalPowerConsumption) {
		this.houseElectricalPowerConsumption = houseElectricalPowerConsumption;
	}

	public BigDecimal getConclusionFacadeMinTemp() {
		return conclusionFacadeMinTemp;
	}

	public void setConclusionFacadeMinTemp(BigDecimal conclusionFacadeMinTemp) {
		this.conclusionFacadeMinTemp = conclusionFacadeMinTemp;
	}

	public String getConclusionFacadeMinTempName() {
		return conclusionFacadeMinTempName;
	}

	public void setConclusionFacadeMinTempName(String conclusionFacadeMinTempName) {
		this.conclusionFacadeMinTempName = conclusionFacadeMinTempName;
	}

	public BigDecimal getConclusionFacadeSidesDifference() {
		return conclusionFacadeSidesDifference;
	}

	public void setConclusionFacadeSidesDifference(BigDecimal conclusionFacadeSidesDifference) {
		this.conclusionFacadeSidesDifference = conclusionFacadeSidesDifference;
	}

	public BigDecimal getConclusionFacadeMaxTemp() {
		return conclusionFacadeMaxTemp;
	}

	public void setConclusionFacadeMaxTemp(BigDecimal conclusionFacadeMaxTemp) {
		this.conclusionFacadeMaxTemp = conclusionFacadeMaxTemp;
	}

	public BigDecimal getConclusionFacadeMaxTempSunHeating() {
		return conclusionFacadeMaxTempSunHeating;
	}

	public void setConclusionFacadeMaxTempSunHeating(BigDecimal conclusionFacadeMaxTempSunHeating) {
		this.conclusionFacadeMaxTempSunHeating = conclusionFacadeMaxTempSunHeating;
	}

	public String getConclusionFacadeMaxTempName() {
		return conclusionFacadeMaxTempName;
	}

	public void setConclusionFacadeMaxTempName(String conclusionFacadeMaxTempName) {
		this.conclusionFacadeMaxTempName = conclusionFacadeMaxTempName;
	}

	public Intensity getConclusionFacadeMaxTempSunIntensity() {
		return conclusionFacadeMaxTempSunIntensity;
	}

	public void setConclusionFacadeMaxTempSunIntensity(Intensity conclusionFacadeMaxTempSunIntensity) {
		this.conclusionFacadeMaxTempSunIntensity = conclusionFacadeMaxTempSunIntensity;
	}

	public Intensity getConclusionFacadeMaxTempHeatingIntensity() {
		return conclusionFacadeMaxTempHeatingIntensity;
	}

	public void setConclusionFacadeMaxTempHeatingIntensity(
			Intensity conclusionFacadeMaxTempHeatingIntensity) {
		this.conclusionFacadeMaxTempHeatingIntensity = conclusionFacadeMaxTempHeatingIntensity;
	}

	public Hint getConclusionHintKidsRoom() {
		return conclusionHintKidsRoom;
	}

	public void setConclusionHintKidsRoom(Hint conclusionHintKidsRoom) {
		this.conclusionHintKidsRoom = conclusionHintKidsRoom;
	}

	public Hint getConclusionHintBathRoom() {
		return conclusionHintBathRoom;
	}

	public void setConclusionHintBathRoom(Hint conclusionHintBathRoom) {
		this.conclusionHintBathRoom = conclusionHintBathRoom;
	}

	public Hint getConclusionHintBedRoom() {
		return conclusionHintBedRoom;
	}

	public void setConclusionHintBedRoom(Hint conclusionHintBedRoom) {
		this.conclusionHintBedRoom = conclusionHintBedRoom;
	}

	public Hint getConclusionHintLivingRoom() {
		return conclusionHintLivingRoom;
	}

	public void setConclusionHintLivingRoom(Hint conclusionHintLivingRoom) {
		this.conclusionHintLivingRoom = conclusionHintLivingRoom;
	}

	public long getDateTime() {
		return dateTime;
	}

	public HeatingModel getBathRoomHeating() {
		return bathRoomHeating;
	}

	public void setBathRoomHeating(HeatingModel bathRoomHeating) {
		this.bathRoomHeating = bathRoomHeating;
	}

	public SwitchModel getKitchenWindowLightSwitch() {
		return kitchenWindowLightSwitch;
	}

	public void setKitchenWindowLightSwitch(SwitchModel kitchenWindowLightSwitch) {
		this.kitchenWindowLightSwitch = kitchenWindowLightSwitch;
	}
}

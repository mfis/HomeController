package homecontroller.domain.model;

import java.io.Serializable;

public class PowerConsumptionMonth implements Serializable {

	private static final long serialVersionUID = 1L;

	private Long powerConsumption;

	private long measurePointMin;

	private long measurePointMax;

	private Long lastSingleValue;

	public Long getPowerConsumption() {
		return powerConsumption;
	}

	public void setPowerConsumption(Long powerConsumption) {
		this.powerConsumption = powerConsumption;
	}

	public long getMeasurePointMin() {
		return measurePointMin;
	}

	public void setMeasurePointMin(long measurePointMin) {
		this.measurePointMin = measurePointMin;
	}

	public long getMeasurePointMax() {
		return measurePointMax;
	}

	public void setMeasurePointMax(long measurePointMax) {
		this.measurePointMax = measurePointMax;
	}

	public Long getLastSingleValue() {
		return lastSingleValue;
	}

	public void setLastSingleValue(Long lastSingleValue) {
		this.lastSingleValue = lastSingleValue;
	}

}

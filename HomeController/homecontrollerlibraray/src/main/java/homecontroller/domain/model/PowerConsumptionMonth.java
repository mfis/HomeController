package homecontroller.domain.model;

import java.io.Serializable;

public class PowerConsumptionMonth implements Serializable {

	private static final long serialVersionUID = 1L;

	private long powerConsumption;

	private long measurePointMin;

	private long measurePointMax;

	private boolean isMonthCompleted;

	public long getPowerConsumption() {
		return powerConsumption;
	}

	public void setPowerConsumption(long powerConsumption) {
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

	public boolean isMonthCompleted() {
		return isMonthCompleted;
	}

	public void setMonthCompleted(boolean isMonthCompleted) {
		this.isMonthCompleted = isMonthCompleted;
	}

}

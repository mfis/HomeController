package homecontroller.domain.model;

import java.io.Serializable;
import java.util.Date;
import java.util.LinkedList;

public class HistoryModel implements Serializable {

	private static final long serialVersionUID = 1L;

	private long dateTime;

	private LinkedList<PowerConsumptionMonth> electricPowerConsumption;

	// ----------

	public HistoryModel() {
		super();
		dateTime = new Date().getTime();
		electricPowerConsumption = new LinkedList<>();
	}

	public long getDateTime() {
		return dateTime;
	}

	public LinkedList<PowerConsumptionMonth> getElectricPowerConsumption() {
		return electricPowerConsumption;
	}

	public void setElectricPowerConsumption(LinkedList<PowerConsumptionMonth> electricPowerConsumption) {
		this.electricPowerConsumption = electricPowerConsumption;
	}

}

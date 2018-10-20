package homecontroller.domain.model;

import java.io.Serializable;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

public class HistoryModel implements Serializable {

	private static final long serialVersionUID = 1L;

	private long dateTime;

	private Map<String, PowerConsumptionMonth> electricPowerConsumption;

	// ----------

	public HistoryModel() {
		super();
		dateTime = new Date().getTime();
		electricPowerConsumption = new LinkedHashMap<>();
	}

	public long getDateTime() {
		return dateTime;
	}

	public Map<String, PowerConsumptionMonth> getElectricPowerConsumption() {
		return electricPowerConsumption;
	}

	public void setElectricPowerConsumption(Map<String, PowerConsumptionMonth> electricPowerConsumption) {
		this.electricPowerConsumption = electricPowerConsumption;
	}

}

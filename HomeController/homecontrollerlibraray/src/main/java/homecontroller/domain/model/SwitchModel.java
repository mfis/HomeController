package homecontroller.domain.model;

import java.io.Serializable;

public class SwitchModel implements Serializable {

	private static final long serialVersionUID = 1L;

	public SwitchModel() {
		super();
	}

	private boolean state;

	private String deviceIdVar;

	public boolean isState() {
		return state;
	}

	public void setState(boolean state) {
		this.state = state;
	}

	public String getDeviceIdVar() {
		return deviceIdVar;
	}

	public void setDeviceIdVar(String deviceIdVar) {
		this.deviceIdVar = deviceIdVar;
	}

}

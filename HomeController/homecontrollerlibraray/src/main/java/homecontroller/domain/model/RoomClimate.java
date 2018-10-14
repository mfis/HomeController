package homecontroller.domain.model;

import java.io.Serializable;

public class RoomClimate extends Climate implements Serializable {

	private static final long serialVersionUID = 1L;

	private HeatingModel heating;

	private Hint hint;

	public HeatingModel getHeating() {
		return heating;
	}

	public void setHeating(HeatingModel heating) {
		this.heating = heating;
	}

	public Hint getHint() {
		return hint;
	}

	public void setHint(Hint hint) {
		this.hint = hint;
	}
}

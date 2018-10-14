package homecontroller.domain.model;

import java.io.Serializable;

public class Hint implements Serializable {

	private static final long serialVersionUID = 1L;

	private String text;

	public Hint() {
		super();
	}

	public Hint(String text) {
		super();
		this.text = text;
	}

	public String formatWithRoomName(RoomClimate roomClimate) {
		if (text == null || text.trim().length() == 0) {
			return null;
		}
		return roomClimate.getPlaceName() + ": " + text;
	}

	@Override
	public String toString() {
		return getText();
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

}

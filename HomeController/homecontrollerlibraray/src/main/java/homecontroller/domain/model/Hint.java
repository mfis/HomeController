package homecontroller.domain.model;

import java.io.Serializable;

public class Hint implements Serializable {

	private static final long serialVersionUID = 1L;

	private String text;

	private String roomName;

	public Hint() {
		super();
	}

	public Hint(String text, String roomName) {
		super();
		this.text = text;
		this.roomName = roomName;
	}

	public String formatWithRoomName() {
		if (text == null || text.trim().length() == 0) {
			return null;
		}
		return roomName + ": " + text;
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

	public String getRoomName() {
		return roomName;
	}

	public void setRoomName(String roomName) {
		this.roomName = roomName;
	}
}

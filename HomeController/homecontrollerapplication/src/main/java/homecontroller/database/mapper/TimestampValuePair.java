package homecontroller.database.mapper;

import java.sql.Timestamp;

public class TimestampValuePair {

	private Timestamp timeatamp;

	public TimestampValuePair(Timestamp timeatamp, long value) {
		super();
		this.timeatamp = timeatamp;
		this.value = value;
	}

	private long value;

	public Timestamp getTimeatamp() {
		return timeatamp;
	}

	public void setTimeatamp(Timestamp timeatamp) {
		this.timeatamp = timeatamp;
	}

	public long getValue() {
		return value;
	}

	public void setValue(long value) {
		this.value = value;
	}
}

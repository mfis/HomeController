package homecontroller.database.mapper;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.springframework.jdbc.core.RowMapper;

public class TimestampValueRowMapper implements RowMapper<TimestampValuePair> {

	@Override
	public TimestampValuePair mapRow(ResultSet rs, int rowNum) throws SQLException {
		return new TimestampValuePair(rs.getTimestamp("ts"), rs.getLong("value"));
	}

}

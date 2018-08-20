package homecontroller.domain;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.springframework.jdbc.core.RowMapper;

public class CustomerRowMapper implements RowMapper<Object> {

	@Override
	public Object mapRow(ResultSet rs, int rowNum) throws SQLException {
		System.out.println(rs.getBigDecimal("value"));
		return null;
	}

}

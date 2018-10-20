package homecontroller.domain.service;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import javax.annotation.PostConstruct;

import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import homecontroller.dao.ModelDAO;
import homecontroller.database.mapper.LongRowMapper;
import homecontroller.database.mapper.TimestampRowMapper;
import homecontroller.domain.model.Datapoint;
import homecontroller.domain.model.Device;
import homecontroller.domain.model.HistoryModel;
import homecontroller.domain.model.PowerConsumptionMonth;

@Component
public class HistoryService {

	private SimpleDateFormat monthYear = new SimpleDateFormat("yyyy-MM");

	// 2017-10-31 23:59:59.999
	private SimpleDateFormat sqlTimestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@PostConstruct
	public void init() {

		try {
			refreshHistoryModelComplete();
		} catch (Exception e) {
			LogFactory.getLog(HistoryService.class).error("Could not initialize HistoryService completly.",
					e);
		}
	}

	@Scheduled(cron = "5 0 0 * * *")
	private void refreshHistoryModelComplete() {

		HistoryModel newModel = new HistoryModel();

		List<Timestamp> timestampsMax = jdbcTemplate.query(
				"select formatdatetime(ts, 'yyyy_MM') as month, max(ts) as last FROM "
						+ Device.STROMZAEHLER.accessKeyHistorian(Datapoint.ENERGY_COUNTER)
						+ " group by month order by month asc;",
				new Object[] {}, new TimestampRowMapper("last"));

		Timestamp lastTimestamp = null;
		for (Timestamp timestamp : timestampsMax) {
			if (lastTimestamp != null) {
				PowerConsumptionMonth pcm = new PowerConsumptionMonth();
				pcm.setMeasurePointMax(timestamp.getTime());
				pcm.setMeasurePointMin(lastTimestamp.getTime());
				String key = monthYear.format(timestamp);
				newModel.getElectricPowerConsumption().put(key, pcm);
			}
			lastTimestamp = timestamp;
		}

		for (PowerConsumptionMonth pcm : newModel.getElectricPowerConsumption().values()) {
			String sqlFrom = sqlTimestamp.format(new Date(pcm.getMeasurePointMin()));
			String sqlTo = sqlTimestamp.format(new Date(pcm.getMeasurePointMax()));
			List<Long> measurePoints = jdbcTemplate.query(
					"select ts, value FROM "
							+ Device.STROMZAEHLER.accessKeyHistorian(Datapoint.ENERGY_COUNTER)
							+ " where ts between '" + sqlFrom + "' and '" + sqlTo + "'  order by ts asc;",
					new Object[] {}, new LongRowMapper("value"));
			Long lastValue = null;
			Long powerConsumption = 0L;
			for (Long measurePoint : measurePoints) {
				if (lastValue != null) {
					if (lastValue < measurePoint) {
						powerConsumption += (measurePoint - lastValue);
					} else if (lastValue.compareTo(measurePoint) > 0) {
						// overflow
						powerConsumption += measurePoint;
					}
				}
				lastValue = measurePoint;
			}
			pcm.setPowerConsumption(powerConsumption.longValue() / 1000);
		}

		ModelDAO.getInstance().write(newModel);
	}

	@Scheduled(cron = "0 2/3 * * * *")
	private void refreshHistoryModel() {

		HistoryModel model = ModelDAO.getInstance().readHistoryModel();
		if (model == null) {
			return;
		}

		if (model.getElectricPowerConsumption() == null || model.getElectricPowerConsumption().isEmpty()) {
			refreshHistoryModelComplete();
			return;
		}

	}

	private boolean isSameMonth(Date date1, Date date2) {

		Calendar cal1 = Calendar.getInstance();
		cal1.setTime(date1);
		Calendar cal2 = Calendar.getInstance();
		cal2.setTime(date2);

		if (cal1 == null || cal2 == null) {
			return false;
		}

		return cal1.get(Calendar.ERA) == cal2.get(Calendar.ERA)
				&& cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR)
				&& cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH);
	}
}

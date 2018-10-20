package homecontroller.domain.service;

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
import homecontroller.database.mapper.TimestampValuePair;
import homecontroller.database.mapper.TimestampValueRowMapper;
import homecontroller.domain.model.Datapoint;
import homecontroller.domain.model.Device;
import homecontroller.domain.model.HistoryModel;
import homecontroller.domain.model.PowerConsumptionMonth;

@Component
public class HistoryService {

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
		ModelDAO.getInstance().write(newModel);

		calculateElectricPowerConsumption(newModel, new Date(0));
	}

	private void calculateElectricPowerConsumption(HistoryModel newModel, Date fromDate) {

		String startTs = sqlTimestamp.format(fromDate);

		List<TimestampValuePair> timestampValues = jdbcTemplate.query(
				"select ts, value FROM " + Device.STROMZAEHLER.accessKeyHistorian(Datapoint.ENERGY_COUNTER)
						+ " where ts > '" + startTs + "' order by ts asc;",
				new Object[] {}, new TimestampValueRowMapper());

		for (TimestampValuePair pair : timestampValues) {
			PowerConsumptionMonth dest = null;
			for (PowerConsumptionMonth pcm : newModel.getElectricPowerConsumption()) {
				if (isSameMonth(pair.getTimeatamp(), new Date(pcm.getMeasurePointMax()))) {
					dest = pcm;
				}
			}
			if (dest == null) {
				dest = new PowerConsumptionMonth();
				if (newModel.getElectricPowerConsumption().size() > 0) {
					dest.setMeasurePointMin(newModel.getElectricPowerConsumption()
							.get(newModel.getElectricPowerConsumption().size() - 1).getMeasurePointMax());
					dest.setLastSingleValue(newModel.getElectricPowerConsumption()
							.get(newModel.getElectricPowerConsumption().size() - 1).getLastSingleValue());
				}
				newModel.getElectricPowerConsumption().add(dest);
			}
			addMeasurePoint(dest, pair);
		}
	}

	private void addMeasurePoint(PowerConsumptionMonth pcm, TimestampValuePair measurePoint) {

		if (pcm.getLastSingleValue() != null) {
			try {
				if (pcm.getLastSingleValue() < measurePoint.getValue()) {
					pcm.setPowerConsumption(
							(pcm.getPowerConsumption() != null ? pcm.getPowerConsumption() : 0)
									+ (measurePoint.getValue() - pcm.getLastSingleValue()));
				} else if (pcm.getLastSingleValue().compareTo(measurePoint.getValue()) > 0) {
					// overflow
					pcm.setPowerConsumption(pcm.getPowerConsumption() + measurePoint.getValue());
				}
			} catch (NullPointerException npe) {
				System.out.println("");
			}
		}
		pcm.setLastSingleValue(measurePoint.getValue());
		pcm.setMeasurePointMax(measurePoint.getTimeatamp().getTime());
	}

	@Scheduled(cron = "0 2/3 * * * *")
	private void refreshHistoryModel() {

		HistoryModel model = ModelDAO.getInstance().readHistoryModel();
		if (model == null) {
			return;
		}

		if (model.getElectricPowerConsumption() == null || model.getElectricPowerConsumption().isEmpty()) {
			return;
		}

		calculateElectricPowerConsumption(model, new Date(model.getElectricPowerConsumption()
				.get(model.getElectricPowerConsumption().size() - 1).getMeasurePointMax()));

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

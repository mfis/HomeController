package homecontroller.domain.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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

	private final static DateTimeFormatter SQL_TIMESTAMP_FORMATTER = DateTimeFormatter
			.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

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

		HistoryModel oldModel = ModelDAO.getInstance().readHistoryModel();
		if (oldModel != null) {
			oldModel.setElectricPowerConsumptionInitialized(false);
		}

		HistoryModel newModel = new HistoryModel();
		ModelDAO.getInstance().write(newModel);

		calculateElectricPowerConsumption(newModel,
				LocalDateTime.ofInstant(Instant.ofEpochMilli(0), ZoneId.systemDefault()));
		newModel.setElectricPowerConsumptionInitialized(true);
	}

	@Scheduled(fixedDelay = (1000 * 60 * 3))
	private void refreshHistoryModel() {

		HistoryModel model = ModelDAO.getInstance().readHistoryModel();
		if (model == null || model.getElectricPowerConsumption() == null) {
			return;
		}

		if (model.isElectricPowerConsumptionInitialized()) {
			calculateElectricPowerConsumption(model, model.getElectricPowerConsumption()
					.get(model.getElectricPowerConsumption().size() - 1).measurePointMaxDateTime());
		}
	}

	private void calculateElectricPowerConsumption(HistoryModel newModel, LocalDateTime fromDateTime) {

		String startTs = SQL_TIMESTAMP_FORMATTER.format(fromDateTime);

		List<TimestampValuePair> timestampValues = jdbcTemplate.query(
				"select ts, value FROM " + Device.STROMZAEHLER.accessKeyHistorian(Datapoint.ENERGY_COUNTER)
						+ " where ts > '" + startTs + "' order by ts asc;",
				new Object[] {}, new TimestampValueRowMapper());

		for (TimestampValuePair pair : timestampValues) {
			PowerConsumptionMonth dest = null;
			for (PowerConsumptionMonth pcm : newModel.getElectricPowerConsumption()) {
				if (isSameMonth(pair.getTimeatamp(), pcm.measurePointMaxDateTime())) {
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
			if (pcm.getLastSingleValue() < measurePoint.getValue()) {
				pcm.setPowerConsumption((pcm.getPowerConsumption() != null ? pcm.getPowerConsumption() : 0)
						+ (measurePoint.getValue() - pcm.getLastSingleValue()));
			} else if (pcm.getLastSingleValue().compareTo(measurePoint.getValue()) > 0) {
				// overflow
				pcm.setPowerConsumption(pcm.getPowerConsumption() + measurePoint.getValue());
			}
		}
		pcm.setLastSingleValue(measurePoint.getValue());
		pcm.setMeasurePointMax(
				measurePoint.getTimeatamp().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
	}

	private boolean isSameMonth(LocalDateTime date1, LocalDateTime date2) {
		return date1.getYear() == date2.getYear() && date1.getMonthValue() == date2.getMonthValue();
	}
}

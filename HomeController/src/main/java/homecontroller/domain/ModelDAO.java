package homecontroller.domain;

import java.util.Date;

public class ModelDAO {

	private static ModelDAO instance;

	private HouseModel houseModel;

	private HistoryModel historyModel;

	private static final Object monitor = new Object();

	private ModelDAO() {
		super();
	}

	public static ModelDAO getInstance() {
		if (instance == null) {
			synchronized (monitor) {
				if (instance == null) {
					instance = new ModelDAO();
				}
			}
		}
		return instance;
	}

	public void write(HouseModel newModel) {
		houseModel = newModel;
	}

	public void write(HistoryModel newModel) {
		historyModel = newModel;
	}

	public HouseModel readHouseModel() {
		if (new Date().getTime() - houseModel.getDateTime() > 1000 * 60 * 3) {
			return null; // Too old. Should never happen
		} else {
			return houseModel;
		}
	}

	public HistoryModel readHistoryModel() {
		if (new Date().getTime() - historyModel.getDateTime() > 1000 * 60 * 60 * 24) {
			return null; // Too old. Should never happen
		} else {
			return historyModel;
		}
	}
}

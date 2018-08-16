package homecontroller.domain;

import java.util.Date;

public class ModelDAO {

	private static ModelDAO instance;

	private HouseModel houseModel;

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

	public HouseModel read() {
		if (new Date().getTime() - houseModel.getDateTime() > 1000 * 60 * 3) {
			return null; // Too old. Should never happen
		} else {
			return houseModel;
		}
	}
}

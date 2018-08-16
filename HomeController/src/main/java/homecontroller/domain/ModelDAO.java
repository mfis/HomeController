package homecontroller.domain;

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
		return houseModel;
	}
}

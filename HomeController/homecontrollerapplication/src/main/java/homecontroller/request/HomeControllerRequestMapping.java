package homecontroller.request;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import homecontroller.dao.ModelDAO;
import homecontroller.domain.model.ActionModel;
import homecontroller.domain.model.HistoryModel;
import homecontroller.domain.model.HouseModel;
import homecontroller.domain.service.HouseService;

@RestController
public class HomeControllerRequestMapping {

	@Autowired
	private HouseService houseService;

	@GetMapping("/controller/toggle")
	public ActionModel toggle(@RequestParam("key") String key) throws Exception {
		houseService.toggle(key);
		return new ActionModel("OK");
	}

	@GetMapping("/controller/actualstate")
	public HouseModel actualstate() throws Exception {
		return ModelDAO.getInstance().readHouseModel();
	}

	@GetMapping("/controller/history")
	public HistoryModel history() throws Exception {
		return ModelDAO.getInstance().readHistoryModel();
	}

}
package homecontroller.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import homecontroller.domain.ActionModel;
import homecontroller.domain.HistoryModel;
import homecontroller.domain.HouseModel;
import homecontroller.domain.HouseService;
import homecontroller.domain.ModelDAO;

@RestController
public class HomeControllerRequestMapping {

	@Autowired
	private HouseService houseService;

	@GetMapping("/toggle")
	public ActionModel toggle(@RequestParam("key") String key) throws Exception {
		houseService.toggle(key);
		return new ActionModel("OK");
	}

	@GetMapping("/actualstate")
	public HouseModel actualstate() throws Exception {
		return ModelDAO.getInstance().readHouseModel();
	}

	@GetMapping("/history")
	public HistoryModel history() throws Exception {
		return ModelDAO.getInstance().readHistoryModel();
	}

}
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

	@GetMapping("/controller/refresh")
	public ActionModel refresh() throws Exception {
		houseService.refreshHouseModel(true);
		return new ActionModel("OK");
	}

	@GetMapping("/controller/toggle")
	public ActionModel toggle(@RequestParam("devIdVar") String devIdVar) throws Exception {
		houseService.toggle(devIdVar);
		return new ActionModel("OK");
	}

	@GetMapping("/controller/heatingboost")
	public ActionModel heatingBoost(@RequestParam("prefix") String prefix) throws Exception {
		houseService.heatingBoost(prefix);
		return new ActionModel("OK");
	}

	@GetMapping("/controller/heatingmanual")
	public ActionModel heatingManual(@RequestParam("prefix") String prefix, @RequestParam("temperature") String temperature) throws Exception {
		houseService.heatingManual(prefix, temperature);
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
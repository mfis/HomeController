package homecontroller.request;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import homecontroller.dao.ModelDAO;
import homecontroller.domain.model.ActionModel;
import homecontroller.domain.model.HistoryModel;
import homecontroller.domain.model.HouseModel;
import homecontroller.domain.model.SettingsModel;
import homecontroller.domain.service.HouseService;
import homecontroller.service.SettingsService;

@RestController
public class HomeClientRequestMapping {

	@Autowired
	private HouseService houseService;

	@Autowired
	private SettingsService settingsService;

	@PostMapping("/controller/toggle")
	public ActionModel toggle(@RequestParam("devIdVar") String devIdVar) throws Exception {
		houseService.toggle(devIdVar);
		return new ActionModel("OK");
	}

	@PostMapping("/controller/heatingboost")
	public ActionModel heatingBoost(@RequestParam("prefix") String prefix) throws Exception {
		houseService.heatingBoost(prefix);
		return new ActionModel("OK");
	}

	@PostMapping("/controller/heatingmanual")
	public ActionModel heatingManual(@RequestParam("prefix") String prefix,
			@RequestParam("temperature") String temperature) throws Exception {
		houseService.heatingManual(prefix, temperature);
		return new ActionModel("OK");
	}

	@PostMapping("/controller/actualstate")
	public HouseModel actualstate() throws Exception {
		return ModelDAO.getInstance().readHouseModel();
	}

	@PostMapping("/controller/history")
	public HistoryModel history() throws Exception {
		return ModelDAO.getInstance().readHistoryModel();
	}

	@PostMapping("/controller/settings")
	public SettingsModel settings(@RequestParam("user") String user) throws Exception {
		SettingsModel settings = settingsService.read(user);
		return settings;
	}

	@PostMapping("/controller/settingspushtoggle")
	public ActionModel settingspushtoggle(@RequestParam("user") String user) throws Exception {
		settingsService.togglePush(user);
		return new ActionModel("OK");
	}

	@PostMapping("/controller/settingspushover")
	public ActionModel settingspushover(@RequestParam("user") String user,
			@RequestParam("token") String token, @RequestParam("userid") String userid,
			@RequestParam("device") String device) throws Exception {
		settingsService.setupPush(user, token, userid, device);
		return new ActionModel("OK");
	}
}
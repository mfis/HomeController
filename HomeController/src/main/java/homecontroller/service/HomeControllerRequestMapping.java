package homecontroller.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import homecontroller.domain.HouseModel;
import homecontroller.domain.HouseService;

@RestController
public class HomeControllerRequestMapping {

	@Autowired
	private HouseService houseService;

	@GetMapping("/toggle")
	public HouseModel toggle(@RequestParam("key") String key) throws Exception {
		houseService.toggle(key);
		return houseService.getHouse();
	}

	@GetMapping("/actualstate")
	public HouseModel actualstate() throws Exception {
		return houseService.getHouse();
	}

}
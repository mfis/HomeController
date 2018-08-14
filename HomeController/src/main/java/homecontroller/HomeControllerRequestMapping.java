package homecontroller;

import java.util.ArrayList;
import java.util.Arrays;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import domain.HouseModel;
import domain.HouseService;

@RestController
public class HomeControllerRequestMapping {

	@Autowired
	private Environment env;

	private static HomematicAPI api;

	private static HouseService houseService;

	@PostConstruct
	public void init() {
		String hmHost = env.getProperty("homematic.hostName");
		String hmDevPrefixes = env.getProperty("homematic.devicePrefixes");
		api = new HomematicAPI(hmHost, new ArrayList<String>(Arrays.asList(hmDevPrefixes.split(","))));
		houseService = new HouseService(api);
	}

	@GetMapping("/toggle")
	public HouseModel toggle(@RequestParam("key") String key) throws Exception {
		houseService.toggle(key);
		return houseService.getHouse();
	}

	@GetMapping("/actualstate")
	public HouseModel actualstate() throws Exception {
		houseService.refreshModel();
		houseService.calculateConclusion();
		return houseService.getHouse();
	}

}
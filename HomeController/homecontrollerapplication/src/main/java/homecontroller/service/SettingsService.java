package homecontroller.service;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import homecontroller.dao.ExternalPropertiesDAO;
import homecontroller.domain.model.SettingsModel;

@Component
public class SettingsService {

	private static final String PUSH_DEVICE = ".push.device";
	private static final String PUSH_USERID = ".push.userid";
	private static final String PUSH_TOKEN = ".push.token";
	private static final String PUSH_ACTIVE = ".push.active";

	public SettingsModel read(String user) {

		SettingsModel model = new SettingsModel();

		String state = StringUtils.trimToEmpty(ExternalPropertiesDAO.getInstance().read(user + PUSH_ACTIVE));
		model.setPushActive(Boolean.parseBoolean(state));

		String token = StringUtils.trimToEmpty(ExternalPropertiesDAO.getInstance().read(user + PUSH_TOKEN));
		model.setPushoverApiToken(token);

		String userid = StringUtils.trimToEmpty(ExternalPropertiesDAO.getInstance().read(user + PUSH_USERID));
		model.setPushoverUserId(userid);

		String device = StringUtils.trimToEmpty(ExternalPropertiesDAO.getInstance().read(user + PUSH_DEVICE));
		model.setPushoverDevice(device);

		return model;
	}

	public void togglePush(String user) {

		boolean state = Boolean.parseBoolean(StringUtils.trimToEmpty(ExternalPropertiesDAO.getInstance().read(user + PUSH_ACTIVE)));
		ExternalPropertiesDAO.getInstance().write(user + PUSH_ACTIVE, Boolean.toString(!state).toString());
	}

	public void setupPush(String user, String token, String userid, String device) {

		ExternalPropertiesDAO.getInstance().write(user + PUSH_TOKEN, StringUtils.trimToEmpty(token));
		ExternalPropertiesDAO.getInstance().write(user + PUSH_USERID, StringUtils.trimToEmpty(userid));
		ExternalPropertiesDAO.getInstance().write(user + PUSH_DEVICE, StringUtils.trimToEmpty(device));
	}

}

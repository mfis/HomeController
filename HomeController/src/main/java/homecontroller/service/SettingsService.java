package homecontroller.service;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class SettingsService {

	public Boolean isPushEnabledForUser(String user) {
		String state = ExternalPropertiesDAO.getInstance().read(user + ".push");
		if (StringUtils.isBlank(state)) {
			ExternalPropertiesDAO.getInstance().write(user + ".push", Boolean.FALSE.toString());
			return Boolean.FALSE;
		} else {
			return !Boolean.parseBoolean(StringUtils.trim(state));
		}
	}

	public void togglePushStateForUser(String user) {
		boolean state = isPushEnabledForUser(user);
		ExternalPropertiesDAO.getInstance().write(user + ".push", Boolean.toString(state).toString());
	}
}

package org.keycloak.services.util;

import java.text.MessageFormat;

public class AcrValidationUtils {

	public static final String ACR_URN_MASK = "urn:id.gov.au:tdif:acr:ip{0}:cl{1}";

	public static int getClLevelFromACR(String acr) {
		int clValueInt = 0;
		String acrs[] = acr.split(" ");
		acr = acrs[0];
		
		MessageFormat fmt = new MessageFormat(ACR_URN_MASK);
		Object[] values = null;
		try {
			values = fmt.parse(acr);
		}
		catch (java.text.ParseException e) {
			
		}
		
		String clValue = null;
		String ipValue = null;
		if (values != null && values.length == 2) {
			ipValue = values[0].toString();
			clValue = values[1].toString();
			clValueInt = Integer.parseInt(clValue);
		}
		
		return clValueInt;
	}
	
}

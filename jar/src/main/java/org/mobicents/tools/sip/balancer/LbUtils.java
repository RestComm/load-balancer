package org.mobicents.tools.sip.balancer;

import org.apache.commons.validator.routines.InetAddressValidator;

public class LbUtils {

	public static boolean isValidInet6Address(String ipAddress)
	{
		String str = ipAddress;
		int index = str.indexOf('%');
		if(index>0)
			str = str.substring(0,index);
        return InetAddressValidator.getInstance().isValidInet6Address(str);
	}
}

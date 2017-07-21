package org.mobicents.tools.sip.balancer;

import java.util.regex.Pattern;

public class RoutingRule {
	boolean isPatch;
	Pattern ipPattern;
	public RoutingRule(String ipPattern, boolean isPatch)
	{
		this.isPatch = isPatch;
		this.ipPattern = Pattern.compile(ipPattern);
	}
	public boolean isPatch() {
		return isPatch;
	}
	public Pattern getIpPattern() {
		return ipPattern;
	}
	
}

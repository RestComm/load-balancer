package org.mobicents.tools.sip.balancer;

import java.util.Properties;

import javax.sip.SipProvider;
import javax.sip.message.Request;
import javax.sip.message.Response;

public abstract class DefaultBalancerAlgorithm implements BalancerAlgorithm {
	protected Properties properties;

	public void setProperties(Properties properties) {
		this.properties = properties;
	}

	public BalancerContext getBalancerContext() {
		return SIPBalancerForwarder.balancerContext;
	}

	public Properties getProperties() {
		return properties;
	}
	
	public void processResponse(SipProvider sipProvider, Response response) {
		
	}
	
	public void jvmRouteSwitchover(String fromJvmRoute, String toJvmRoute) {
		
	}
	
	public void assignToNode(String id, SIPNode node) {
		
	}

}

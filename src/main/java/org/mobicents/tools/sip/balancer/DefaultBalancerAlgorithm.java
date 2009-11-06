package org.mobicents.tools.sip.balancer;

import java.util.Properties;

import javax.sip.message.Request;
import javax.sip.message.Response;

import org.jboss.netty.handler.codec.http.HttpRequest;

public abstract class DefaultBalancerAlgorithm implements BalancerAlgorithm {
	protected Properties properties;

	public void setProperties(Properties properties) {
		this.properties = properties;
	}

	public BalancerContext getBalancerContext() {
		return BalancerContext.balancerContext;
	}

	public Properties getProperties() {
		return properties;
	}
	
	public void processInternalRequest(Request request) {
		
	}
	
	public SIPNode processHttpRequest(HttpRequest request) {
		if(BalancerContext.balancerContext.nodes.size()>0) {
			return BalancerContext.balancerContext.nodes.get(0);
		} else {
			return null;
		}
	}
	
	public void processInternalResponse(Response response) {
		
	}
	
	public void processExternalResponse(Response response) {
		
	}
	
	public void start() {
		
	}
	
	public void stop() {
		
	}
	
	public void nodeAdded(SIPNode node) {
		
	}

	public void nodeRemoved(SIPNode node) {
		
	}
	
	public void jvmRouteSwitchover(String fromJvmRoute, String toJvmRoute) {
		
	}
	
	public void assignToNode(String id, SIPNode node) {
		
	}

}

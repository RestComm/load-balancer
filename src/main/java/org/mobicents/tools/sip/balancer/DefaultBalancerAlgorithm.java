package org.mobicents.tools.sip.balancer;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;

import javax.sip.message.Request;
import javax.sip.message.Response;

import org.jboss.netty.handler.codec.http.Cookie;
import org.jboss.netty.handler.codec.http.CookieDecoder;
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

			String httpSessionId = null;
			httpSessionId = getUrlParameters(request.getUri()).get("jsessionid");
			if(httpSessionId == null) {
				CookieDecoder cookieDocoder = new CookieDecoder();
				String cookieString = request.getHeader("Cookie");
				if(cookieString != null) {
					Set<Cookie> cookies = cookieDocoder.decode(cookieString);
					Iterator<Cookie> cookieIterator = cookies.iterator();
					while(cookieIterator.hasNext()) {
						Cookie c = cookieIterator.next();
						if(c.getName().equalsIgnoreCase("jsessionid")) {
							httpSessionId = c.getValue();
						}
					}
				}
			}
			if(httpSessionId != null) {
				int indexOfDot = httpSessionId.lastIndexOf('.');
				if(indexOfDot>0 && indexOfDot<httpSessionId.length()) {
					//String sessionIdWithoutJvmRoute = httpSessionId.substring(0, indexOfDot);
					String jvmRoute = httpSessionId.substring(indexOfDot + 1);
					SIPNode node = BalancerContext.balancerContext.jvmRouteToSipNode.get(jvmRoute);
					
					if(node != null) {
						if(BalancerContext.balancerContext.nodes.contains(node)) {
							return node;
						}
					}
				}
				
				// As a failsafe if there is no jvmRoute, just hash the sessionId
				int nodeId = httpSessionId.hashCode()%BalancerContext.balancerContext.nodes.size();
				return BalancerContext.balancerContext.nodes.get(nodeId);
				
			}
			
			return BalancerContext.balancerContext.nodes.get(0);
		} else {
			String unavailaleHost = getProperties().getProperty("unavailableHost");
			if(unavailaleHost != null) {
				return new SIPNode(unavailaleHost, unavailaleHost,0,null,null, 80,0,null);
			} else {
				return null;
			}
		}
	}
	
	public SIPNode processAssignedExternalRequest(Request request, SIPNode assignedNode) {
		return assignedNode;
	}

	private HashMap<String,String> getUrlParameters(String url) {
		HashMap<String,String> parameters = new HashMap<String, String>();
    	int start = url.lastIndexOf('?');
    	if(start>0 && url.length() > start +1) {
    		url = url.substring(start + 1);
    	} else {
    		return parameters;
    	}
    	String[] tokens = url.split("&");
    	for(String token : tokens) {
    		String[] params = token.split("=");
    		if(params.length<2) {
    			parameters.put(token, "");
    		} else {
    			parameters.put(params[0], params[1]);
    		}
    	}
    	return parameters;
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

package org.mobicents.tools.sip.balancer;

public interface EventListener {
	void uasAfterResponse(int statusCode, AppServer source);
	void uasAfterRequestReceived(String method, AppServer source);
	void uacAfterResponse(int statusCode, AppServer source);
	void uacAfterRequestSent(String method, AppServer source);
}

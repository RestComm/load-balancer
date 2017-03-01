package org.mobicents.tools.sip.balancer.operation;

import java.util.HashMap;
import java.util.Map;

import org.mobicents.tools.heartbeat.api.Node;

public class Helper {

	public static Node getNode()
	{
		Node node = new Node("TestNodeHeartbeat", "127.0.0.1");
		Map<String,String> map = new HashMap<>();
		map.put("httpPort", "8080");
		map.put("sslPort", "8081");
		map.put("udpPort", "5060");
		map.put("tcpPort", "5060");
		map.put("tlsPort", "5061");
		map.put("wsPort", "5062");
		map.put("wssPort", "5063");
		map.put("sessionId", "" + System.currentTimeMillis());
		map.put("Restcomm-Instance-Id", "q1w2e3r4t5y6");
		map.put("version", "0");
		map.put("heartbeatPort", "2222");
		node.getProperties().putAll(map);
		return node;
	}
	
	
	public static void sleep(int timeout)
	{
		try {
			Thread.sleep(timeout);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}

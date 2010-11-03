package org.mobicents.tools.sip.balancer;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class AppServer {
	ProtocolObjects po;
	TestSipListener listener;
	Timer timer;
	int port;
	String name;
	SIPNode node;
	public boolean sendHeartbeat = true;

	public AppServer(String appServer, int port) {
		this.port = port;
		this.name = appServer;

	}

	public void start() {
		timer = new Timer();
		po = new ProtocolObjects(name,
				"gov.nist", "UDP", false, null);
		listener = new TestSipListener(port, 0, po, false);
		node = new SIPNode(name, "127.0.0.1");
		node.getProperties().put("udpPort", 5051);
		timer.schedule(new TimerTask() {
			
			@Override
			public void run() {
				ArrayList<SIPNode> nodes = new ArrayList<SIPNode>();
				nodes.add(node);
				sendKeepAliveToBalancers(nodes);
			}
		}, 1000, 1000);
	}
	
	public void stop() {
		timer.cancel();
	}

	private void sendKeepAliveToBalancers(ArrayList<SIPNode> info) {
		if(sendHeartbeat) {
			Thread.currentThread().setContextClassLoader(NodeRegisterRMIStub.class.getClassLoader());
			try {
				Registry registry = LocateRegistry.getRegistry("127.0.0.1", 2000);
				NodeRegisterRMIStub reg=(NodeRegisterRMIStub) registry.lookup("SIPBalancer");
				reg.handlePing(info);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

	}		

}

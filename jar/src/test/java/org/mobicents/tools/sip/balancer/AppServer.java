package org.mobicents.tools.sip.balancer;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import javax.sip.SipProvider;

public class AppServer {
	public ProtocolObjects protocolObjects;
	public TestSipListener sipListener;
	Timer timer;
	int port;
	String name;
	SIPNode appServerNode;
	public boolean sendHeartbeat = true;
	String lbAddress;
	int lbRMIport;
	int lbSIPext;
	int lbSIPint;
	protected String balancers;
	public SipProvider sipProvider;

	public AppServer(String appServer, int port, String lbAddress, int lbRMI, int lbSIPext, int lbSIPint) {
		this.port = port;
		this.name = appServer;
		this.lbAddress = lbAddress;
		this.lbRMIport = lbRMI;
		this.lbSIPext = lbSIPext;
		this.lbSIPint = lbSIPint;
	}
	
	public AppServer(String appServer, int port) {
		this(appServer, port, "127.0.0.1");

	} 
	
	public void setBalancers(String balancers) {
		this.balancers = balancers;
	}
	
	public AppServer(String appServer, int port, String address) {
		this(appServer, port, address, 2000, 5060, 5065);

	} 
	
	public void setEventListener(EventListener listener) {
		sipListener.eventListener = listener;
	}

	public void start() {
		timer = new Timer();
		protocolObjects = new ProtocolObjects(name,
				"gov.nist", "UDP", false, null);
		sipListener = new TestSipListener(port, lbSIPint, protocolObjects, false);
		sipListener.appServer = this;
		try {
			sipProvider = sipListener.createProvider();
			sipProvider.addSipListener(sipListener);
			protocolObjects.start();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		appServerNode = new SIPNode(name, "127.0.0.1");
		appServerNode.getProperties().put("udpPort", port);
		timer.schedule(new TimerTask() {
			
			@Override
			public void run() {
				ArrayList<SIPNode> nodes = new ArrayList<SIPNode>();
				nodes.add(appServerNode);
				sendKeepAliveToBalancers(nodes);
			}
		}, 1000, 1000);
	}
	
	public void stop() {
		timer.cancel();
		if(protocolObjects != null)
		protocolObjects.sipStack.stop();
		protocolObjects=null;
		//sendCleanShutdownToBalancers();
	}

	private void sendKeepAliveToBalancers(ArrayList<SIPNode> info) {
		if(sendHeartbeat) {
			Thread.currentThread().setContextClassLoader(NodeRegisterRMIStub.class.getClassLoader());
			if(balancers != null) {
				for(String balancer:balancers.replaceAll(" ","").split(",")) {
					if(balancer.length()<2) continue;
					String host;
					String port;
					int semi = balancer.indexOf(':');
					if(semi>0) {
						host = balancer.substring(0, semi);
						port = balancer.substring(semi+1);
					} else {
						host = balancer;
						port = "2000";
					}
					try {
						Registry registry = LocateRegistry.getRegistry(host, Integer.parseInt(port));
						NodeRegisterRMIStub reg=(NodeRegisterRMIStub) registry.lookup("SIPBalancer");
						reg.handlePing(info);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				
			} else {
				try {
					Registry registry = LocateRegistry.getRegistry(lbAddress, lbRMIport);
					NodeRegisterRMIStub reg=(NodeRegisterRMIStub) registry.lookup("SIPBalancer");
					reg.handlePing(info);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}

	}	
	public void sendCleanShutdownToBalancers() {
		ArrayList<SIPNode> nodes = new ArrayList<SIPNode>();
		nodes.add(appServerNode);
		sendCleanShutdownToBalancers(nodes);
	}
	
	public void sendCleanShutdownToBalancers(ArrayList<SIPNode> info) {
		Thread.currentThread().setContextClassLoader(NodeRegisterRMIStub.class.getClassLoader());
		try {
			Registry registry = LocateRegistry.getRegistry(lbAddress, lbRMIport);
			NodeRegisterRMIStub reg=(NodeRegisterRMIStub) registry.lookup("SIPBalancer");
			reg.forceRemoval(info);
			stop();
			Thread.sleep(2000); // delay the OK for a while
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public TestSipListener getTestSipListener() {
		return this.sipListener;
	}

}

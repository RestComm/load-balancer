package org.mobicents.tools.sip.balancer;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

import org.mobicents.tools.sip.balancer.NodeRegisterRMIStub;

import javax.sip.SipProvider;
import javax.sip.message.Response;

public class AppServerWithRmi extends AppServer {

	Timer timer;
	int port;
	String name;
	SIPNode appServerNode;
	public boolean sendHeartbeat = true;
	String lbAddress;
	int lbRMIport;
	int lbSIPext;
	int lbSIPint;
	String transport;
	protected String balancers;
	public SipProvider sipProvider;
	public String version;
	AtomicBoolean stopFlag = new AtomicBoolean(false);
	boolean isDummy;
	boolean isMediaFailure;
	boolean isFirstStart = true;
	boolean isIpv6 = false;
	
	public AppServerWithRmi(String appServer, int port, String lbAddress, int lbRMI, int lbSIPext, int lbSIPint, String version , String transport) 
	{
		super();
		this.port = port;
		this.name = appServer;
		this.lbAddress = lbAddress;
		this.lbRMIport = lbRMI;
		this.lbSIPext = lbSIPext;
		this.lbSIPint = lbSIPint;
		this.version = version;
		this.transport=transport;
	}
	public AppServerWithRmi(boolean isIpv6,String appServer, int port, String lbAddress, int lbRMI, int lbSIPext, int lbSIPint, String version , String transport) 
	{
		this(appServer, port, lbAddress, lbRMI, lbSIPext, lbSIPint, version , transport);
		this.isIpv6 = true;
	}
	
	public AppServerWithRmi(String appServer, int port, String lbAddress, int lbRMI, int lbSIPext, int lbSIPint, String version , String transport, boolean isDummy)
	{
		this(appServer, port, lbAddress, lbRMI, lbSIPext, lbSIPint, version , transport);
		this.isDummy = isDummy; 
	}
	
	public AppServerWithRmi(String appServer, int port, String lbAddress, int lbRMI, int lbSIPext, int lbSIPint, String version , String transport, boolean isDummy, boolean isMediaFailure)
	{
		this(appServer, port, lbAddress, lbRMI, lbSIPext, lbSIPint, version , transport);
		this.isDummy = isDummy;
		this.isMediaFailure = isMediaFailure;
	}
	
	public void setBalancers(String balancers) {
		this.balancers = balancers;
	}
	
		
	public void setEventListener(EventListener listener) {
		sipListener.eventListener = listener;
	}

	public void start() {
		timer = new Timer();
		protocolObjects = new ProtocolObjects(name,	"gov.nist", transport, false, false, true);
			if(!isDummy)
			{
				if(!isMediaFailure||!isFirstStart)
				{
					sipListener = new TestSipListener(isIpv6,port, lbSIPint, protocolObjects, false);
				}
				else
				{
					sipListener = new TestSipListener(isIpv6,port, lbSIPint, protocolObjects, false);
					sipListener.setRespondWithError(Response.SERVICE_UNAVAILABLE);
				}
			}
			else
			{
				sipListener = new TestSipListener(isIpv6,port+1, lbSIPint, protocolObjects, false);
			}

		sipListener.appServer = this;
		try {
			sipProvider = sipListener.createProvider();
			sipProvider.addSipListener(sipListener);
			protocolObjects.start();
		} catch (Exception e) {
			e.printStackTrace();
		}
		if(!isIpv6)
			appServerNode = new SIPNode(name, "127.0.0.1");
		else
			appServerNode = new SIPNode(name, "::1");
		appServerNode.getProperties().put(transport.toLowerCase() + "Port", port);		
		appServerNode.getProperties().put("version", version);
		appServerNode.getProperties().put("sessionId", ""+System.currentTimeMillis());
		stopFlag.set(false);
		timer.schedule(new TimerTask() {
			
			@Override
			public void run() {
				try {
					ArrayList<SIPNode> nodes = new ArrayList<SIPNode>();
					nodes.add(appServerNode);
					appServerNode.getProperties().put("version", version);
					if(!stopFlag.get())
					sendKeepAliveToBalancers(nodes);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}, 1000, 1000);
	}
	
	public void stop() {
		isFirstStart = false;
		stopFlag.getAndSet(true);
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
	
//	public SIPNode getNode() {
//		return appServerNode;
//	}

	public void gracefulShutdown()
	{
		appServerNode.getProperties().put("GRACEFUL_SHUTDOWN", "true");
	}
	
}

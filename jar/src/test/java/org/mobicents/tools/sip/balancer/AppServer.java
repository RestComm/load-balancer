/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, Red Hat, Inc. and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.mobicents.tools.sip.balancer;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

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
	String transport;
	protected String balancers;
	public SipProvider sipProvider;
	public String version;
	AtomicBoolean stopFlag = new AtomicBoolean(false);
	
	public AppServer(String appServer, int port, String lbAddress, int lbRMI, int lbSIPext, int lbSIPint, String version , String transport) {
		this.port = port;
		this.name = appServer;
		this.lbAddress = lbAddress;
		this.lbRMIport = lbRMI;
		this.lbSIPext = lbSIPext;
		this.lbSIPint = lbSIPint;
		this.version = version;
		this.transport=transport;
	}
	
	public void setBalancers(String balancers) {
		this.balancers = balancers;
	}
	
		
	public void setEventListener(EventListener listener) {
		sipListener.eventListener = listener;
	}

	public void start() {
		timer = new Timer();
		
		protocolObjects = new ProtocolObjects(name,	"gov.nist", transport, false, null);		
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
		appServerNode.getProperties().put(transport.toLowerCase() + "Port", port);		
		appServerNode.getProperties().put("version", version);
		
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
	
	public SIPNode getSIPNode() {
		return appServerNode;
	}

}

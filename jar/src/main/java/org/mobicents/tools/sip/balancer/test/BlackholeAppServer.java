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

package org.mobicents.tools.sip.balancer.test;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import javax.sip.SipProvider;

import org.mobicents.tools.heartbeat.api.Node;
import org.mobicents.tools.sip.balancer.NodeRegisterRMIStub;

public class BlackholeAppServer {
	public ProtocolObjects protocolObjects;
	Timer timer;
	int port;
	String name;
	Node appServerNode;
	public boolean sendHeartbeat = true;
	String lbAddress;
	int lbRMIport;
	int lbSIPext;
	int lbSIPint;
	public SipProvider sipProvider;

	public BlackholeAppServer(String appServer, int port, String lbAddress, int lbRMI, int lbSIPext, int lbSIPint) {
		this.port = port;
		this.name = appServer;
		this.lbAddress = lbAddress;
		this.lbRMIport = lbRMI;
		this.lbSIPext = lbSIPext;
		this.lbSIPint = lbSIPint;
	}
	
	public BlackholeAppServer(String appServer, int port) {
		this(appServer, port, "127.0.0.1");

	} 
	
	public BlackholeAppServer(String appServer, int port, String address) {
		this(appServer, port, address, 2000, 5060, 5065);

	} 
	
	DatagramSocket socket;
	public long numPacketsReceived;
	DatagramPacket packet = new DatagramPacket(new byte[1000], 1000);
	Thread thread;
	public void start() {
		timer = new Timer();
		try {
			socket = new DatagramSocket(port, InetAddress.getByName(lbAddress));
			thread = new Thread() {
				public void run() {
					try {
						while(true) {
							socket.receive(packet);
							numPacketsReceived++;
						}
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			};
			thread.start();
		} catch (SocketException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		appServerNode = new Node(name, "127.0.0.1");
		appServerNode.getProperties().put("udpPort", "" + port);
		
	}
	
	public void stop() {
		try {
			thread.interrupt();
			thread.stop();
		} catch (Exception e) {}
		
		timer.cancel();
		socket.close();
		if(protocolObjects != null)
			protocolObjects.sipStack.stop();
		protocolObjects=null;
		//sendCleanShutdownToBalancers();
	}

	private void sendKeepAliveToBalancers(ArrayList<Node> info) {
		if(sendHeartbeat) {
			Thread.currentThread().setContextClassLoader(NodeRegisterRMIStub.class.getClassLoader());
			try {
				Registry registry = LocateRegistry.getRegistry(lbAddress, lbRMIport);
				NodeRegisterRMIStub reg=(NodeRegisterRMIStub) registry.lookup("SIPBalancer");
				reg.handlePing(info);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

	}	
	public void sendCleanShutdownToBalancers() {
		ArrayList<Node> nodes = new ArrayList<Node>();
		nodes.add(appServerNode);
		sendCleanShutdownToBalancers(nodes);
	}
	
	public void sendCleanShutdownToBalancers(ArrayList<Node> info) {
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
	

}

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

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import javax.sip.SipProvider;

import org.mobicents.tools.heartbeat.impl.Node;

public class BlackholeAppServer {
	public ProtocolObjects protocolObjects;
	public TestSipListener sipListener;
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
	
	public void setEventListener(EventListener listener) {
		sipListener.eventListener = listener;
	}
	ServerSocket tcpSocket;
	Socket sock;
	DatagramSocket socket;
	public long numUnitsReceived;
	DatagramPacket packet = new DatagramPacket(new byte[1000], 1000);
	byte[] temp = new byte[10000];
	Thread thread;
	Thread tcpThread;
	String lastString = "";
	public void start() {
		timer = new Timer();
		try {
			socket = new DatagramSocket(port, InetAddress.getByName(lbAddress));
			try {
				tcpSocket = new ServerSocket(port);
				tcpThread = new Thread() {
					public void run() {
						while(true) {
							
							try {
								sock = tcpSocket.accept();
								new Thread() {
									public void run() {
										while(true) {
											try {
												numUnitsReceived+=sock.getInputStream().read(temp);
											} catch (IOException e) {
												return;
											}
										}
									}
								}.start();
								
							} catch (IOException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}
					}
				};
				tcpThread.start();
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			thread = new Thread() {
				public void run() {
					try {
						while(true) {
							socket.receive(packet);
							numUnitsReceived++;
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
		appServerNode.getProperties().put("udpPort",""+ port);
		appServerNode.getProperties().put("tcpPort",""+ port);
		
			
	}
	
	public void stop() {
		try {
			thread.interrupt();
			thread.stop();
		} catch (Exception e) {}
		try {
			tcpThread.interrupt();
			tcpThread.stop();
		} catch (Exception e) {}
		try {
			sock.shutdownInput();
			sock.close();
			tcpSocket.close();
		} catch (IOException e) {
		}
		timer.cancel();
		socket.close();
		if(protocolObjects != null)
			protocolObjects.sipStack.stop();
		protocolObjects=null;
		//sendCleanShutdownToBalancers();
	}
	
	public TestSipListener getTestSipListener() {
		return this.sipListener;
	}

}

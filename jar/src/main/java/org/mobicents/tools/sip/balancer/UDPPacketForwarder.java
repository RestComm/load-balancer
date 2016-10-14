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

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;

public class UDPPacketForwarder {
	int fromPort;
	int toPort;
	String bindAddress;
	boolean running;
	DatagramSocket fromSocket;
	Thread worker;
	String destinations;

	public LinkedList<String> sipMessages;
	public HashSet<String> sipMessageWithoutRetrans;

	static long next = 0;

	public UDPPacketForwarder(int fromPort, String destinations, String bind) {
		this.fromPort = fromPort;
		this.bindAddress = bind;
		this.destinations = destinations;

		sipMessages = new LinkedList<String>();
		sipMessageWithoutRetrans = new HashSet<String>();
	}

	public void setDestinations(String destinations) {
		this.destinations = destinations;
	}

	public void start() {
		try {
			fromSocket = new DatagramSocket(fromPort, InetAddress.getByName(bindAddress));
			running = true;
			worker = new Thread() {
				@Override
				public void run() {
					while(running) {
						DatagramPacket packet = new DatagramPacket(new byte[3000], 3000);
						try {
							fromSocket.receive(packet);
							String sipMessage = new String(packet.getData());
							sipMessages.add(sipMessage);
							sipMessageWithoutRetrans.add(sipMessage);
							ArrayList<String> list = new ArrayList<String>();
							for(String dest:destinations.split(",")) {
								if(dest.length()>2) list.add(dest);
							}
							int size = list.size();
							String dest = list.get((int) ((next++)%size));
							String port;
							int semi = dest.indexOf(':');
							if(semi>0) {
								port = dest.substring(semi+1);
							} else {
								port = "5060";
							}
							packet.setPort(Integer.parseInt(port));
							//packet.setSocketAddress(new InetSocketAddress(host, Integer.parseInt(port)));
							fromSocket.send(packet);
						}						
						catch (Exception e) {
							//log only if not stopped yet
							if(running)
								e.printStackTrace();
						}
					}

				}
			};
			worker.start();
		} catch (Exception e) {
			throw new RuntimeException("Error", e);
		}
	}

	public void stop() {
		running = false;
		try {
			worker.interrupt();			
			fromSocket.close();
			fromSocket = null;
		} catch (Exception e) {
			//e.printStackTrace();
		}

	}
}

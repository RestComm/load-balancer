package org.mobicents.tools.sip.balancer;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
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
							String host;
							String port;
							int semi = dest.indexOf(':');
							if(semi>0) {
								host = dest.substring(0, semi);
								port = dest.substring(semi+1);
							} else {
								host = dest;
								port = "5060";
							}
							packet.setPort(Integer.parseInt(port));
							//packet.setSocketAddress(new InetSocketAddress(host, Integer.parseInt(port)));
							fromSocket.send(packet);
						} catch (Exception e) {
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
		} catch (Exception e) {
			//e.printStackTrace();
		}
		
	}
}

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

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Properties;

import org.mobicents.tools.sip.balancer.BalancerRunner;

public class SipBalancerUdpTest {
	static final String inviteRequest = "INVITE sip:joe@company.com SIP/2.0\r\n"+
	"To: sip:joe@company.com\r\n"+
	"From: sip:caller@university.edu ;tag=1234\r\n"+
	"Call-ID: 0ha0isnda977644900765@10.0.0.1\r\n"+
	"CSeq: 9 INVITE\r\n"+
	"Via: SIP/2.0/UDP 135.180.130.133\r\n"+
	"Content-Type: application/sdp\r\n"+
	"\r\n"+
	"v=0\r\n"+
	"o=mhandley 29739 7272939 IN IP4 126.5.4.3\r\n" +
	"c=IN IP4 135.180.130.88\r\n" +
	"m=video 3227 RTP/AVP 31\r\n" +
	"m=audio 4921 RTP/AVP 12\r\n" +
	"a=rtpmap:31 LPC\r\n";

	static byte[] inviteRequestBytes = inviteRequest.getBytes();
	
	static final String ringing = 	"SIP/2.0 180 Ringing\n" + "To: <sip:LittleGuy@there.com>;tag=5432\n" +
	"Via: SIP/2.0/UDP 127.0.0.1:5065;branch=z9hG4bK-3530-488ff2840f609639903eff914df9870f202e2zsd,SIP/2.0/UDP 127.0.0.1:5060;branch=z9hG4bK-3530-488ff2840f609639903eff914df9870f202e2,SIP/2.0/UDP 127.0.0.1:5033;branch=z9hG4bK-3530-488ff2840f609639903eff914df9870f\n"+
	"Record-Route: <sip:127.0.0.1:5065;transport=udp;lr>,<sip:127.0.0.1:5060;transport=udp;lr>\n"+
	"CSeq: 1 INVITE\n"+
	"Call-ID: 202e236d75a43c17b234a992873c3c74@127.0.0.1\n"+
	"From: <sip:BigGuy@here.com>;tag=12345\n"+
	"Content-Length: 0\n";
	
	static byte[] ringingBytes = ringing.getBytes();
	
	BalancerRunner balancer;
	int numNodes = 2;
	BlackholeAppServer server;
	
	public void setUp() throws Exception {
		
		
		balancer = new BalancerRunner();
		Properties properties = new Properties();
		properties.setProperty("javax.sip.STACK_NAME", "SipBalancerForwarder");
		properties.setProperty("javax.sip.AUTOMATIC_DIALOG_SUPPORT", "off");
		// You need 16 for logging traces. 32 for debug + traces.
		// Your code will limp at 32 but it is best for debugging.
		properties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "0");
		properties.setProperty("gov.nist.javax.sip.DEBUG_LOG",
				"logs/sipbalancerforwarderdebug.txt");
		properties.setProperty("gov.nist.javax.sip.SERVER_LOG",
				"logs/sipbalancerforwarder.xml");
		properties.setProperty("gov.nist.javax.sip.THREAD_POOL_SIZE", "100");
		properties.setProperty("gov.nist.javax.sip.REENTRANT_LISTENER", "true");
		properties.setProperty("gov.nist.javax.sip.CANCEL_CLIENT_TRANSACTION_CHECKED", "false");		
		
		properties.setProperty("host", "127.0.0.1");
		properties.setProperty("externalHost", "127.0.0.1");
		properties.setProperty("internalHost", "127.0.0.1");
		properties.setProperty("internalUdpPort", "5065");
		properties.setProperty("externalUdpPort", "5060");
		properties.setProperty("usePrettyEncoding", "true");
		balancer.start(properties);
		
		
		server = new BlackholeAppServer("blackhole", 18452, "127.0.0.1");
		server.start();
		Thread.sleep(5000);
		
	}
	static InetAddress localhost;
	static int callIdByteStart = -1;
	static {try {
		localhost = InetAddress.getByName("127.0.0.1");
		byte[] callid = "0ha0isn".getBytes();
		for(int q=0;q<1000; q++) {
			int found = -1;;
			for(int w=0;w<callid.length;w++) {
				if(callid[w] != inviteRequestBytes[q+w]) {
					break;
				}
				found = w;
			}
			if(found >0) {callIdByteStart = q; break;}
		}
	} catch (UnknownHostException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}}
	private static long n = 0;
	private static void modCallId() {
		n++;
		inviteRequestBytes[callIdByteStart] = (byte) (n&0xff);
		inviteRequestBytes[callIdByteStart+1] = (byte) ((n>>8)&0xff);
		inviteRequestBytes[callIdByteStart+2] = (byte) ((n>>16)&0xff);
	}
	
	public void testInvitePerformanceLong() {
		testMessagePerformance(10*60*1000, 100000, inviteRequestBytes);
	}
	
	public void testInvitePerformance10sec() {
		testMessagePerformance(10*1000, 100, inviteRequestBytes);
	}
	
	public void testInvitePerformanceDiffCallId10sec() {
		testDiffCallIdPerformance(10*1000, 100);
	}
	
	public void testRingingPerformance10sec() {
		testMessagePerformance(10*1000, 100, ringingBytes);
	}
	
	private void testMessagePerformance(int timespan, int maxLostPackets, byte[] bytes) {
		try {
			DatagramSocket socket = new DatagramSocket(33276, localhost);
			long sentPackets = 0;
			long startTime = System.currentTimeMillis();
			while(true) {
				boolean diffNotTooBig = sentPackets - server.numPacketsReceived<maxLostPackets;
				boolean thereIsStillTime = System.currentTimeMillis()-startTime<timespan;
				if(!thereIsStillTime) {
					break;
				}
				if(diffNotTooBig) {
					socket.send(new DatagramPacket(bytes,bytes.length,localhost, 5060));
					sentPackets++;
				} else {
					Thread.sleep(1);
				}
			}
			System.out.println("Packets sent in " + timespan + " ms are " + sentPackets + "(making " + sentPackets/((double)(timespan)/1000.) + " initial requests per second)");
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private void testDiffCallIdPerformance(int timespan, int maxLostPackets) {
		try {
			DatagramSocket socket = new DatagramSocket(33276, localhost);
			long sentPackets = 0;
			long startTime = System.currentTimeMillis();
			while(true) {
				boolean diffNotTooBig = sentPackets - server.numPacketsReceived<maxLostPackets;
				boolean thereIsStillTime = System.currentTimeMillis()-startTime<timespan;
				if(!thereIsStillTime) {
					break;
				}
				if(diffNotTooBig) {
					socket.send(new DatagramPacket(inviteRequestBytes,inviteRequestBytes.length,localhost, 5060));
					modCallId();
					sentPackets++;
				} else {
					Thread.sleep(1);
				}
			}
			System.out.println("Packets sent in " + timespan + " ms are " + sentPackets + "(making " + sentPackets/((double)(timespan)/1000.) + " initial requests per second)");
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void tearDown() throws Exception {
		server.stop();
		balancer.stop();
	}
	
	public static void main(String[] args) {
		try {
			SipBalancerUdpTest test = new SipBalancerUdpTest();
			test.setUp();
			String timeString = "10";
			if(args.length>0) {
				timeString = args[0];
			}
			Integer time = Integer.parseInt(timeString);
			Integer maxDiff = Integer.parseInt(args[0]);
			test.testMessagePerformance(time*1000, maxDiff, inviteRequestBytes);
			test.tearDown();
			System.exit(0);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}

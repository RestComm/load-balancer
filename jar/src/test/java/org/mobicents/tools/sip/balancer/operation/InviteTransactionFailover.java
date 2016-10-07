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

package org.mobicents.tools.sip.balancer.operation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import javax.sip.ListeningPoint;
import javax.sip.address.SipURI;
import javax.sip.header.RecordRouteHeader;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mobicents.tools.configuration.LoadBalancerConfiguration;
import org.mobicents.tools.sip.balancer.AppServer;
import org.mobicents.tools.sip.balancer.BalancerRunner;
import org.mobicents.tools.sip.balancer.EventListener;


public class InviteTransactionFailover {
	BalancerRunner balancer;
	int numNodes = 2;
	AppServer[] servers = new AppServer[numNodes];
	Shootist shootist;
	AppServer ringingAppServer;
	AppServer okAppServer;

	@Before
	public void setUp() throws Exception {
		shootist = new Shootist();
		
		balancer = new BalancerRunner();
		LoadBalancerConfiguration lbConfig = new LoadBalancerConfiguration();
		lbConfig.getCommonConfiguration().setHost(null);
		lbConfig.getSipConfiguration().getExternalLegConfiguration().setHost("127.0.0.1");
		lbConfig.getSipConfiguration().getInternalLegConfiguration().setHost("127.0.0.1");
		lbConfig.getSipConfiguration().getInternalLegConfiguration().setUdpPort(5065);
		balancer.start(lbConfig);
		
		
		for(int q=0;q<servers.length;q++) {
			servers[q] = new AppServer("node" + q,4060+q , "127.0.0.1", 2000, 5060, 5065, "0", ListeningPoint.UDP);
			servers[q].start();
		}
		Thread.sleep(5000);
	}

	@After
	public void tearDown() throws Exception {
		for(int q=0;q<servers.length;q++) {
			servers[q].stop();
		}
		shootist.stop();
		balancer.stop();
	}
	
	@Test
	public void testFailDetection() throws Exception {
			
			String[] nodes = balancer.getNodeList();
			assertEquals(numNodes, nodes.length);
			servers[0].sendHeartbeat = false;
			Thread.sleep(14000);
			//assertEquals(numNodes-1, balancer.getInvocationContext("0").nodes.size());
			assertEquals(numNodes-1, balancer.getInvocationContext("0").sipNodeMap.size());
			assertEquals(numNodes-1, balancer.getNodeList().length);
	}

	@Test
	public void testAllNodesDead() throws Exception {
		for(AppServer as:servers) {
			as.sendCleanShutdownToBalancers();
			as.sendHeartbeat=false;
		}
		Thread.sleep(1000);
		shootist.callerSendsBye = true;
		shootist.sendInitialInvite();

		Thread.sleep(5000);
		assertEquals(500, shootist.responses.get(0).getStatusCode());
		assertNotNull(shootist.responses.get(0).getHeader("Via"));
	}

//	private void _BAD_testInviteTx() throws Exception {
//		ProtocolObjects senderProtocolObjects = new ProtocolObjects("forward-udp-sender",
//				"gov.nist", "udp", false, null);
//		TestSipListener sender = new TestSipListener(5080, 5060, senderProtocolObjects, true);
//		SipProvider senderProvider = sender.createProvider();
//
//
//		senderProvider.addSipListener(sender);
//
//		senderProtocolObjects.start();
//
//		String fromName = "forward-tcp-sender";
//		String fromSipAddress = "sip-servlets.com";
//		SipURI fromAddress = senderProtocolObjects.addressFactory.createSipURI(
//				fromName, fromSipAddress);
//		
//		String toSipAddress = "sip-servlets.com";
//		String toUser = "forward-receiver";
//		SipURI toAddress = senderProtocolObjects.addressFactory.createSipURI(
//				toUser, toSipAddress);
//		
//		sender.sendSipRequest("INVITE", fromAddress, toAddress, null, null, false);
//		Thread.sleep(20000);
//	}
	
	@Test
	public void testSimpleShutdown() throws Exception {
		EventListener failureEventListener = new EventListener() {
			boolean once = false;
			@Override
			public synchronized void uasAfterResponse(int statusCode, AppServer source) {
				if(!once) {
					once = true;
					System.out.println("HERE " + once);
					source.sendCleanShutdownToBalancers();
					
				}
				
			}
			
			@Override
			public void uasAfterRequestReceived(String method, AppServer source) {
				// TODO Auto-generated method stub
				
			}

			@Override
			public void uacAfterRequestSent(String method, AppServer source) {
				// TODO Auto-generated method stub
				
			}

			@Override
			public void uacAfterResponse(int statusCode, AppServer source) {
				// TODO Auto-generated method stub
				
			}
		};
		for(AppServer as:servers) as.setEventListener(failureEventListener);
		shootist.callerSendsBye = true;
		shootist.sendInitialInvite();
		Thread.sleep(12000);
		assertEquals(1,balancer.getNodes().size());
		//if(balancer.getNodes().size()!=1) fail("Expected one dead node");
	}

	@Test
	public void testASactingAsUAC() throws Exception {
		
		EventListener failureEventListener = new EventListener() {
			
			@Override
			public void uasAfterResponse(int statusCode, AppServer source) {
				
				
			}
			
			@Override
			public void uasAfterRequestReceived(String method, AppServer source) {
				// TODO Auto-generated method stub
				
			}

			@Override
			public void uacAfterRequestSent(String method, AppServer source) {
				// TODO Auto-generated method stub
				
			}

			@Override
			public void uacAfterResponse(int statusCode, AppServer source) {
				if(statusCode == 180) {
					ringingAppServer = source;
					source.sendCleanShutdownToBalancers();		
				} else if(statusCode == 200) {
					okAppServer = source;
					
				}
			}
		};
		for(AppServer as:servers) as.setEventListener(failureEventListener);
		shootist.callerSendsBye = true;
		
		String fromName = "sender";
		String fromHost = "sip-servlets.com";
		SipURI fromAddress = servers[0].protocolObjects.addressFactory.createSipURI(
				fromName, fromHost);
				
		String toUser = "replaces";
		String toHost = "sip-servlets.com";
		SipURI toAddress = servers[0].protocolObjects.addressFactory.createSipURI(
				toUser, toHost);
		
		SipURI ruri = servers[0].protocolObjects.addressFactory.createSipURI(
				"usera", "127.0.0.1:5033");
		ruri.setLrParam();
		SipURI route = servers[0].protocolObjects.addressFactory.createSipURI(
				"lbint", "127.0.0.1:5065");
		route.setParameter("node_host", "127.0.0.1");
		route.setParameter("node_port", "4060");
		route.setLrParam();
		shootist.start();
		//servers[0].sipListener.sendSipRequest("INVITE", fromAddress, toAddress, null, null, false);
		servers[0].sipListener.sendSipRequest("INVITE", fromAddress, toAddress, null, route, false, null, null, ruri);
		Thread.sleep(16000);
		assertTrue(shootist.inviteRequest.getHeader(RecordRouteHeader.NAME).toString().contains("node_host"));
		assertNotSame(ringingAppServer, okAppServer);
		assertNotNull(ringingAppServer);
		assertNotNull(okAppServer);
	}

}

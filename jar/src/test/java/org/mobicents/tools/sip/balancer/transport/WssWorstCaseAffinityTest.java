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

package org.mobicents.tools.sip.balancer.transport;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import gov.nist.javax.sip.ListeningPointExt;
import gov.nist.javax.sip.stack.NioMessageProcessorFactory;

import java.util.Properties;

import javax.sip.address.SipURI;
import javax.sip.header.RecordRouteHeader;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mobicents.tools.sip.balancer.AppServer;
import org.mobicents.tools.sip.balancer.BalancerRunner;
import org.mobicents.tools.sip.balancer.EventListener;
import org.mobicents.tools.sip.balancer.WorstCaseUdpTestAffinityAlgorithm;
import org.mobicents.tools.sip.balancer.operation.Shootist;
import org.mobicents.tools.smpp.balancer.ConfigInit;

public class WssWorstCaseAffinityTest{
	BalancerRunner balancer;
	int numNodes = 2;
	AppServer[] servers = new AppServer[numNodes];
	Shootist shootist;
	static AppServer invite;
	static AppServer ack;
	static AppServer bye;
	AppServer ringingAppServer;
	AppServer okAppServer;

	@Before
	public void setUp() throws Exception {
		shootist = new Shootist(ListeningPointExt.WSS,5061);
		balancer = new BalancerRunner();
		Properties properties = new Properties();
		properties.setProperty("javax.sip.STACK_NAME", "SipBalancerForwarder");
		properties.setProperty("javax.sip.AUTOMATIC_DIALOG_SUPPORT", "off");
		// You need 16 for logging traces. 32 for debug + traces.
		// Your code will limp at 32 but it is best for debugging.
		properties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "32");
		properties.setProperty("gov.nist.javax.sip.DEBUG_LOG",
				"logs/sipbalancerforwarderdebug.txt");
		properties.setProperty("gov.nist.javax.sip.SERVER_LOG",
				"logs/sipbalancerforwarder.xml");
		properties.setProperty("gov.nist.javax.sip.THREAD_POOL_SIZE", "2");
		properties.setProperty("gov.nist.javax.sip.REENTRANT_LISTENER", "true");
		properties.setProperty("gov.nist.javax.sip.CANCEL_CLIENT_TRANSACTION_CHECKED", "false");
		properties.setProperty("algorithmClass", WorstCaseUdpTestAffinityAlgorithm.class.getName());
		properties.setProperty("gov.nist.javax.sip.MESSAGE_PROCESSOR_FACTORY", NioMessageProcessorFactory.class.getName());
		properties.setProperty("gov.nist.javax.sip.PATCH_SIP_WEBSOCKETS_HEADERS", "false");
		
		properties.setProperty("host", "127.0.0.1");
		properties.setProperty("internalPort", "5065");
		properties.setProperty("externalPort", "5060");
		properties.setProperty("internalSecurePort", "5066");
		properties.setProperty("externalSecurePort", "5061");
		properties.setProperty("earlyDialogWorstCase", "true");
		properties.setProperty("javax.net.ssl.keyStore", ConfigInit.class.getClassLoader().getResource("keystore").getFile());
		properties.setProperty("javax.net.ssl.keyStorePassword", "123456");
		properties.setProperty("javax.net.ssl.trustStore", ConfigInit.class.getClassLoader().getResource("keystore").getFile());
		properties.setProperty("javax.net.ssl.trustStorePassword", "123456");
		properties.setProperty("gov.nist.javax.sip.TLS_CLIENT_PROTOCOLS", "TLSv1");
		properties.setProperty("gov.nist.javax.sip.TLS_CLIENT_AUTH_TYPE", "Disabled");
		properties.setProperty("isTransportWs", "true");
		
				
		balancer.start(properties);
		
		
		for(int q=0;q<servers.length;q++) {
			servers[q] = new AppServer("node" + q,4060+q , "127.0.0.1", 2000, 5061, 5066, "0", ListeningPointExt.WSS);			
			servers[q].start();		
		}
		
		Thread.sleep(5000);
	}

	@After
	public void tearDown() throws Exception {
		shootist.stop();
		for(int q=0;q<servers.length;q++) {
			servers[q].stop();
		}
		balancer.stop();
	}

	@Test
	public void testInviteAckLandOnDifferentNodes() throws Exception {
		EventListener failureEventListener = new EventListener() {

			@Override
			public void uasAfterResponse(int statusCode, AppServer source) {
				
			}
			
			@Override
			public void uasAfterRequestReceived(String method, AppServer source) {
				if(method.equals("INVITE")) invite = source;
				if(method.equals("ACK")) {
					ack = source;
				
					}
				if(method.equals("BYE")) {
					bye = source;

				}

				
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
		Thread.sleep(15000);
		assertNotNull(invite);
		assertNotNull(ack);
		assertNotSame(invite, bye);
		assertEquals(ack, invite);			
	}
	
	@Test
	public void testOKRingingLandOnDifferentNodes() throws Exception {
		
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
				} else if(statusCode == 200){
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
				"lbint", "127.0.0.1:5066");
		route.setParameter("node_host", "127.0.0.1");
		route.setParameter("node_port", "4060");
		route.setTransportParam(ListeningPointExt.WSS);
		route.setLrParam();
		shootist.start();
		//servers[0].sipListener.sendSipRequest("INVITE", fromAddress, toAddress, null, null, false);
		servers[0].sipListener.sendSipRequest("INVITE", fromAddress, toAddress, null, route, false, null, null, ruri);
		Thread.sleep(16000);
		assertTrue(shootist.inviteRequest.getHeader(RecordRouteHeader.NAME).toString().contains("node_host"));
		assertNotNull(ringingAppServer);
		assertNotNull(okAppServer);
		assertNotSame(ringingAppServer, okAppServer);		
	}
}

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
import org.mobicents.tools.sip.balancer.SinglePointTest;
import org.mobicents.tools.sip.balancer.WorstCaseUdpTestAffinityAlgorithm;
import org.mobicents.tools.sip.balancer.operation.Shootist;

public class TlsWorstCaseAffinityTest{
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
		shootist = new Shootist(ListeningPoint.TLS,5061);
		balancer = new BalancerRunner();
		LoadBalancerConfiguration lbConfig = new LoadBalancerConfiguration();
		lbConfig.getSipConfiguration().getExternalLegConfiguration().setTcpPort(null);
		lbConfig.getSipConfiguration().getInternalLegConfiguration().setTlsPort(5066);
		lbConfig.getSipConfiguration().getExternalLegConfiguration().setTlsPort(5061);
		lbConfig.getSipConfiguration().getAlgorithmConfiguration().setAlgorithmClass(WorstCaseUdpTestAffinityAlgorithm.class.getName());
		lbConfig.getSipConfiguration().getAlgorithmConfiguration().setEarlyDialogWorstCase(true);
		lbConfig.getSipStackConfiguration().getSipStackProperies().setProperty("javax.net.ssl.keyStore", SinglePointTest.class.getClassLoader().getResource("keystore").getFile());
		lbConfig.getSipStackConfiguration().getSipStackProperies().setProperty("javax.net.ssl.trustStorePassword", "123456");
		lbConfig.getSipStackConfiguration().getSipStackProperies().setProperty("javax.net.ssl.trustStore",SinglePointTest.class.getClassLoader().getResource("keystore").getFile());
		lbConfig.getSipStackConfiguration().getSipStackProperies().setProperty("javax.net.ssl.keyStorePassword","123456");
		balancer.start(lbConfig);
		
		for(int q=0;q<servers.length;q++) {
			servers[q] = new AppServer("node" + q,4060+q , "127.0.0.1", 2000, 5061, 5066, "0", ListeningPoint.TLS);			
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
		assertEquals(ack, invite);
		assertNotSame(ack, bye);		
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
		route.setTransportParam(ListeningPoint.TLS);
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

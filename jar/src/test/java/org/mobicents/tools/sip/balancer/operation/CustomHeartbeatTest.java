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

import java.util.List;

import javax.sip.ListeningPoint;
import javax.sip.address.SipURI;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mobicents.tools.configuration.LoadBalancerConfiguration;
import org.mobicents.tools.heartbeat.impl.Node;
import org.mobicents.tools.sip.balancer.AppServer;
import org.mobicents.tools.sip.balancer.BalancerRunner;
import org.mobicents.tools.sip.balancer.EventListener;

public class CustomHeartbeatTest{
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
			servers[q] = new AppServer("node" + q,4060+q, "127.0.0.1", 2000, 5060, 5060, "0", ListeningPoint.UDP, 2222+q);
			servers[q].sendHeartbeat = false;
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
	public void testCustomHeartbeat() throws Exception {
		
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
				} else {
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
				"lbaddress_InternalPort", "127.0.0.1:5065");
		route.setParameter("node_host", "127.0.0.1");
		route.setParameter("node_port", "4060");
		route.setLrParam();
		shootist.start();
		//servers[0].sipListener.sendSipRequest("INVITE", fromAddress, toAddress, null, null, false);
		//servers[0].sendHeartbeat = false;
		//servers[1].sendHeartbeat = false;
		servers[0].sipListener.sendSipRequest("OPTIONS", fromAddress, toAddress, "tcpPort=1\nudpPort=2\nhostname=sipHeartbeat\nip=127.0.0.1", route, false, new String[]{"Mobicents-Heartbeat"}, new String[]{"1"}, ruri);
		Thread.sleep(4000);
		servers[0].sipListener.sendSipRequest("OPTIONS", fromAddress, toAddress, "tcpPort=1\nudpPort=2\nhostname=sipHeartbeat\nip=127.0.0.1", route, false, new String[]{"Mobicents-Heartbeat"}, new String[]{"1"}, ruri);
		Thread.sleep(4000);
		servers[0].sipListener.sendSipRequest("OPTIONS", fromAddress, toAddress, "tcpPort=1\nudpPort=2\nhostname=sipHeartbeat\nip=127.0.0.1", route, false, new String[]{"Mobicents-Heartbeat"}, new String[]{"1"}, ruri);
		Thread.sleep(4000);
		servers[0].sipListener.sendSipRequest("OPTIONS", fromAddress, toAddress, "tcpPort=1\nudpPort=2\nhostname=sipHeartbeat\nip=127.0.0.1", route, false, new String[]{"Mobicents-Heartbeat"}, new String[]{"1"}, ruri);
		Thread.sleep(4000);
		servers[0].sipListener.sendSipRequest("OPTIONS", fromAddress, toAddress, "tcpPort=1\nudpPort=2\nhostname=sipHeartbeat\nip=127.0.0.1", route, false, new String[]{"Mobicents-Heartbeat"}, new String[]{"1"}, ruri);
		List<Node> list = balancer.getNodes();
		Node node = list.get(0);
		assertEquals(node.getHostName(), "sipHeartbeat");
		assertEquals(1, list.size());
	}

}

/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2015, Telestax Inc and individual contributors
 * by the @authors tag.
 *
 * This program is free software: you can redistribute it and/or modify
 * under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */

package org.mobicents.tools.sip.balancer.algorithms;

import static org.junit.Assert.*;

import java.util.regex.Pattern;

import javax.sip.ListeningPoint;
import javax.sip.header.CallIdHeader;
import javax.sip.message.Response;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mobicents.ext.javax.sip.congestion.CongestionControlMessageValve;
import org.mobicents.tools.configuration.LoadBalancerConfiguration;
import org.mobicents.tools.sip.balancer.AppServer;
import org.mobicents.tools.sip.balancer.BalancerRunner;
import org.mobicents.tools.sip.balancer.SIPBalancerValveProcessor;
import org.mobicents.tools.sip.balancer.UserBasedAlgorithm;
import org.mobicents.tools.sip.balancer.operation.Shootist;

/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */

public class FromURIBasedAlgorithmTest {
	
		BalancerRunner balancer;
		int numNodes = 2;
		int numShootist = 20;
		AppServer[] servers = new AppServer[numNodes];
		Shootist [] shootists = new Shootist [numShootist];

		@Before
		public void setUp() throws Exception {
			for(int i = 0; i < shootists.length; i++)
			{
				shootists[i] = new Shootist("udp",5060,5033+i);
				shootists[i].callerSendsBye = true;
			}
			balancer = new BalancerRunner();
			LoadBalancerConfiguration lbConfig = new LoadBalancerConfiguration();
			lbConfig.getSipStackConfiguration().getSipStackProperies().setProperty("gov.nist.javax.sip.SIP_MESSAGE_VALVE", 
					CongestionControlMessageValve.class.getName() + "," + SIPBalancerValveProcessor.class.getName());
			lbConfig.getSipStackConfiguration().getSipStackProperies().setProperty("org.mobicents.ext.javax.sip.congestion.SIP_SCANNERS", 
					"sipx");
			lbConfig.getSipConfiguration().getInternalLegConfiguration().setUdpPort(5065);
			lbConfig.getSipConfiguration().getAlgorithmConfiguration().setAlgorithmClass(UserBasedAlgorithm.class.getName());
			lbConfig.getSipConfiguration().getAlgorithmConfiguration().setSipHeaderAffinityKey("From");
			balancer.start(lbConfig);
			
			for(int q=0;q<servers.length;q++) 
			{
				servers[q] = new AppServer("node" + q,4060+q , "127.0.0.1", 2000, 5060, 5065, "0", ListeningPoint.UDP);
				servers[q].start();
			}
			Thread.sleep(5000);
		}

		@After
		public void tearDown() throws Exception {
			for(Shootist s :shootists)
				s.stop();
			
			for(AppServer as: servers) {
				as.stop();
			}
			balancer.stop();
		}
		
		@Test
		public void testInviteFromToSameNode() throws Exception {
			for(int i = 0; i < shootists.length; i++)
			{
				shootists[i].sendInitialInvite();
				Thread.sleep(6000);
				shootists[i].sendBye();
				Thread.sleep(2000);
			}
			
			assertNotEquals(servers[0].getTestSipListener().isInviteReceived(),servers[1].getTestSipListener().isInviteReceived());
			assertNotEquals(servers[0].getTestSipListener().isAckReceived(),servers[1].getTestSipListener().isAckReceived());
			assertNotEquals(servers[0].getTestSipListener().getByeReceived(),servers[1].getTestSipListener().getByeReceived());
			
			for(Shootist s :shootists)
			{
				boolean wasRinging = false;
				boolean wasOk = false;
				for(Response res : s.responses)
				{
					if(res.getStatusCode() != Response.RINGING)
						wasRinging = true;
					if(res.getStatusCode() != Response.OK)
						wasOk = true;
				}
				assertTrue(wasOk);
				assertTrue(wasRinging);
			}
		}
		
		@Test
		public void testInviteFromToDifferentNodes() throws Exception {
			balancer.balancerContext.sipHeaderAffinityKeyExclusionPattern = Pattern.compile(".*sip.nexmo.com");
			for(int i = 0; i < shootists.length; i++)
			{
				String fromUri = "Anonymous";
//				if(i % 2 == 0) {
//					fromUri = "Restricted";
//				}
				shootists[i].sendInitial(fromUri, "sip.nexmo.com", "INVITE", null, null, null);
				Thread.sleep(6000);
				shootists[i].sendBye();
				Thread.sleep(2000);
			}
			
			assertEquals(servers[0].getTestSipListener().isInviteReceived(),servers[1].getTestSipListener().isInviteReceived());
			assertEquals(servers[0].getTestSipListener().isAckReceived(),servers[1].getTestSipListener().isAckReceived());
			assertEquals(servers[0].getTestSipListener().getByeReceived(),servers[1].getTestSipListener().getByeReceived());
			
			for(Shootist s :shootists)
			{
				boolean wasRinging = false;
				boolean wasOk = false;
				for(Response res : s.responses)
				{
					if(res.getStatusCode() != Response.RINGING)
						wasRinging = true;
					if(res.getStatusCode() != Response.OK)
						wasOk = true;
				}
				assertTrue(wasOk);
				assertTrue(wasRinging);
			}
		}
		@Test
		public void testCongestionControl() throws Exception {
//			balancer.balancerContext.sipHeaderAffinityKeyExclusionPattern = Pattern.compile(".*sip.nexmo.com");
			for(int i = 0; i < shootists.length; i++)
			{
				String fromUri = "Anonymous";
//				if(i % 2 == 0) {
//					fromUri = "Restricted";
//				}
				shootists[i].sendInitial(fromUri, "sip.nexmo.com", "INVITE", null, new String[]{"User-Agent"}, new String[]{"sipx"});
				Thread.sleep(6000);
				assertFalse(servers[0].getTestSipListener().isInviteReceived());
				assertFalse(servers[1].getTestSipListener().isInviteReceived());
				//shootists[i].sendBye();
				//Thread.sleep(2000);
			}
			
			
			
			assertEquals(servers[0].getTestSipListener().isInviteReceived(),servers[1].getTestSipListener().isInviteReceived());
			assertEquals(servers[0].getTestSipListener().isAckReceived(),servers[1].getTestSipListener().isAckReceived());
			assertEquals(servers[0].getTestSipListener().getByeReceived(),servers[1].getTestSipListener().getByeReceived());
			
			for(Shootist s :shootists)
			{
				boolean wasRinging = false;
				boolean wasOk = false;
				for(Response res : s.responses)
				{
					if(res.getStatusCode() != Response.RINGING)
						wasRinging = true;
					if(res.getStatusCode() != Response.OK)
						wasOk = true;
				}
				assertFalse(wasOk);
				assertFalse(wasRinging);
			}
		}
}

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

package org.mobicents.tools.sip.balancer.btbua;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import gov.nist.javax.sip.stack.NioMessageProcessorFactory;

import java.util.Properties;

import javax.sip.ListeningPoint;
import javax.sip.address.SipURI;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mobicents.tools.sip.balancer.BalancerRunner;
import org.mobicents.tools.sip.balancer.operation.Shootist;

public class BTBUATest
{
	
	BalancerRunner balancer;
	Shootist shootist1, shootist2;
	BackToBackUserAgent agent;

	@Before
	public void setUp() throws Exception {
		shootist1 = new Shootist(ListeningPoint.UDP,5034,5033);
		shootist2 = new Shootist(ListeningPoint.UDP,5033,5034);
		balancer = new BalancerRunner();
		agent = new BackToBackUserAgent(4060, ListeningPoint.UDP, "127.0.0.1", 2000, 5065);
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
		properties.setProperty("gov.nist.javax.sip.MESSAGE_PROCESSOR_FACTORY", NioMessageProcessorFactory.class.getName());
		properties.setProperty("host", "127.0.0.1");
		properties.setProperty("internalUdpPort", "5065");
		properties.setProperty("externalUdpPort", "5060");
		balancer.start(properties);
		agent.start();
		Thread.sleep(5000);
	}

	@After
	public void tearDown() throws Exception {
		shootist1.stop();
		shootist2.stop();
		balancer.stop();
		agent.stop();
	}

	//test back to back use agent 
	@Test
	public void testBackToBackUserAgent() throws Exception {
		boolean wasRinging = false;
		boolean wasOk = false;
		boolean wasInvite = false;
		boolean wasAck = false;
		boolean wasBye = false;
		shootist2.start();
		shootist1.start();
		shootist1.callerSendsBye = true;
		SipURI route = shootist1.addressFactory.createSipURI("lbint", "127.0.0.1:5060");
		route.setLrParam();		
		shootist1.sendInitial(Request.INVITE, shootist1.headerFactory.createRouteHeader(shootist1.addressFactory.createAddress(route)));
		Thread.sleep(9000);
		shootist1.sendBye();
		Thread.sleep(2000);
		for(Response res : shootist1.responses)
		{
			if(res.getStatusCode() == Response.RINGING)
				wasRinging = true;
			if(res.getStatusCode() == Response.OK)
				wasOk = true;
		}

		for(Request req : shootist2.requests)
		{
			if(req.getMethod().equals(Request.INVITE))
				wasInvite = true;
			if(req.getMethod().equals(Request.ACK))
				wasAck = true;
			if(req.getMethod().equals(Request.BYE))
				wasBye = true;
		}
		assertEquals(3,shootist2.requests.size());
		assertTrue(wasOk);
		assertTrue(wasRinging);
		assertTrue(wasInvite);
		assertTrue(wasAck);
		assertTrue(wasBye);
	}
}
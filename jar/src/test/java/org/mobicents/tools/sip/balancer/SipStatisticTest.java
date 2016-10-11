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

package org.mobicents.tools.sip.balancer;

import static org.junit.Assert.assertEquals;
import javax.sip.ListeningPoint;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mobicents.tools.configuration.LoadBalancerConfiguration;
import org.mobicents.tools.sip.balancer.AppServer;
import org.mobicents.tools.sip.balancer.BalancerRunner;
import org.mobicents.tools.sip.balancer.operation.Shootist;

/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */

public class SipStatisticTest{
	BalancerRunner balancer;
	int numNodes = 2;
	AppServer[] servers = new AppServer[numNodes];
	Shootist shootist;
	int activeConnections;


	@Before
	public void setUp() throws Exception {
		shootist = new Shootist(ListeningPoint.TCP,5060);
		balancer = new BalancerRunner();
		LoadBalancerConfiguration lbConfig = new LoadBalancerConfiguration();
		lbConfig.getSipConfiguration().getExternalLegConfiguration().setUdpPort(null);
		lbConfig.getSipConfiguration().getInternalLegConfiguration().setTcpPort(5065);
		balancer.start(lbConfig);
		
		for(int q=0;q<servers.length;q++) {
			servers[q] = new AppServer("node" + q,4060+q , "127.0.0.1", 2000, 5060, 5065, "0", ListeningPoint.TCP);			
			servers[q].start();		
		}
		
		Thread.sleep(2000);
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
		
		shootist.callerSendsBye = true;
		shootist.sendInitialInvite();
		Thread.sleep(10000);
		activeConnections = balancer.getNumberOfActiveSipConnections();
		Thread.sleep(2000);
		shootist.sendBye();
		Thread.sleep(2000);
		
		assertEquals(247, balancer.getNumberOfBytesTransferred());
		assertEquals(3, balancer.getNumberOfRequestsProcessed());
		assertEquals(5, balancer.getNumberOfResponsesProcessed());
		assertEquals(1 , balancer.getRequestsProcessedByMethod("INVITE"));
		assertEquals(2 , balancer.getResponsesProcessedByStatusCode("2XX"));
		assertEquals(1*2, activeConnections);		
	}

}


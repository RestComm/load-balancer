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
import static org.junit.Assert.assertNotNull;
import javax.sip.ListeningPoint;
import javax.sip.message.Response;

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

public class NodeGracefulShutdownTest{
	BalancerRunner balancer;
	int numNodes = 2;
	AppServer[] servers = new AppServer[numNodes];
	Shootist shootist1, shootist2;
	int activeConnections;


	@Before
	public void setUp() throws Exception {
		shootist1 = new Shootist(ListeningPoint.TCP,5060,5033);
		shootist2 = new Shootist(ListeningPoint.TCP,5060,5034);
		balancer = new BalancerRunner();
		LoadBalancerConfiguration lbConfig = new LoadBalancerConfiguration();
		lbConfig.getSipConfiguration().getExternalLegConfiguration().setUdpPort(5060);
		lbConfig.getSipConfiguration().getInternalLegConfiguration().setTcpPort(5065);
		lbConfig.getSipConfiguration().getAlgorithmConfiguration().setAlgorithmClass(UserBasedAlgorithm.class.getCanonicalName());
		lbConfig.getSipConfiguration().getAlgorithmConfiguration().setSipHeaderAffinityKey("To");
		balancer.start(lbConfig);
		
		for(int q=0;q<servers.length;q++) {
			servers[q] = new AppServer("node" + q,4060+q , "127.0.0.1", 2000, 5060, 5065, "0", ListeningPoint.TCP, 2222+q);			
			servers[q].start();
			Thread.sleep(1000);
		}
		
		Thread.sleep(5000);
	}

	@After
	public void tearDown() throws Exception {
		shootist1.stop();
		shootist2.stop();
		for(int q=0;q<servers.length;q++) {
			servers[q].stop();
		}
		balancer.stop();
	}

	@Test
	public void testGracefulRemovingNode() throws Exception {

		int okNumber1 = 0;
		int okNumber2 = 0;
		shootist1.sendInitialInvite();
		Thread.sleep(500);
		for(AppServer server : servers)
			if(server.sipListener.getInviteRequest()!=null)
				server.gracefulShutdown();
		Thread.sleep(5000);
		shootist1.sendBye();
		Thread.sleep(2000);
		shootist2.sendInitialInvite();
		Thread.sleep(5000);
		shootist2.sendBye();
		Thread.sleep(2000);
		for(Response res : shootist1.responses)
			if(res.getStatusCode() == Response.OK)
				okNumber1++;
		for(Response res : shootist2.responses)
			if(res.getStatusCode() == Response.OK)
				okNumber2++;
		
		for(AppServer server : servers)
			assertNotNull(server.sipListener.getInviteRequest());
		assertEquals(2, okNumber1);
		assertEquals(2, okNumber2);
	}
}



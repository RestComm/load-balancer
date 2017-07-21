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
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;

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

public class RouteRulesTest{
	BalancerRunner balancer;
	int numNodes = 1;
	AppServer[] servers = new AppServer[numNodes];
	Shootist shootist;


	@Before
	public void setUp() throws Exception {
		shootist = new Shootist(ListeningPoint.TCP,5060);
		balancer = new BalancerRunner();
		LoadBalancerConfiguration lbConfig = new LoadBalancerConfiguration();
		lbConfig.getSipConfiguration().getExternalLegConfiguration().setUdpPort(null);
		lbConfig.getSipConfiguration().getInternalLegConfiguration().setTcpPort(5065);
		ArrayList<String> ipLoadBalancerIps = new ArrayList <>();
		ipLoadBalancerIps.add("56.120.30.100");
		lbConfig.getSipConfiguration().getExternalLegConfiguration().setIpLoadBalancerAddress(ipLoadBalancerIps);
		lbConfig.getSipConfiguration().getExternalLegConfiguration().setIpLoadBalancerTcpPort(5060);
		ArrayList<String> ipLoadBalancerIpsin = new ArrayList <>();
		ipLoadBalancerIpsin.add("127.0.0.1");
		lbConfig.getSipConfiguration().getInternalLegConfiguration().setIpLoadBalancerAddress(ipLoadBalancerIpsin);
		lbConfig.getSipConfiguration().getInternalLegConfiguration().setIpLoadBalancerTcpPort(5066);
		RoutingRule rule = new RoutingRule("127.0.0.*", false);
		ArrayList<RoutingRule> rules = new ArrayList <RoutingRule>();
		rules.add(rule);
		lbConfig.getSipConfiguration().setRoutingRulesIpv4(rules);
		balancer.start(lbConfig);
		
		for(int q=0;q<servers.length;q++) 
		{
			servers[q] = new AppServer("node" + q,4060+q , "127.0.0.1", 2000, 5060, 5065, "0", ListeningPoint.TCP, 2222);			
			servers[q].start();		
		}
		
		Thread.sleep(2000);
	}

	@After
	public void tearDown() throws Exception {
		shootist.stop();
		for(int q=0;q<servers.length;q++) 
		{
			servers[q].stop();
		}
		balancer.stop();
	}

	@Test
	public void testCorrectRouting() throws Exception {
		shootist.callerSendsBye = true;
		shootist.sendInitial("KostyaNosach", "here.com", "INVITE",null, null, null);
		Thread.sleep(5000);
		shootist.sendBye();
		Thread.sleep(2000);
		assertTrue(servers[0].getTestSipListener().isInviteReceived());
		assertTrue(servers[0].getTestSipListener().isAckReceived());
		assertTrue(servers[0].getTestSipListener().getByeReceived());
		
	}

}



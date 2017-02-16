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
package org.mobicents.tools.sip.balancer.operation;

import static org.junit.Assert.assertEquals;

import java.util.Properties;

import javax.sip.ListeningPoint;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.junit.After;
import org.junit.Test;
import org.mobicents.tools.configuration.LoadBalancerConfiguration;
import org.mobicents.tools.sip.balancer.AppServer;
import org.mobicents.tools.sip.balancer.BalancerRunner;
import org.mobicents.tools.sip.balancer.UserBasedAlgorithm;
import org.mobicents.tools.sip.balancer.operation.Shootist;

import com.meterware.httpunit.GetMethodWebRequest;
import com.meterware.httpunit.WebConversation;
import com.meterware.httpunit.WebRequest;

/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */

public class RegexTest {
	
	BalancerRunner balancer;
	Shootist shootist;
	Properties properties;
	AppServer node0, node1;

	public void setUp() throws Exception
	{
		shootist = new Shootist(ListeningPoint.TCP,5060);
		balancer = new BalancerRunner();
		LoadBalancerConfiguration lbConfig = new LoadBalancerConfiguration();
		lbConfig.getSipConfiguration().getExternalLegConfiguration().setUdpPort(5060);
		lbConfig.getSipConfiguration().getInternalLegConfiguration().setTcpPort(5065);
		lbConfig.getSipConfiguration().getAlgorithmConfiguration().setAlgorithmClass(UserBasedAlgorithm.class.getCanonicalName());
		lbConfig.getSipConfiguration().getAlgorithmConfiguration().setSipHeaderAffinityKey("From");
		balancer.start(lbConfig);
		node0 = new AppServer("node0",4060 , "127.0.0.1", 2000, 5060, 5065, "0", ListeningPoint.TCP,2222);
		node1 = new AppServer("node1",4061 , "127.0.0.1", 2000, 5060, 5065, "0", ListeningPoint.TCP,2223);
		node0.start();
		Thread.sleep(1000);
		node1.start();
		Thread.sleep(1000);
	}
	
	@After
	public void tearDown() throws Exception 
	{
		shootist.stop();
		node0.stop();
		node1.stop();
		balancer.stop();
	}
	
	@Test
	public void testRegexAddAndRemove() throws Exception
	{
		int okCounter = 0;
		setUp();
		WebConversation conversation = new WebConversation();
		WebRequest request = new GetMethodWebRequest(
				"http://127.0.0.1:2006/lbnoderegex?regex=(-)&ip=127.0.0.1&port=4060");
		conversation.getResponse(request);
		Thread.sleep(2000);
		shootist.sendInitial("Kostya-test", "here.com", Request.INVITE, null, null, null);
		Thread.sleep(5000);
		shootist.sendInitial("Nosach-test", "here.com", Request.INVITE, null, null, null);
		Thread.sleep(5000);
		request = new GetMethodWebRequest(
				"http://127.0.0.1:2006/lbnoderegex?regex=(-)");
		conversation.getResponse(request);
		Thread.sleep(2000);
		shootist.sendInitial("Kostya-test", "here.com", Request.INVITE, null, null, null);
		Thread.sleep(5000);
		shootist.sendInitial("Nosach-test", "here.com", Request.INVITE, null, null, null);
		Thread.sleep(5000);
		
		for(Response res : shootist.responses)
		{
			if(res.getStatusCode() == Response.OK)
				okCounter++;
		}
		
		assertEquals(4,okCounter);
		assertEquals(3,node0.getTestSipListener().getDialogCount());
		assertEquals(1,node1.getTestSipListener().getDialogCount());
	}
}


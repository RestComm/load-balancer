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

/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */

import static org.junit.Assert.*;
import gov.nist.javax.sip.message.SIPRequest;

import javax.sip.ListeningPoint;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mobicents.tools.configuration.LoadBalancerConfiguration;
import org.mobicents.tools.sip.balancer.AppServer;
import org.mobicents.tools.sip.balancer.BalancerRunner;
import org.mobicents.tools.sip.balancer.EventListener;
import org.mobicents.tools.sip.balancer.UserBasedAlgorithm;
import org.mobicents.tools.sip.balancer.operation.Shootist;

public class RegisterWithNexmoPropertyTest {
	
	BalancerRunner balancer;
	int numNodes = 2;
	AppServer[] servers = new AppServer[numNodes];
	Shootist shootist;
	static AppServer register;
	static AppServer ack;
	static AppServer bye;
	AppServer ringingAppServer;
	AppServer okAppServer;

	@Before
	public void setUp() throws Exception {
		shootist = new Shootist(ListeningPoint.TCP,5060);
		balancer = new BalancerRunner();
		LoadBalancerConfiguration lbConfig = new LoadBalancerConfiguration();
		lbConfig.getSipConfiguration().getInternalLegConfiguration().setTcpPort(5065);
		lbConfig.getSipConfiguration().getExternalLegConfiguration().setTcpPort(5060);
		lbConfig.getSipConfiguration().getAlgorithmConfiguration().setAlgorithmClass(UserBasedAlgorithm.class.getCanonicalName());
		lbConfig.getSipConfiguration().getAlgorithmConfiguration().setSipHeaderAffinityKey("From");
		lbConfig.getSipConfiguration().setIsUseWithNexmo(true);
		lbConfig.getSipConfiguration().setSendTrying(false);
		balancer.start(lbConfig);
		
		for(int q=0;q<servers.length;q++)
		{
			servers[q] = new AppServer("node" + q,4060+q , "127.0.0.1", 2000, 5060, 5065, "0", ListeningPoint.TCP,2222+q);			
			servers[q].start();		
		}
		Thread.sleep(3000);
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
	public void testRegisterWithNexmoProperty() throws Exception {
		EventListener failureEventListener = new EventListener() {

			@Override
			public void uasAfterResponse(int statusCode, AppServer source) {
				
			}
			
			@Override
			public void uasAfterRequestReceived(String method, AppServer source) {
				if(method.equals(SIPRequest.REGISTER)) register = source;
			}

			@Override
			public void uacAfterRequestSent(String method, AppServer source) {
			}

			@Override
			public void uacAfterResponse(int statusCode, AppServer source) {
			}
		};
		for(AppServer as:servers) as.setEventListener(failureEventListener);

		shootist.sendInitial("KostyaNosach", "here.com", SIPRequest.REGISTER, null, null, null);
		Thread.sleep(3000);
		assertNotNull(register);
		assertEquals(200,shootist.responses.get(0).getStatusCode());
	}
}


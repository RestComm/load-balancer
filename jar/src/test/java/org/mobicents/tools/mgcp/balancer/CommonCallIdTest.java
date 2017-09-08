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

package org.mobicents.tools.mgcp.balancer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.net.InetAddress;

import org.apache.log4j.Logger;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.restcomm.media.client.mgcp.stack.JainMgcpStackImpl;
import org.mobicents.tools.configuration.LoadBalancerConfiguration;
import org.mobicents.tools.sip.balancer.BalancerRunner;

/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */
public class CommonCallIdTest {

	private static Logger logger = Logger.getLogger(CommonCallIdTest.class);
	
	private static BalancerRunner balancerRunner;
	private static JainMgcpStackImpl caStack1 = null;
	private static JainMgcpStackImpl caStack2 = null;
	private static JainMgcpStackImpl mgStack1 = null;
	private static JainMgcpStackImpl mgStack2 = null;
	private static InetAddress address = null;
	private static CA ca1 = null;
	private static CA ca2 = null;
	private static MGW mgw1 = null;
	private static MGW mgw2 = null;
	private static final String LOCAL_ADDRESS = "127.0.0.1";
	private static final int CA1_PORT = 2327;
	private static final int CA2_PORT = 2427;
	private static final int LB_PORT_EXTERNAL = 2527;
	private static final int LB_PORT_INTERNAL = 2627;
	private static final int SERVER1_PORT = 2727;
	private static final int SERVER2_PORT = 2827;
	
	
	@BeforeClass
	public static void initialization() throws Exception
	{
		balancerRunner = new BalancerRunner();
		address = InetAddress.getByName(LOCAL_ADDRESS);
		LoadBalancerConfiguration lbConfig = new LoadBalancerConfiguration();
		lbConfig.getMgcpConfiguration().setMgcpHost(LOCAL_ADDRESS);
		lbConfig.getMgcpConfiguration().setMgcpExternalPort(LB_PORT_EXTERNAL);
		lbConfig.getMgcpConfiguration().setMgcpInternalPort(LB_PORT_INTERNAL);
		balancerRunner.start(lbConfig);
		caStack1 = new JainMgcpStackImpl(address, CA1_PORT);
		caStack2 = new JainMgcpStackImpl(address, CA2_PORT);
		mgStack1 = new JainMgcpStackImpl(address, SERVER1_PORT);
		mgStack2 = new JainMgcpStackImpl(address, SERVER2_PORT);
		ca1 = new CA(caStack1.createProvider(), LB_PORT_EXTERNAL);
		ca2 = new CA(caStack2.createProvider(), LB_PORT_EXTERNAL);
		mgw1 = new MGW(mgStack1.createProvider(), 2222);
		mgw2 = new MGW(mgStack2.createProvider(), 2223);
		mgw1.start();
		sleep(100);
		mgw2.start();
		sleep(2000);
	}
	
	@Test
    public void testCallsFromDiffCA() 
    { 
		testConsistentlyCalls();
		testParallelCals();
		
		assertFalse(ca1.getReceivedResponses().isEmpty());
		assertFalse(ca2.getReceivedResponses().isEmpty());
		assertFalse(mgw1.getReceivedCommands().isEmpty());
		assertFalse(mgw2.getReceivedCommands().isEmpty());
		assertEquals(ca1.getCommandsCount(),ca1.getReceivedResponses().size());
		assertEquals(ca2.getCommandsCount(),ca2.getReceivedResponses().size());
		assertEquals(mgw1.getCommandsCount(),mgw1.getReceivedResponses().size());
		assertEquals(mgw2.getCommandsCount(),mgw2.getReceivedResponses().size());
		assertTrue(balancerRunner.mgcpBalancerRunner.getMgcpBalancerDispatcher().getCallMap().isEmpty());
		assertTrue(balancerRunner.mgcpBalancerRunner.getMgcpBalancerDispatcher().getConnectionMap().isEmpty());
		assertTrue(balancerRunner.mgcpBalancerRunner.getMgcpBalancerDispatcher().getReverseConnectionMap().isEmpty());
		assertTrue(balancerRunner.mgcpBalancerRunner.getMgcpBalancerDispatcher().getEndpointMap().isEmpty());
		assertTrue(balancerRunner.mgcpBalancerRunner.getMgcpBalancerDispatcher().getReverseEndpointMap().isEmpty());
		assertTrue(balancerRunner.mgcpBalancerRunner.getMgcpBalancerDispatcher().getTransactionMap().isEmpty());
		assertTrue(balancerRunner.mgcpBalancerRunner.getMgcpBalancerDispatcher().getReverseTransactionMap().isEmpty());
		assertTrue(balancerRunner.mgcpBalancerRunner.getMgcpBalancerDispatcher().getReverseRequestMap().isEmpty());
		for(MgwHost mgwHost : balancerRunner.mgcpBalancerRunner.getMgcpBalancerDispatcher().getMgwHosts().values())
			assertTrue(mgwHost.getCalls().isEmpty());
    }
	private void testConsistentlyCalls()
	{
		ca1.sendCreateConnectionWildcard();
		sleep(1000);
		ca1.sendModifyConnection();
		sleep(1000);
		ca1.sendCreateConnection();
		sleep(1000);
		ca1.sendNotificationRequest();
		sleep(1000);
		mgw2.sendNotify();
		sleep(1000);
		ca1.sendDeleteConnectionWithEndpoint();
		sleep(1000);
		ca1.sendDeleteConnectionWithEndpoint();
		sleep(1000);
		ca2.sendCreateConnectionWildcard();
		sleep(1000);
		ca2.sendModifyConnection();
		sleep(1000);
		ca2.sendCreateConnection();
		sleep(1000);
		ca2.sendNotificationRequest();
		sleep(1000);
		mgw1.sendNotify();
		sleep(1000);
		ca2.sendDeleteConnectionWithEndpoint();
		sleep(1000);
		ca2.sendDeleteConnectionWithEndpoint();
		sleep(1000);
	}
	private void testParallelCals()
	{
		ca1.sendCreateConnectionWildcard();
		ca2.sendCreateConnectionWildcard();
		sleep(1000);
		ca1.sendModifyConnection();
		ca2.sendModifyConnection();
		sleep(1000);
		ca1.sendCreateConnection();
		ca2.sendCreateConnection();
		sleep(1000);
		ca1.sendNotificationRequest();
		ca2.sendNotificationRequest();
		sleep(1000);
		mgw1.sendNotify();
		mgw2.sendNotify();
		sleep(1000);
		ca1.sendDeleteConnectionWithEndpoint();
		ca2.sendDeleteConnectionWithEndpoint();
		sleep(1000);
		ca1.sendDeleteConnectionWithEndpoint();
		ca2.sendDeleteConnectionWithEndpoint();
		sleep(1000);
	}
	
	@AfterClass
	public static void finalization()
	{
		logger.info("TEST FINALIZATION");

		mgw1.stop();
		mgw2.stop();
		balancerRunner.stop();
		if (caStack1 != null) {
			caStack1.close();
			caStack1 = null;
		}
		if (caStack2 != null) {
			caStack2.close();
			caStack2 = null;
		}
		if (mgStack1 != null) {
			mgStack1.close();
			mgStack1 = null;
		}
		if (mgStack2 != null) {
			mgStack2.close();
			mgStack2 = null;
		}
		// Wait for stack threads to release resources (e.g. port)
		sleep(1000);
	}
	
	protected static void sleep(long sleepFor) {
		try {
			Thread.sleep(sleepFor);
		} catch (InterruptedException ex) {
			// Ignore
		}
	}
}

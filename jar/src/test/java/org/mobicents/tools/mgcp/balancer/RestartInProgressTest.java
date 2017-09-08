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
import org.restcomm.media.client.mgcp.stack.JainMgcpStackProviderImpl;
import org.mobicents.tools.configuration.LoadBalancerConfiguration;
import org.mobicents.tools.sip.balancer.BalancerRunner;
/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */
public class RestartInProgressTest {


	private static Logger logger = Logger.getLogger(RestartInProgressTest.class);
	
	private static BalancerRunner balancerRunner;
	private static JainMgcpStackProviderImpl caProvider = null;
	private static JainMgcpStackProviderImpl mgProvider1 = null;
	private static JainMgcpStackImpl caStack = null;
	private static JainMgcpStackImpl mgStack1 = null;
	private static InetAddress caIPAddress = null;
	private static InetAddress mgIPAddress = null;
	private static CA ca = null;
	private static MGW server1 = null;
	private static final String LOCAL_ADDRESS = "127.0.0.1";
	private static final int CLIENT_PORT = 2427;
	private static final int LB_PORT_EXTERNAL = 2527;
	private static final int LB_PORT_INTERNAL = 2627;
	private static final int SERVER1_PORT = 2727;
	
	@BeforeClass
	public static void initialization() throws Exception
	{
		balancerRunner = new BalancerRunner();
		LoadBalancerConfiguration lbConfig = new LoadBalancerConfiguration();
		lbConfig.getMgcpConfiguration().setMgcpHost(LOCAL_ADDRESS);
		lbConfig.getMgcpConfiguration().setMgcpExternalPort(LB_PORT_EXTERNAL);
		lbConfig.getMgcpConfiguration().setMgcpInternalPort(LB_PORT_INTERNAL);
		balancerRunner.start(lbConfig);
		//client
		caIPAddress = InetAddress.getByName(LOCAL_ADDRESS);
		caStack = new JainMgcpStackImpl(caIPAddress, CLIENT_PORT);
		caProvider = (JainMgcpStackProviderImpl) caStack.createProvider();
		//server1
		mgIPAddress = InetAddress.getByName(LOCAL_ADDRESS);
		mgStack1 = new JainMgcpStackImpl(mgIPAddress, SERVER1_PORT);
		mgProvider1 = (JainMgcpStackProviderImpl) mgStack1.createProvider();
		ca = new CA(caProvider, LB_PORT_EXTERNAL);
		server1 = new MGW(mgProvider1, 2222);
		server1.start();
		sleep(2000);
	}
	
	@Test
    public void testDeleteConnectionsWithConnectionId() 
    { 
		ca.sendCreateConnectionWildcard();
		sleep(1000);
		ca.sendModifyConnection();
		sleep(1000);
		ca.sendCreateConnection();
		sleep(1000);
		ca.sendNotificationRequest();
		sleep(1000);
		server1.sendNotify();
		sleep(1000);
		server1.sendRestartInProgress();
		sleep(1000);
		assertFalse(ca.getReceivedResponses().isEmpty());
		assertEquals(ca.getCommandsCount(),ca.getReceivedResponses().size());
		assertEquals(server1.getCommandsCount(),server1.getReceivedResponses().size());
		assertEquals(1, balancerRunner.mgcpBalancerRunner.getMgcpBalancerDispatcher().getCallMap().size());
		assertEquals(1, balancerRunner.mgcpBalancerRunner.getMgcpBalancerDispatcher().getConnectionMap().size());
		assertEquals(1, balancerRunner.mgcpBalancerRunner.getMgcpBalancerDispatcher().getReverseConnectionMap().size());
		assertEquals(1, balancerRunner.mgcpBalancerRunner.getMgcpBalancerDispatcher().getEndpointMap().size());
		assertEquals(1, balancerRunner.mgcpBalancerRunner.getMgcpBalancerDispatcher().getReverseEndpointMap().size());
		assertTrue(balancerRunner.mgcpBalancerRunner.getMgcpBalancerDispatcher().getTransactionMap().isEmpty());
		assertTrue(balancerRunner.mgcpBalancerRunner.getMgcpBalancerDispatcher().getReverseTransactionMap().isEmpty());
		assertTrue(balancerRunner.mgcpBalancerRunner.getMgcpBalancerDispatcher().getReverseRequestMap().isEmpty());
    }
	
	@AfterClass
	public static void finalization()
	{
		logger.info("TEST FINALIZATION");

		server1.stop();
		balancerRunner.stop();
		
		if (caStack != null) {
			caStack.close();
			caStack = null;
		}
		if (mgStack1 != null) {
			mgStack1.close();
			mgStack1 = null;
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

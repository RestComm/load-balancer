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

/*
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
package org.mobicents.tools.sip.balancer;

import static org.junit.Assert.assertEquals;

import javax.sip.ListeningPoint;

import org.junit.Test;
import org.mobicents.ext.javax.sip.congestion.CongestionControlMessageValve;
import org.mobicents.tools.configuration.LoadBalancerConfiguration;
import org.mobicents.tools.sip.balancer.operation.Helper;

/**
 * @author <A HREF="mailto:jean.deruelle@gmail.com">Jean Deruelle</A> 
 *
 */
public class NodeRegisterTest{


	@Test
	public void testNodeTimeouts() {
		BalancerRunner balancerRunner = new BalancerRunner();
		LoadBalancerConfiguration lbConfig = new LoadBalancerConfiguration();
		balancerRunner.start(lbConfig);
		Helper.sleep(1000);
		int numNodes = 2;
		AppServer[] servers = new AppServer[numNodes];
		try {
			for(int q=0;q<servers.length;q++) {
				servers[q] = new AppServer("node" + q,15060+q , "127.0.0.1", 2000, 5060, 5065, "0", ListeningPoint.UDP,  2222+q);
				servers[q].start();
			}
			
			Helper.sleep(8000);
			String[] nodes = balancerRunner.getNodeList();
			assertEquals(numNodes, nodes.length);
			servers[0].stop();
			Helper.sleep(14000);
			nodes = balancerRunner.getNodeList();
			assertEquals(numNodes-1, nodes.length);
		}
		finally {
			for(int q=0;q<servers.length;q++) {
				servers[q].stop();
			}
			
			balancerRunner.stop();
		}

		
	}
	
	@Test
	public void testNodeTimeouts2ValvesDrop()  {
		BalancerRunner balancerRunner = new BalancerRunner();
		LoadBalancerConfiguration lbConfig = new LoadBalancerConfiguration();
		lbConfig.getSipStackConfiguration().getSipStackProperies().setProperty("gov.nist.javax.sip.SIP_MESSAGE_VALVE", 
				CongestionControlMessageValve.class.getName() + "," + SIPBalancerValveProcessor.class.getName());
		balancerRunner.start(lbConfig);
		Helper.sleep(1000);
		int numNodes = 2;
		AppServer[] servers = new AppServer[numNodes];
		try {
			for(int q=0;q<servers.length;q++) {
				servers[q] = new AppServer("node" + q,15060+q , "127.0.0.1", 2000, 5060, 5065, "0", ListeningPoint.UDP, 2222+q);
				servers[q].start();
			}
			
			Helper.sleep(8000);
			String[] nodes = balancerRunner.getNodeList();
			assertEquals(numNodes, nodes.length);
			servers[0].stop();
			Helper.sleep(14000);
			nodes = balancerRunner.getNodeList();
			assertEquals(numNodes-1, nodes.length);
		}
		finally {
			for(int q=0;q<servers.length;q++) {
				servers[q].stop();
			}
			
			balancerRunner.stop();
		}

		
	}
}

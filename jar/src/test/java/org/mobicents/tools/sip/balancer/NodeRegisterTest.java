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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.rmi.RemoteException;
import java.util.Properties;

import junit.framework.TestCase;

/**
 * @author <A HREF="mailto:jean.deruelle@gmail.com">Jean Deruelle</A> 
 *
 */
public class NodeRegisterTest extends TestCase {

	/**
	 * @param name
	 */
	public NodeRegisterTest(String name) {
		super(name);
	}

	/* (non-Javadoc)
	 * @see junit.framework.TestCase#setUp()
	 */
	protected void setUp() throws Exception {
		super.setUp();
	}

	/* (non-Javadoc)
	 * @see junit.framework.TestCase#tearDown()
	 */
	protected void tearDown() throws Exception {
		super.tearDown();
	}
	
	public void testNodeTimeouts() throws RemoteException, Exception {
		BalancerRunner balancerRunner = new BalancerRunner();
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
		properties.setProperty("gov.nist.javax.sip.THREAD_POOL_SIZE", "64");
		properties.setProperty("gov.nist.javax.sip.REENTRANT_LISTENER", "true");
		properties.setProperty("gov.nist.javax.sip.CANCEL_CLIENT_TRANSACTION_CHECKED", "false");
		
		properties.setProperty("host", "127.0.0.1");
		properties.setProperty("internalPort", "5065");
		properties.setProperty("externalPort", "5060");
		balancerRunner.start(properties);
		Thread.sleep(1000);
		int numNodes = 2;
		AppServer[] servers = new AppServer[numNodes];
		try {
			for(int q=0;q<servers.length;q++) {
				servers[q] = new AppServer("node" + q,15060+q);
				servers[q].start();
			}
			
			Thread.sleep(5000);
			String[] nodes = balancerRunner.getNodeList();
			assertEquals(numNodes, nodes.length);
			servers[0].sendHeartbeat = false;
			Thread.sleep(10000);
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

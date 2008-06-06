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
import java.rmi.RemoteException;

import junit.framework.TestCase;

/**
 * @author <A HREF="mailto:jean.deruelle@gmail.com">Jean Deruelle</A> 
 *
 */
public class SipBalancerForwarderTest extends TestCase {
	InetAddress balancerAddress = null;
	private final static int BALANCER_EXTERNAL_PORT = 5060;
	private final static int BALANCER_INTERNAL_PORT = 5065;
	/**
	 * @param name
	 */
	public SipBalancerForwarderTest(String name) {
		super(name);
	}

	/* (non-Javadoc)
	 * @see junit.framework.TestCase#setUp()
	 */
	protected void setUp() throws Exception {
		super.setUp();
		balancerAddress=InetAddress.getByAddress(new byte[]{127,0,0,1});
	}

	/* (non-Javadoc)
	 * @see junit.framework.TestCase#tearDown()
	 */
	protected void tearDown() throws Exception {
		super.tearDown();
	}
	
	public void testStartStop2x() throws RemoteException {
		NodeRegisterImpl reg=new NodeRegisterImpl(balancerAddress);
		SIPBalancerForwarder fwd=new SIPBalancerForwarder(balancerAddress.getHostAddress(),BALANCER_INTERNAL_PORT,BALANCER_EXTERNAL_PORT,reg);
		fwd.start();
		fwd.stop();
		fwd.start();
		fwd.stop();
	}

}

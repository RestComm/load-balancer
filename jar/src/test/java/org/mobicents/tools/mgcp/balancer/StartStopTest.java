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

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mobicents.tools.configuration.LoadBalancerConfiguration;
import org.mobicents.tools.sip.balancer.BalancerRunner;


/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */

public class StartStopTest
{

	private static BalancerRunner balancerRunner;
	
	@BeforeClass
	public static void initialization() 
	{

		balancerRunner = new BalancerRunner();
		LoadBalancerConfiguration lbConfig = new LoadBalancerConfiguration();
		lbConfig.getMgcpConfiguration().setMgcpHost("127.0.0.1");
		lbConfig.getMgcpConfiguration().setMgcpExternalPort(2527);
		lbConfig.getMgcpConfiguration().setMgcpInternalPort(2627);
		balancerRunner.start(lbConfig);
		sleep(2000);
	}

	//tests mgcp balancer
	//@Test
    public void testStartStop() 
    {  
		sleep(2000);
    }
	
	private static void sleep(int i) {
		try {
			Thread.sleep(i);
		} catch (InterruptedException e) {
			e.getMessage();
		}
		
	}

	@AfterClass
	public static void finalization()
	{
		balancerRunner.stop();
	}
}



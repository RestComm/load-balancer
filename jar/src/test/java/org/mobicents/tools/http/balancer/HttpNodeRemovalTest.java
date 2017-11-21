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

package org.mobicents.tools.http.balancer;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.Semaphore;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mobicents.tools.configuration.LoadBalancerConfiguration;
import org.mobicents.tools.sip.balancer.BalancerRunner;
import org.mobicents.tools.sip.balancer.operation.Helper;
import org.mobicents.tools.smpp.balancer.ClientListener;

import com.meterware.httpunit.GetMethodWebRequest;
import com.meterware.httpunit.WebConversation;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;


/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */

public class HttpNodeRemovalTest
{
	private static BalancerRunner balancerRunner;
	private static int numberNodes = 3;
	private static int numberUsers = 6;
	private static HttpServer [] serverArray;
	private static HttpUser [] userArray;
	
	@BeforeClass
	public static void initialization() 
	{
		serverArray = new HttpServer[numberNodes];
		balancerRunner = new BalancerRunner();
		LoadBalancerConfiguration lbConfig = new LoadBalancerConfiguration();
		lbConfig.getSipConfiguration().getInternalLegConfiguration().setTcpPort(5065);
		lbConfig.getSipConfiguration().getExternalLegConfiguration().setTcpPort(5060);
		balancerRunner.start(lbConfig);
		for(int i = 0; i < numberNodes; i++)
		{
			serverArray[i] = new HttpServer(7080+i, 4444+i, 2222+i);
			serverArray[i].start();	
			Helper.sleep(1000);
		}
		Helper.sleep(5000);
	}

	//tests http balancer
	@Test
    public void testHttpBalancer() 
    {  
		userArray = new HttpUser[numberUsers];
		Locker locker = new Locker(numberUsers);

		for(int i = 0; i < numberUsers;i++)
		{
			if(i==2)
			{
				try {
					Thread.sleep(5000);
				} catch (InterruptedException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
				serverArray[0].stop();
				serverArray[2].stop();
				try {
					Thread.sleep(10000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			userArray[i] = new HttpUser(locker);
			userArray[i].start();

		}
		
		locker.waitForClients();

		try 
		{
			Thread.sleep(1000);
		} 
		catch (InterruptedException e) 
		{
			e.printStackTrace();
		}
		
		for(int i = 0; i < numberUsers;i++)
			assertEquals(200, userArray[i].codeResponse);
    }
	
	@AfterClass
	public static void finalization()
	{
		for(int i = 0; i < serverArray.length; i++)
			serverArray[i].stop();
		
		balancerRunner.stop();
	}
	private class HttpUser extends Thread
	{
		int codeResponse;
		ClientListener listener;
		public HttpUser(ClientListener listener)
		{
		 this.listener = listener;
		}

		public void run()
		{
			try 
			{ 
				WebConversation conversation = new WebConversation();
				WebRequest request = new GetMethodWebRequest(new String("http://127.0.0.1:2080/app?fName=Konstantin&lName=Nosach"));
				WebResponse response = conversation.getResponse(request);
				codeResponse = response.getResponseCode();
				listener.clientCompleted();
			} 
			catch (Exception e) 
			{
				listener.clientCompleted();
				e.printStackTrace();
			}
		}
	}
	private class Locker implements ClientListener{
    	private Semaphore clientsSemaphore;
    	private Locker(int clients)
    	{
    		clientsSemaphore=new Semaphore(1-clients);
    	}
    	
		@Override
		public void clientCompleted() 
		{
			clientsSemaphore.release();
		}
    	public void waitForClients()
    	{
    		try
    		{
    			clientsSemaphore.acquire();
    		}
    		catch(InterruptedException ex)
    		{
    			
    		}
    	}
    }
	
}

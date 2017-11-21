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
import org.mobicents.tools.smpp.balancer.ClientListener;

import com.meterware.httpunit.PostMethodWebRequest;
import com.meterware.httpunit.WebConversation;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;


/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */

public class HttpBadResponseNodeRemovalTest
{
	private static BalancerRunner balancerRunner;
	private static int numberNodes = 2;
	private static int numberUsers = 4;
	private static HttpServer [] serverArray;
	private static HttpUser [] userArray;
	
	@BeforeClass
	public static void initialization() 
	{
		serverArray = new HttpServer[numberNodes];
		for(int i = 0; i < numberNodes; i++)
		{
			String id = null;
			if(i==0)
				id = "ID1f2a2222772f4195948d040a2ccc648c";
			else
				id = "ID1f2a2222772f4195948d040a2ccc648"+i;
			serverArray[i] = new HttpServer(7080+i, 4444+i, id, 2222+i);
			if(i==0)
				serverArray[i].setBadSever(true);
			serverArray[i].start();	
		}
		balancerRunner = new BalancerRunner();
		LoadBalancerConfiguration lbConfig = new LoadBalancerConfiguration();
		lbConfig.getSipConfiguration().getInternalLegConfiguration().setTcpPort(5065);
		lbConfig.getSipConfiguration().getExternalLegConfiguration().setTcpPort(5060);
		lbConfig.getHttpConfiguration().setRequestCheckPattern("(/Accounts/)");
		balancerRunner.start(lbConfig);
		try 
		{
			Thread.sleep(1000);
		} 
		catch (InterruptedException e) 
		{
			e.printStackTrace();
		}
	}

	//tests http balancer
	@Test
    public void testHttpBalancer() 
    {  
		userArray = new HttpUser[numberUsers];
		Locker locker = new Locker(numberUsers);
		
		for(int i = 0; i < numberUsers;i++)
		{
			userArray[i] = new HttpUser(i,locker);
			userArray[i].start();
			try {
				Thread.sleep(3000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
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
		
		assertEquals(1 ,serverArray[0].getRequstCount().get());
		assertEquals(3 ,serverArray[1].getRequstCount().get());
		assertEquals(0, userArray[0].codeResponse);
		assertEquals(200, userArray[1].codeResponse);
		assertEquals(200, userArray[2].codeResponse);
		assertEquals(200, userArray[3].codeResponse);
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
		int accountSid;
		public HttpUser(int accountSid, ClientListener listener)
		{
		 this.listener = listener;
		 this.accountSid = accountSid;
		}

		public void run()
		{
			try 
			{ 
				WebConversation conversation = new WebConversation();
				WebRequest request = new PostMethodWebRequest(
						"http://user:password@127.0.0.1:2080/restcomm/2012-04-24/Accounts/"+accountSid+"/Calls.json/ID1f2a2222772f4195948d040a2ccc648c-CA00af667a6a2cbfda0c07d923e78194cd");
				WebResponse response = conversation.getResponse(request);
				codeResponse = response.getResponseCode();
			} 
			catch (Exception e) 
			{
				
			}
			finally
			{
				listener.clientCompleted();
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


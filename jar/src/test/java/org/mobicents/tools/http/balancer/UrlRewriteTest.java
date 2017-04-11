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
import org.mobicents.tools.sip.balancer.BalancerRunner;
import org.mobicents.tools.smpp.balancer.ClientListener;

import com.meterware.httpunit.PostMethodWebRequest;
import com.meterware.httpunit.WebConversation;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;


/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */

public class UrlRewriteTest
{
	private static BalancerRunner balancerRunner;
	private static int numberNodes = 1;
	private static int numberUsers = 2;
	private static HttpServer [] serverArray;
	private static HttpUser [] userArray;
	
	@BeforeClass
	public static void initialization() 
	{
		serverArray = new HttpServer[numberNodes];
		for(int i = 0; i < numberNodes; i++)
		{
			serverArray[i] = new HttpServer(8080+i, 4444+i,"ID1f2a2222772f4195948d040a2ccc648c");
			serverArray[i].start();	
		}
		balancerRunner = new BalancerRunner();
		String pathToConfig = UrlRewriteTest.class.getClassLoader().getResource("lb-configuration.xml").getFile();
		balancerRunner.start(pathToConfig);
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
			userArray[i] = new HttpUser(locker,i);
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
		
		for(int i = 0; i < numberNodes; i++)
			assertEquals(numberUsers/numberNodes,serverArray[i].getRequstCount().get());
		
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
		int userId;
		public HttpUser(ClientListener listener,int userId)
		{
		 this.listener = listener;
		 this.userId = userId;
		}

		public void run()
		{
			try 
			{ 
				WebConversation conversation = new WebConversation();
				WebRequest request = null;
				if(userId==0)
					request = new PostMethodWebRequest(
							"http://user:password@127.0.0.1:2080/someCompany/2012-04-24/Accounts/"+userId+"/Calls/ID1f2a2222772f4195948d040a2ccc648c-CA00af667a6a2cbfda0c07d923e78194cd");
				else
					request = new PostMethodWebRequest(
							"http://user:password@127.0.0.1:2080/mobius/2012-04-24/Accounts/"+userId+"/Calls/ID1f2a2222772f4195948d040a2ccc648c-CA00af667a6a2cbfda0c07d923e78194cd");
				request.setHeaderField("Url", "http://192.168.1.151:8080/restcomm/demos/conference.xml");
				request.setHeaderField("MoveConnectedCallLeg", "true");
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


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
import gov.nist.javax.sip.stack.NioMessageProcessorFactory;

import java.util.Properties;
import java.util.concurrent.Semaphore;

import org.jboss.netty.handler.codec.http.HttpMethod;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mobicents.tools.sip.balancer.BalancerRunner;
import org.mobicents.tools.smpp.balancer.ClientListener;

import com.meterware.httpunit.PostMethodWebRequest;
import com.meterware.httpunit.WebConversation;
import com.meterware.httpunit.WebRequest;


/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */

public class HttpStatisticTest
{

	private static BalancerRunner balancerRunner;
	private static int numberNodes = 3;
	private static int numberUsers = 6;
	private static HttpServer [] serverArray;
	private static HttpUser [] userArray;
	private static long activConnections;
	
	@BeforeClass
	public static void initialization() 
	{
		serverArray = new HttpServer[numberNodes];
		for(int i = 0; i < numberNodes; i++)
		{
			serverArray[i] = new HttpServer(8080+i, 4444+i);
			serverArray[i].start();	
		}
		
		balancerRunner = new BalancerRunner();
		Properties properties = new Properties();
		properties.setProperty("javax.sip.STACK_NAME", "SipBalancerForwarder");
		properties.setProperty("javax.sip.AUTOMATIC_DIALOG_SUPPORT", "off");
		// You need 16 for logging traces. 32 for debug + traces.
		// Your code will limp at 32 but it is best for debugging.
		properties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "32");
		properties.setProperty("gov.nist.javax.sip.DEBUG_LOG", "logs/sipbalancerforwarderdebug.txt");
		properties.setProperty("gov.nist.javax.sip.SERVER_LOG",	"logs/sipbalancerforwarder.xml");
		properties.setProperty("gov.nist.javax.sip.THREAD_POOL_SIZE", "2");
		properties.setProperty("gov.nist.javax.sip.REENTRANT_LISTENER", "true");
		properties.setProperty("gov.nist.javax.sip.CANCEL_CLIENT_TRANSACTION_CHECKED", "false");
		properties.setProperty("gov.nist.javax.sip.MESSAGE_PROCESSOR_FACTORY", NioMessageProcessorFactory.class.getName());
		properties.setProperty("host", "127.0.0.1");
		properties.setProperty("internalUdpPort", "5065");
		properties.setProperty("externalUdpPort", "5060");
		properties.setProperty("httpPort", "2080");
		properties.setProperty("maxContentLength", "1048576");
		properties.setProperty("terminateTLSTraffic", "true");
		balancerRunner.start(properties);
		try 
		{
			Thread.sleep(1000);
		} 
		catch (InterruptedException e) 
		{
			e.printStackTrace();
		}
	}

	//tests http balancer statistic
	@Test
    public void testHttpBalancerStatistic() 
    {  
		userArray = new HttpUser[numberUsers];
		Locker locker = new Locker(numberUsers);

		for(int i = 0; i < numberUsers;i++)
		{
			userArray[i] = new HttpUser(i, locker);
			userArray[i].start();
		}
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {

			e.printStackTrace();
		}
		activConnections = balancerRunner.getNumberOfActiveHttpConnections();
		
		locker.waitForClients();

		assertEquals(72, balancerRunner.getNumberOfHttpBytesToServer());
		assertEquals(144, balancerRunner.getNumberOfHttpBytesToClient());
		assertEquals(numberUsers,balancerRunner.getNumberOfHttpRequests());
		assertEquals(numberUsers,balancerRunner.getHttpRequestsProcessedByMethod(HttpMethod.POST.getName()));
		assertEquals(numberUsers,balancerRunner.getHttpResponseProcessedByCode("2XX"));
		assertEquals(numberUsers*2, activConnections);
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
		int jsessionid;
		ClientListener listener;
		public HttpUser(int jsessionid, ClientListener listener)
		{
		 this.jsessionid = jsessionid;	
		 this.listener = listener;
		}

		public void run()
		{
			try 
			{ 
				WebConversation conversation = new WebConversation();
				WebRequest request = new PostMethodWebRequest(new String("http://127.0.0.1:2080/app?fName=Konstantin&lName=Nosach"));
				request.setParameter("jsessionid", ""+ jsessionid);
				conversation.getResponse(request);
				listener.clientCompleted();
			} 
			catch (Exception e) 
			{
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


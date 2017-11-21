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

public class HttpsBalancerWithHttpServerTest
{

	private static BalancerRunner balancerRunner;
	private static int numberNodes = 3;
	private static int numberUsers = 6;
	private static HttpServer [] serverArray;
	private static HttpUser [] userArray;
	@BeforeClass
	public static void initialization() 
	{
		balancerRunner = new BalancerRunner();
		LoadBalancerConfiguration lbConfig = new LoadBalancerConfiguration();
		lbConfig.getSipConfiguration().getInternalLegConfiguration().setUdpPort(5065);
		lbConfig.getHttpConfiguration().setHttpsPort(2085);
		lbConfig.getSslConfiguration().setKeyStore(HttpsBalancerWithHttpsServerTest.class.getClassLoader().getResource("keystore").getFile());
		lbConfig.getSslConfiguration().setKeyStorePassword("123456");
		lbConfig.getSslConfiguration().setTrustStore(HttpsBalancerWithHttpsServerTest.class.getClassLoader().getResource("keystore").getFile());
		lbConfig.getSslConfiguration().setTrustStorePassword("123456");
		lbConfig.getSslConfiguration().setTerminateTLSTraffic(true);
		lbConfig.getSslConfiguration().setTlsClientProtocols("TLSv1,TLSv1.1,TLSv1.2");
		lbConfig.getSslConfiguration().setEnabledCipherSuites("TLS_ECDH_RSA_WITH_3DES_EDE_CBC_SHA,TLS_DHE_RSA_WITH_AES_128_CBC_SHA");
		balancerRunner.start(lbConfig);
		serverArray = new HttpServer[numberNodes];
		for(int i = 0; i < serverArray.length; i++)
		{
			serverArray[i] = new HttpServer(7080+i, 4444+i, 2222+i);
			serverArray[i].start();	
			Helper.sleep(1000);
		}
		Helper.sleep(5000);
	}

	//tests https balancer and http server
	@Test
    public void testHttpBalancer() 
    {  

		Locker locker = new Locker(numberUsers);
		try 
		{
			DisableSSLCertificateCheckUtil.disableChecks();
		} catch (Exception e) 
		{
			e.printStackTrace();
		}
		userArray = new HttpUser[numberUsers];

		for(int i = 0; i < numberUsers;i++)
		{
			userArray[i] = new HttpUser(i, locker);
			userArray[i].start();
		}

		locker.waitForClients();
		
		for(int i = 0; i < numberNodes; i++)
			assertEquals(numberUsers/numberNodes,serverArray[i].getRequstCount().get());
		
		for(int i = 0; i < numberUsers;i++)
			assertEquals(200, userArray[i].codeResponse);
    }
	
	@AfterClass
	public static void finalization()
	{
		for(int i = 0; i < numberNodes; i++)
		{
			serverArray[i].stop();
		}
		balancerRunner.stop();
	}
	private class HttpUser extends Thread
	{
		int codeResponse;
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
				WebRequest request = new GetMethodWebRequest(new String("https://127.0.0.1:2085/app?fName=Konstantin&lName=Nosach"));
				request.setParameter("jsessionid", ""+jsessionid);
				WebResponse response = conversation.getResponse(request);
				codeResponse = response.getResponseCode();
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

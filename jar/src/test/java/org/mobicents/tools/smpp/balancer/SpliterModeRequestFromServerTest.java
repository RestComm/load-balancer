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

package org.mobicents.tools.smpp.balancer;

import static org.junit.Assert.*;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mobicents.tools.sip.balancer.BalancerRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cloudhopper.commons.charset.CharsetUtil;
import com.cloudhopper.smpp.SmppSession;
import com.cloudhopper.smpp.impl.DefaultSmppClient;
import com.cloudhopper.smpp.pdu.BaseSm;
import com.cloudhopper.smpp.pdu.DataSm;
import com.cloudhopper.smpp.type.SmppChannelException;

/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */

public class SpliterModeRequestFromServerTest{
	
	
	private static final Logger logger = LoggerFactory.getLogger(SpliterModeRequestFromServerTest.class);
	
	private static ThreadPoolExecutor executor = (ThreadPoolExecutor)Executors.newFixedThreadPool(16);

    private static ScheduledThreadPoolExecutor monitorExecutor = (ScheduledThreadPoolExecutor)Executors.newScheduledThreadPool(1, new ThreadFactory() {
         private AtomicInteger sequence = new AtomicInteger(0);
         public Thread newThread(Runnable r) {
             Thread t = new Thread(r);
             t.setName("MonitorPool-" + sequence.getAndIncrement());
             return t;
         }
     }); 
    
    private static int serverNumbers = 1;
    private static AdvancedSmppServer [] serverArray;
    private static DefaultSmppServerHandler [] serverHandlerArray;
    private static DefaultSmppClientHandler [] clientHandlerArray;
    private static BalancerRunner balancer;
	
	@BeforeClass
	public static void initialization() {
		
		boolean enableSslLbPort = false;
		boolean terminateTLSTraffic = true;
		//start lb
		balancer = new BalancerRunner();
        balancer.start(ConfigInit.getLbSpliterProperties(enableSslLbPort,terminateTLSTraffic,true));
		//start servers
        serverArray = new AdvancedSmppServer[serverNumbers];
        serverHandlerArray = new DefaultSmppServerHandler [serverNumbers];
		for (int i = 0; i < serverNumbers; i++) {
			serverHandlerArray[i] = new DefaultSmppServerHandler();
			serverArray[i] = new AdvancedSmppServer(ConfigInit.getSmppServerConfiguration(i,false), serverHandlerArray[i], executor,monitorExecutor);
			logger.info("Starting SMPP server...");
			try {
				serverArray[i].start();
			} catch (SmppChannelException e) {
				logger.info("SMPP server does not started");
				e.printStackTrace();
			}

			logger.info("SMPP server started");
		}
        try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	//tests statistic of requests from server
	@Test
    public void testStatisticVariable() 
    {   
		int clientNumbers = 1;
		clientHandlerArray = new DefaultSmppClientHandler[clientNumbers];
		Locker locker=new Locker(clientNumbers);

		for(int i = 0; i < clientNumbers; i++)
			new Load(i, locker).start();

		locker.waitForClients();
		assertEquals(clientNumbers, balancer.smppBalancerRunner.getNumberOfSmppRequestsToClient());
		
		for(DefaultSmppClientHandler handler : clientHandlerArray)
			assertEquals(1, handler.getRequestFromServerNumber().get());
		
		for(DefaultSmppServerHandler handler : serverHandlerArray)
			assertEquals(clientNumbers, handler.getResponsesFromClient().get());
		
    }
	
	@After
	public void resetCounters()
	{
		  for(int i = 0; i < serverNumbers; i++)
		    {
		    	serverArray[i].resetCounters();
		    	serverHandlerArray[i].resetCounters();
		    }
	}

	@AfterClass
	public static void finalization()
	{

		for(int i = 0; i < serverNumbers; i++)
		{
			logger.info("Stopping SMPP server "+ i +" ...");
			serverArray[i].destroy();
			logger.info("SMPP server "+ i +"stopped");
		}
		executor.shutdownNow();
        monitorExecutor.shutdownNow();
        balancer.stop();
        logger.info("Done. Exiting");

	}
	
	private class Load extends Thread{
		private int i;
		private ClientListener listener;
		Load (int i, ClientListener listener)
		{
			this.i = i;
			this.listener = listener;

		}
		
		public void run()
		{
			DefaultSmppClient client = new DefaultSmppClient();
			SmppSession session = null; 
			try
			{
			 clientHandlerArray[i] = new  DefaultSmppClientHandler();
			 session = client.bind(ConfigInit.getSmppSessionConfiguration(i,false), clientHandlerArray[i]);
			 @SuppressWarnings("rawtypes")
			 BaseSm packet = new DataSm();
			 String text160 = "Hello world!";
		     byte[] textBytes = CharsetUtil.encode(text160, CharsetUtil.CHARSET_GSM);
			 packet.setShortMessage(textBytes);
			 packet.setSequenceNumber(i+2);
			 serverArray[0].sendData(packet);
			 sleep(1000);
		     session.unbind(5000);
		     sleep(100);
		        }catch(Exception e){
		        	logger.error("", e);
		        }
			if (session != null) 
	        {
	            logger.info("Cleaning up session...");
	            session.destroy();
	        }
			
	        logger.info("Shutting down client bootstrap and executors...");
	        client.destroy();
	        listener.clientCompleted();
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


package org.mobicents.tools.smpp.balancer;

import static org.junit.Assert.assertEquals;
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

import com.cloudhopper.smpp.SmppConstants;
import com.cloudhopper.smpp.SmppSession;
import com.cloudhopper.smpp.impl.DefaultSmppClient;
import com.cloudhopper.smpp.impl.DefaultSmppServer;
import com.cloudhopper.smpp.type.SmppChannelException;

public class SpliterModeStatisticTest {

private static final Logger logger = LoggerFactory.getLogger(SpliterModeTest.class);
	
	private static ThreadPoolExecutor executor = (ThreadPoolExecutor)Executors.newCachedThreadPool();

    private static ScheduledThreadPoolExecutor monitorExecutor = (ScheduledThreadPoolExecutor)Executors.newScheduledThreadPool(1, new ThreadFactory() {
         private AtomicInteger sequence = new AtomicInteger(0);
         public Thread newThread(Runnable r) {
             Thread t = new Thread(r);
             t.setName("MonitorPool-" + sequence.getAndIncrement());
             return t;
         }
     }); 
    private static BalancerRunner balancer;
    private static int serverNumbers = 3;
    private static DefaultSmppServer [] serverArray;
    private static DefaultSmppServerHandler [] serverHandlerArray;
    private static DefaultSmppClientHandler [] clientHandlerArray;
    private static long activeConnections;

	
	@BeforeClass
	public static void initialization() {
		boolean enableSslLbPort = false;
		boolean terminateTLSTraffic = true;
		//start lb
		balancer = new BalancerRunner();
        balancer.start(ConfigInit.getLbSpliterProperties(enableSslLbPort,terminateTLSTraffic));
		//start servers
        serverArray = new DefaultSmppServer[serverNumbers];
        serverHandlerArray = new DefaultSmppServerHandler [serverNumbers];
		for (int i = 0; i < serverNumbers; i++) {
			serverHandlerArray[i] = new DefaultSmppServerHandler();
			serverArray[i] = new DefaultSmppServer(ConfigInit.getSmppServerConfiguration(i,false), serverHandlerArray[i], executor,monitorExecutor);
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
	//tests statistic of SMPP load balancer
		@Test
	    public void testStatisticVariable() 
	    {   
			int clientNumbers = 3;
			clientHandlerArray = new DefaultSmppClientHandler[clientNumbers];
			int smsNumber = 1;
			Locker locker=new Locker(clientNumbers);

			for(int i = 0; i < clientNumbers; i++)
				new Load(i, smsNumber, locker).start();
			
			try {
				Thread.sleep(2000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			activeConnections = balancer.getNumberOfActiveSmppConnections();
			locker.waitForClients();
			assertEquals(348, balancer.getNumberOfSmppBytesToServer());
			assertEquals(198, balancer.getNumberOfSmppBytesToClient());
			assertEquals(clientNumbers*3, balancer.getNumberOfSmppRequestsToServer());
			assertEquals(clientNumbers, balancer.getSmppRequestsProcessedById(SmppConstants.CMD_ID_SUBMIT_SM));
			assertEquals(clientNumbers, balancer.getSmppResponsesProcessedById(SmppConstants.CMD_ID_SUBMIT_SM_RESP));
			assertEquals(clientNumbers*2, activeConnections);
	    }
	private class Load extends Thread{
		private int i;
		private ClientListener listener;
		private int smsNumber;
		Load (int i, int smsNumber, ClientListener listener)
		{
			this.i =i;
			this.listener = listener;
			this.smsNumber = smsNumber;
		}
		
		public void run()
		{
			DefaultSmppClient client = new DefaultSmppClient();
			SmppSession session = null; 
			try
			{
			 clientHandlerArray[i] = new  DefaultSmppClientHandler();
			 session = client.bind(ConfigInit.getSmppSessionConfiguration(i,false), clientHandlerArray[i]);
			 for(int j = 0; j < smsNumber; j++)
			 {
				 session.submit(ConfigInit.getSubmitSm(), 12000);
			 }
			 sleep(3000);
		     session.unbind(5000);
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
		
		public void sleep(int time)
		{
			try
			{
				Thread.sleep(time);
			}
			catch(InterruptedException ex)
			{
				
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

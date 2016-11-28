package org.mobicents.tools.smpp.balancer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mobicents.tools.sip.balancer.BalancerRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cloudhopper.smpp.SmppSession;
import com.cloudhopper.smpp.impl.DefaultSmppClient;
import com.cloudhopper.smpp.impl.DefaultSmppServer;
import com.cloudhopper.smpp.type.SmppChannelException;

public class SpliterModeRebindTest {
	
	private static final Logger logger = LoggerFactory.getLogger(SpliterModeRebindTest.class);
	
	private static ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newCachedThreadPool();

	private static ScheduledThreadPoolExecutor monitorExecutor = (ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(1, new ThreadFactory() {
				private AtomicInteger sequence = new AtomicInteger(0);
				public Thread newThread(Runnable r) {
					Thread t = new Thread(r);
					t.setName("MonitorPool-" + sequence.getAndIncrement());
					return t;
				}
			});

	private static BalancerRunner balancer;
	private static int clientNumbers = 1;
	private static int serverNumbers = 3;
	private static DefaultSmppServer[] serverArray;
	private static DefaultSmppServerHandler [] serverHandlerArray = new DefaultSmppServerHandler[serverNumbers];
	

	@BeforeClass
	public static void initialization() {

		boolean enableSslLbPort = false;
		boolean terminateTLSTraffic = true;
		//start lb
		balancer = new BalancerRunner();
        balancer.start(ConfigInit.getLbSpliterProperties(enableSslLbPort,terminateTLSTraffic));
        
		// start servers
		serverArray = new DefaultSmppServer[serverNumbers];
		for (int i = 0; i < serverNumbers; i++) {
			serverHandlerArray[i] = new DefaultSmppServerHandler();
			serverArray[i] = new DefaultSmppServer(ConfigInit.getSmppServerConfiguration(i,false), serverHandlerArray[i], executor, monitorExecutor);
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
	//tests situation with dropped connection to server(rebind check)
	@Test
	public void testDisconnectServers() {
		Locker locker = new Locker(clientNumbers);
		// start client
		new Load(locker, 1).start();
		locker.waitForClients();
		
		boolean isCorrectEnqLinkRequest = false;
	    for(DefaultSmppServerHandler handler : serverHandlerArray)
	    	if(handler.getSmsNumber()==1)
	    		isCorrectEnqLinkRequest = true;
		assertTrue(isCorrectEnqLinkRequest);
	}
	
	@Test
	public void testRebind() {
		Locker locker = new Locker(clientNumbers);
		// start client
		new Load(locker, 2).start();
		locker.waitForClients();
		boolean isCorrectEnqLinkRequest = false;
	    for(DefaultSmppServerHandler handler : serverHandlerArray)
	    	if(handler.getSmsNumber()==6)
	    		isCorrectEnqLinkRequest = true;
		assertTrue(isCorrectEnqLinkRequest);
	}

	@AfterClass
	public static void finalization() {

		for (int i = 0; i < serverNumbers; i++) {
			logger.info("Stopping SMPP server " + i + " ...");
			serverArray[i].destroy();
			logger.info("SMPP server " + i + "stopped");
		}
		executor.shutdownNow();
		monitorExecutor.shutdownNow();
		balancer.stop();
		logger.info("Done. Exiting");

	}

	private class Load extends Thread {
		private ClientListener listener;
		private int testNumber; 

		Load(ClientListener listener, int testNumber) {
			this.listener = listener;
			this.testNumber = testNumber;
		}

		public void run() {
			DefaultSmppClient client = new DefaultSmppClient();
			SmppSession session = null;
			try {

				if(testNumber == 1)
				{
					session = client.bind(ConfigInit.getSmppSessionConfiguration(1,false),new DefaultSmppClientHandler());
					logger.info("stopping server 1");
					serverArray[1].stop();
					logger.info("stopping server 2");
					serverArray[2].stop();
					sleep(5000);
					
					session.submit(ConfigInit.getSubmitSm(), 12000);
					sleep(1000);
					session.unbind(5000);
					serverArray[1].start();
					serverArray[2].start();
				}
				if(testNumber == 2)
				{
					session = client.bind(ConfigInit.getSmppSessionConfiguration(1,false),new DefaultSmppClientHandler());
					serverArray[2].stop();
					serverArray[2].start();
					sleep(2000);
					 for(int j = 0; j < 6; j++)
					 {
						 session.submit(ConfigInit.getSubmitSm(), 12000); 
					 }
					 sleep(1000);
				     session.unbind(5000);
				}

			} catch (Exception e) {
				logger.error("", e);
			}
			if (session != null) {
				logger.info("Cleaning up session...");
				session.destroy();
			}
			logger.info("Shutting down client bootstrap and executors...");
			client.destroy();
			listener.clientCompleted();
		}
	}

	private class Locker implements ClientListener {
		private Semaphore clientsSemaphore;

		private Locker(int clients) {
			clientsSemaphore = new Semaphore(1 - clients);
		}

		@Override
		public void clientCompleted() {
			clientsSemaphore.release();
		}

		public void waitForClients() {
			try {
				clientsSemaphore.acquire();
			} catch (InterruptedException ex) {

			}
		}
	}
}

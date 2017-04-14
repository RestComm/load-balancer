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

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mobicents.tools.configuration.LoadBalancerConfiguration;
import org.mobicents.tools.sip.balancer.BalancerRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cloudhopper.smpp.SmppSession;
import com.cloudhopper.smpp.SmppSessionConfiguration;
import com.cloudhopper.smpp.impl.DefaultSmppClient;
import com.cloudhopper.smpp.impl.DefaultSmppServer;
import com.cloudhopper.smpp.type.SmppChannelException;

/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */

public class ErroreResponseFromServerTest {

	private static final Logger logger = LoggerFactory.getLogger(ErroreResponseFromServerTest.class);

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
	private static DefaultSmppServer server;
	private static DefaultSmppServerHandler serverHandler = new DefaultSmppServerHandler();
	@BeforeClass
	public static void initialization() {
		//start lb
		balancer = new BalancerRunner();
		LoadBalancerConfiguration lbConfig = ConfigInit.getLbPropertiesWithOneServer();
		lbConfig.getSmppConfiguration().setReconnectPeriod(2000);
        balancer.start(lbConfig);
        
		// start servers
		serverHandler = new DefaultSmppServerHandler();
		server = new DefaultSmppServer(ConfigInit.getSmppServerConfiguration(0,false), serverHandler, executor, monitorExecutor);
		logger.info("Starting SMPP server...");
		try {
			server.start();
		} catch (SmppChannelException e) {
			logger.info("SMPP server does not started");
			e.printStackTrace();
		}
		logger.info("SMPP server started");
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	@Test
	public void testBindWithErrorFromServer() {
		Locker locker = new Locker(1);
		// start client
		new Load(locker).start();
		locker.waitForClients();
		assertEquals(1, server.getBindRequested());
	}

	@AfterClass
	public static void finalization() {
		logger.info("Stopping LB!");
		balancer.stop();
		logger.info("Stopping SMPP server!");
		server.destroy();
		logger.info("SMPP server stopped!");
		executor.shutdownNow();
		monitorExecutor.shutdownNow();
		balancer.stop();
		logger.info("Done. Exiting");

	}

	private class Load extends Thread {
		private ClientListener listener;
		Load(ClientListener listener) {
			this.listener = listener;
		}

		public void run() {
			DefaultSmppClient client = new DefaultSmppClient();
			SmppSession session = null;
			try 
			{
				SmppSessionConfiguration sessionConfig = ConfigInit.getSmppSessionConfiguration(1,false);
				sessionConfig.setPassword("PasswordForSomeErrors");
				session = client.bind(sessionConfig, new DefaultSmppClientHandler());
			} catch (Exception e) {
				logger.error("Exception : ", e);
			}
			try {
				sleep(5000);
			} catch (InterruptedException e) {
				e.printStackTrace();
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
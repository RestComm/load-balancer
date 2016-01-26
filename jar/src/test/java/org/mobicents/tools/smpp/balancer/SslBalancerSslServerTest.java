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
import org.mobicents.tools.smpp.balancer.core.SmppBalancerRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cloudhopper.smpp.SmppSession;
import com.cloudhopper.smpp.impl.DefaultSmppClient;
import com.cloudhopper.smpp.impl.DefaultSmppServer;
import com.cloudhopper.smpp.type.SmppChannelException;
/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */
public class SslBalancerSslServerTest {
	private static final Logger logger = LoggerFactory
			.getLogger(CommonTest.class);

	private static ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors
			.newCachedThreadPool();

	private static ScheduledThreadPoolExecutor monitorExecutor = (ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(1, new ThreadFactory() {
				private AtomicInteger sequence = new AtomicInteger(0);
				public Thread newThread(Runnable r) {
					Thread t = new Thread(r);
					t.setName("MonitorPool-" + sequence.getAndIncrement());
					return t;
				}
			});

	private static int clientNumbers = 1;
	private static int serverNumbers = 1;
	private static DefaultSmppServer[] serverArray;
	private static SmppBalancerRunner loadBalancerSmpp;
	private static DefaultSmppServerHandler serverHandler;

	@BeforeClass
	public static void initialization() {
		// start servers
		serverArray = new DefaultSmppServer[serverNumbers];
		for (int i = 0; i < serverNumbers; i++) {
			serverHandler = new DefaultSmppServerHandler();
			serverArray[i] = new DefaultSmppServer(ConfigInit.getSmppServerConfiguration(i,true),serverHandler, executor, monitorExecutor);
			logger.info("Starting SMPP server...");
			try {
				serverArray[i].start();
			} catch (SmppChannelException e) {
				logger.info("SMPP server does not started");
				e.printStackTrace();
			}
			logger.info("SMPP server started");
		}

		// start lb
		loadBalancerSmpp = new SmppBalancerRunner();
		loadBalancerSmpp.start(ConfigInit.getLbProperties(true,true));
	}
	// tests SSL client connection to SSL server
	@Test
	public void testSslClient() {
		Locker locker = new Locker(clientNumbers);
		// start client
		new Load(locker,true).start();
		locker.waitForClients();
		assertEquals(1,serverHandler.getSmsNumber().get());
		assertTrue(loadBalancerSmpp.getBalancerDispatcher().getClientSessions().isEmpty());
		assertTrue(loadBalancerSmpp.getBalancerDispatcher().getServerSessions().isEmpty());
		serverHandler.resetCounters();
	}
	// tests noSSL client to SSL server
	@Test
	public void testNoSslClient() {
		Locker locker = new Locker(clientNumbers);
		// start client
		new Load(locker,false).start();
		locker.waitForClients();
		assertEquals(1,serverHandler.getSmsNumber().get());
		assertTrue(loadBalancerSmpp.getBalancerDispatcher().getClientSessions().isEmpty());
		assertTrue(loadBalancerSmpp.getBalancerDispatcher().getServerSessions().isEmpty());
	}

	@AfterClass
	public static void finalization() {

		for (int i = 1; i < serverNumbers; i++) {
			logger.info("Stopping SMPP server " + i + " ...");
			serverArray[i].stop();
			logger.info("SMPP server " + i + "stopped");
		}
		executor.shutdownNow();
		monitorExecutor.shutdownNow();
		logger.info("Done. Exiting");

	}

	private class Load extends Thread {
		private ClientListener listener;
		private boolean isSslClient;

		Load(ClientListener listener, boolean isSslClient) {
			this.listener = listener;
			this.isSslClient = isSslClient;
		}

		public void run() {
			DefaultSmppClient client = new DefaultSmppClient();
			SmppSession session = null;
			try {
				session = client.bind(ConfigInit.getSmppSessionConfiguration(1,isSslClient),new DefaultSmppClientHandler());
				session.submit(ConfigInit.getSubmitSm(), 12000);
				session.unbind(5000);
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

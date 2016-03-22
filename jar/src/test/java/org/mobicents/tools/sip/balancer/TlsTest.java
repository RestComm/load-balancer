package org.mobicents.tools.sip.balancer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import javax.sip.ListeningPoint;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.xml.DOMConfigurator;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mobicents.tools.sip.balancer.AppServer;
import org.mobicents.tools.sip.balancer.operation.Shootist;

public class TlsTest {
	
	static {
		String logLevel = System.getProperty("logLevel", "DEBUG");
		String logConfigFile = System.getProperty("logConfigFile");

		if(logConfigFile == null) {
			Logger.getRootLogger().addAppender(new ConsoleAppender(
					new PatternLayout("%r (%t) %p [%c{1}%x] %m%n")));
			Logger.getRootLogger().setLevel(Level.toLevel(logLevel));
		} else {
		    DOMConfigurator.configure(logConfigFile);
		}
	}

		Shootist shootist1, shootist2;
		static AppServer appServer;

		@Before
		public void setUp() throws Exception {
			
			shootist1 = new Shootist(ListeningPoint.TLS,5034, 5033);
			shootist2 = new Shootist(ListeningPoint.TLS,5033, 5034);
			
			Thread.sleep(5000);
		}

		@After
		public void tearDown() throws Exception {
			shootist1.stop();
			shootist2.stop();
		}

		@Test
		public void testInviteAckLandOnDifferentNodes() throws Exception {
						
			boolean wasRinging = false;
			boolean wasOk = false;
			boolean wasInvite = false;
			boolean wasAck = false;
			boolean wasBye = false;
			shootist1.callerSendsBye = true;
			shootist1.start();
			shootist2.start();
			shootist1.sendInitialInvite();
			Thread.sleep(5000);
			shootist1.sendBye();
			Thread.sleep(2000);
			for(Response res : shootist1.responses)
			{
				if(res.getStatusCode() == Response.RINGING)
					wasRinging = true;
				if(res.getStatusCode() == Response.OK)
					wasOk = true;
			}

			for(Request req : shootist2.requests)
			{
				if(req.getMethod().equals(Request.INVITE))
					wasInvite = true;
				if(req.getMethod().equals(Request.ACK))
					wasAck = true;
				if(req.getMethod().equals(Request.BYE))
					wasBye = true;
			}
			assertEquals(3,shootist2.requests.size());
			assertTrue(wasOk);
			assertTrue(wasRinging);
			assertTrue(wasInvite);
			assertTrue(wasAck);
			assertTrue(wasBye);
		
		}

}

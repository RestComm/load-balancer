package org.mobicents.tools.sip.balancer;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.Properties;

import javax.sip.ListeningPoint;
import javax.sip.message.Response;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mobicents.tools.sip.balancer.operation.Shootist;
import org.mobicents.tools.smpp.balancer.ConfigInit;

public class SinglePointTest {
	
	BalancerRunner balancer;
	Shootist shootistTcp,shootistTls;
	AppServer server;
	AppServer ringingAppServer;
	AppServer okAppServer;
	Properties properties;

	public void setUp(Boolean terminateTLS) throws Exception
	{
		shootistTcp = new Shootist(ListeningPoint.TCP,5060);
		shootistTls = new Shootist(ListeningPoint.TLS,5061);
		balancer = new BalancerRunner();
		properties = new Properties();
		properties.setProperty("javax.sip.STACK_NAME", "SipBalancerForwarder");
		properties.setProperty("javax.sip.AUTOMATIC_DIALOG_SUPPORT", "off");
		// You need 16 for logging traces. 32 for debug + traces.
		// Your code will limp at 32 but it is best for debugging.
		properties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "32");
		properties.setProperty("gov.nist.javax.sip.DEBUG_LOG",
				"logs/sipbalancerforwarderdebug.txt");
		properties.setProperty("gov.nist.javax.sip.SERVER_LOG",
				"logs/sipbalancerforwarder.xml");
		properties.setProperty("gov.nist.javax.sip.THREAD_POOL_SIZE", "2");
		properties.setProperty("gov.nist.javax.sip.REENTRANT_LISTENER", "true");
		properties.setProperty("gov.nist.javax.sip.CANCEL_CLIENT_TRANSACTION_CHECKED", "false");
		properties.setProperty("terminateTLSTraffic",String.valueOf(terminateTLS));
		properties.setProperty("host", "127.0.0.1");
		properties.setProperty("externalTcpPort", "5060");
		properties.setProperty("externalTlsPort", "5061");
		properties.setProperty("javax.net.ssl.keyStore", ConfigInit.class.getClassLoader().getResource("keystore").getFile());
		properties.setProperty("javax.net.ssl.keyStorePassword", "123456");
		properties.setProperty("javax.net.ssl.trustStore", ConfigInit.class.getClassLoader().getResource("keystore").getFile());
		properties.setProperty("javax.net.ssl.trustStorePassword", "123456");
		properties.setProperty("gov.nist.javax.sip.TLS_CLIENT_PROTOCOLS", "TLSv1");
		properties.setProperty("gov.nist.javax.sip.TLS_CLIENT_AUTH_TYPE", "Disabled");
		balancer.start(properties);
		if(terminateTLS)
			server = new AppServer("node" ,4060 , "127.0.0.1", 2000, 5060, 5060, "0", ListeningPoint.TCP);
		else
			server = new AppServer("node" ,4060 , "127.0.0.1", 2000, 5061, 5061, "0", ListeningPoint.TLS);
		server.start();
		Thread.sleep(5000);
	}
	
	@After
	public void tearDown() throws Exception 
	{
		shootistTcp.stop();
		shootistTls.stop();
		server.stop();
		balancer.stop();
	}
	
	@Test
	public void testTcpToTcp() throws Exception
	{
		setUp(true);
		boolean wasRinging = false;
		boolean wasOk = false;
		shootistTcp.sendInitialInvite();
		Thread.sleep(5000);
		shootistTcp.sendBye();
		Thread.sleep(15000);
		assertTrue(server.getTestSipListener().isInviteReceived());
		assertTrue(server.getTestSipListener().isAckReceived());
		assertTrue(server.getTestSipListener().getByeReceived());
		for(Response res : shootistTcp.responses)
		{
			if(res.getStatusCode() == Response.RINGING)
				wasRinging = true;
			if(res.getStatusCode() == Response.OK)
				wasOk = true;
		}
		assertTrue(wasOk);
		assertTrue(wasRinging);
	}
	
	@Test
	public void testTlsToTcp() throws Exception
	{
		setUp(true);
		boolean wasRinging = false;
		boolean wasOk = false;
		shootistTls.sendInitialInvite();
		Thread.sleep(5000);
		shootistTls.sendBye();
		Thread.sleep(15000);
		assertTrue(server.getTestSipListener().isInviteReceived());
		assertTrue(server.getTestSipListener().isAckReceived());
		assertTrue(server.getTestSipListener().getByeReceived());
		for(Response res : shootistTls.responses)
		{
			if(res.getStatusCode() == Response.RINGING)
				wasRinging = true;
			if(res.getStatusCode() == Response.OK)
				wasOk = true;
		}
		assertTrue(wasOk);
		assertTrue(wasRinging);
	}
	
	@Test
	public void testTlsToTls() throws Exception
	{
		setUp(false);
		boolean wasRinging = false;
		boolean wasOk = false;
		shootistTls.sendInitialInvite();
		Thread.sleep(5000);
		shootistTls.sendBye();
		Thread.sleep(15000);
		assertTrue(server.getTestSipListener().isInviteReceived());
		assertTrue(server.getTestSipListener().isAckReceived());
		assertTrue(server.getTestSipListener().getByeReceived());
		for(Response res : shootistTls.responses)
		{
			if(res.getStatusCode() == Response.RINGING)
				wasRinging = true;
			if(res.getStatusCode() == Response.OK)
				wasOk = true;
		}
		assertTrue(wasOk);
		assertTrue(wasRinging);
	}


}

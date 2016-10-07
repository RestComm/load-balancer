package org.mobicents.tools.sip.balancer;

import static org.junit.Assert.assertTrue;

import javax.sip.ListeningPoint;
import javax.sip.message.Response;

import org.junit.After;
import org.junit.Test;
import org.mobicents.tools.configuration.LoadBalancerConfiguration;
import org.mobicents.tools.sip.balancer.operation.Shootist;

public class SinglePointTest {
	
	BalancerRunner balancer;
	Shootist shootistTcp,shootistTls;
	AppServer server;
	AppServer ringingAppServer;
	AppServer okAppServer;

	public void setUp(Boolean terminateTLS) throws Exception
	{
		
		
		shootistTcp = new Shootist(ListeningPoint.TCP,5060);
		shootistTls = new Shootist(ListeningPoint.TLS,5061);
		balancer = new BalancerRunner();
		LoadBalancerConfiguration lbConfig = new LoadBalancerConfiguration();
		lbConfig.getSipStackConfiguration().getSipStackProperies().setProperty("javax.sip.AUTOMATIC_DIALOG_SUPPORT", "off");
		lbConfig.getSipStackConfiguration().getSipStackProperies().setProperty("gov.nist.javax.sip.TRACE_LEVEL", "32");
		lbConfig.getSipStackConfiguration().getSipStackProperies().setProperty("gov.nist.javax.sip.DEBUG_LOG","logs/sipbalancerforwarderdebug.txt");
		lbConfig.getSipStackConfiguration().getSipStackProperies().setProperty("gov.nist.javax.sip.SERVER_LOG","logs/sipbalancerforwarder.xml");
		lbConfig.getSipStackConfiguration().getSipStackProperies().setProperty("gov.nist.javax.sip.THREAD_POOL_SIZE", "2");
		lbConfig.getSipStackConfiguration().getSipStackProperies().setProperty("gov.nist.javax.sip.REENTRANT_LISTENER", "true");
		lbConfig.getSipStackConfiguration().getSipStackProperies().setProperty("gov.nist.javax.sip.CANCEL_CLIENT_TRANSACTION_CHECKED", "false");
		lbConfig.getSipStackConfiguration().getSipStackProperies().setProperty("javax.net.ssl.keyStore", SinglePointTest.class.getClassLoader().getResource("keystore").getFile());
		lbConfig.getSipStackConfiguration().getSipStackProperies().setProperty("javax.net.ssl.trustStorePassword", "123456");
		lbConfig.getSipStackConfiguration().getSipStackProperies().setProperty("javax.net.ssl.trustStore",SinglePointTest.class.getClassLoader().getResource("keystore").getFile());
		lbConfig.getSipStackConfiguration().getSipStackProperies().setProperty("javax.net.ssl.keyStorePassword","123456");
		lbConfig.getSipConfiguration().getExternalLegConfiguration().setTcpPort(5060);
		lbConfig.getSipConfiguration().getExternalLegConfiguration().setTlsPort(5061);
		lbConfig.getSslConfiguration().setTerminateTLSTraffic(terminateTLS);
		balancer.start(lbConfig);
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

package org.mobicents.tools.sip.balancer;

import static org.junit.Assert.assertTrue;

import java.util.Properties;

import javax.sip.ListeningPoint;
import javax.sip.message.Response;

import org.junit.After;
import org.junit.Test;
import org.mobicents.tools.sip.balancer.operation.Shootist;

public class Response5xxTest {
	
	BalancerRunner balancer;
	Shootist shootist;
	AppServer server;
	AppServer ringingAppServer;
	AppServer okAppServer;
	Properties properties;

	public void setUp() throws Exception
	{
		shootist = new Shootist(ListeningPoint.TCP,5060);
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
		properties.setProperty("host", "127.0.0.1");
		properties.setProperty("externalTcpPort", "5060");
		properties.setProperty("gov.nist.javax.sip.TLS_CLIENT_PROTOCOLS", "TLSv1");
		properties.setProperty("gov.nist.javax.sip.TLS_CLIENT_AUTH_TYPE", "Disabled");
		properties.setProperty("isSend5xxResponse", "true");
		properties.setProperty("isSend5xxResponseReasonHeader", "Destination not available");
		properties.setProperty("isSend5xxResponseSatusCode", "503");
		
		balancer.start(properties);
		server = new AppServer("node" ,4060 , "127.0.0.1", 2000, 5060, 5060, "0", ListeningPoint.TCP, true);
		server.start();
		Thread.sleep(5000);
	}
	
	@After
	public void tearDown() throws Exception 
	{
		shootist.stop();
		server.stop();
		balancer.stop();
	}
	
	@Test
	public void testSend503Response() throws Exception
	{
		setUp();
		boolean was503 = false;
		shootist.sendInitialInvite();
		Thread.sleep(1000);
		for(Response res : shootist.responses)
		{
			if(res.getStatusCode() != Response.SERVICE_UNAVAILABLE)
				was503 = true;
		}
		assertTrue(was503);
	}
}

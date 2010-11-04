package org.mobicents.tools.sip.balancer.operation;

import java.net.InetAddress;
import java.rmi.RemoteException;
import java.util.Properties;

import javax.sip.SipProvider;
import javax.sip.address.SipURI;
import javax.sip.header.UserAgentHeader;

import org.mobicents.tools.sip.balancer.AppServer;
import org.mobicents.tools.sip.balancer.BalancerRunner;
import org.mobicents.tools.sip.balancer.ProtocolObjects;
import org.mobicents.tools.sip.balancer.TestSipListener;

import junit.framework.TestCase;

public class InviteTransactionFailover extends TestCase{
	BalancerRunner balancer;
	int numNodes = 2;
	AppServer[] servers = new AppServer[numNodes];

	/* (non-Javadoc)
	 * @see junit.framework.TestCase#setUp()
	 */
	protected void setUp() throws Exception {
		super.setUp();
		balancer = new BalancerRunner();
		Properties properties = new Properties();
		properties.setProperty("javax.sip.STACK_NAME", "SipBalancerForwarder");
		properties.setProperty("javax.sip.AUTOMATIC_DIALOG_SUPPORT", "off");
		// You need 16 for logging traces. 32 for debug + traces.
		// Your code will limp at 32 but it is best for debugging.
		properties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "32");
		properties.setProperty("gov.nist.javax.sip.DEBUG_LOG",
				"logs/sipbalancerforwarderdebug.txt");
		properties.setProperty("gov.nist.javax.sip.SERVER_LOG",
				"logs/sipbalancerforwarder.xml");
		properties.setProperty("gov.nist.javax.sip.THREAD_POOL_SIZE", "64");
		properties.setProperty("gov.nist.javax.sip.REENTRANT_LISTENER", "true");
		properties.setProperty("gov.nist.javax.sip.CANCEL_CLIENT_TRANSACTION_CHECKED", "false");
		
		properties.setProperty("host", "127.0.0.1");
		properties.setProperty("internalPort", "5065");
		properties.setProperty("externalPort", "5060");
		balancer.start(properties);
		
		
		for(int q=0;q<servers.length;q++) {
			servers[q] = new AppServer("node" + q,4060+q);
			servers[q].start();
		}
		Thread.sleep(5000);
	}

	/* (non-Javadoc)
	 * @see junit.framework.TestCase#tearDown()
	 */
	protected void tearDown() throws Exception {
		super.tearDown();
		for(int q=0;q<servers.length;q++) {
			servers[q].stop();
		}
		balancer.stop();
	}
	
	public void testFailDetection() throws Exception {
			
			String[] nodes = balancer.getNodeList();
			assertEquals(numNodes, nodes.length);
			servers[0].sendHeartbeat = false;
			Thread.sleep(10000);
			nodes = balancer.getNodeList();
			assertEquals(numNodes-1, nodes.length);
	}
	
	public void testInviteTx() throws Exception {
		ProtocolObjects senderProtocolObjects = new ProtocolObjects("forward-udp-sender",
				"gov.nist", "UDP", false, null);
		TestSipListener sender = new TestSipListener(5080, 5060, senderProtocolObjects, true);
		SipProvider senderProvider = sender.createProvider();


		senderProvider.addSipListener(sender);

		senderProtocolObjects.start();

		String fromName = "forward-tcp-sender";
		String fromSipAddress = "sip-servlets.com";
		SipURI fromAddress = senderProtocolObjects.addressFactory.createSipURI(
				fromName, fromSipAddress);
		
		String toSipAddress = "sip-servlets.com";
		String toUser = "forward-receiver";
		SipURI toAddress = senderProtocolObjects.addressFactory.createSipURI(
				toUser, toSipAddress);
		
		sender.sendSipRequest("INVITE", fromAddress, toAddress, null, null, false, new String[] {UserAgentHeader.NAME, "extension-header"}, new String[] {"TestSipListener UA", "extension-sip-listener"}, true);		

		Thread.sleep(20000);
	}

}

package org.mobicents.tools.sip.balancer;

import static org.junit.Assert.assertTrue;

import javax.sip.ListeningPoint;
import javax.sip.address.SipURI;
import javax.sip.message.Response;

import org.junit.After;
import org.junit.Test;
import org.mobicents.tools.configuration.LoadBalancerConfiguration;
import org.mobicents.tools.sip.balancer.operation.Shootist;

public class Ipv6Test {
	
	BalancerRunner balancer;
	Shootist shootistipv4;
	Shootist shootistipv6;
	AppServer ipv4Server;
	AppServer ipv6Server;

	public void setUp() throws Exception
	{
		shootistipv4 = new Shootist(ListeningPoint.TCP,5060,5033);
		shootistipv6 = new Shootist(ListeningPoint.TCP,5070,5034,true);
		LoadBalancerConfiguration lbConfig = new LoadBalancerConfiguration();
		lbConfig.getSipStackConfiguration().getSipStackProperies().setProperty("javax.sip.AUTOMATIC_DIALOG_SUPPORT", "off");
		lbConfig.getSipStackConfiguration().getSipStackProperies().setProperty("gov.nist.javax.sip.TRACE_LEVEL", "32");
		lbConfig.getSipStackConfiguration().getSipStackProperies().setProperty("gov.nist.javax.sip.DEBUG_LOG","logs/sipbalancerforwarderdebug.txt");
		lbConfig.getSipStackConfiguration().getSipStackProperies().setProperty("gov.nist.javax.sip.SERVER_LOG","logs/sipbalancerforwarder.xml");
		lbConfig.getSipStackConfiguration().getSipStackProperies().setProperty("gov.nist.javax.sip.THREAD_POOL_SIZE", "2");
		lbConfig.getSipStackConfiguration().getSipStackProperies().setProperty("gov.nist.javax.sip.REENTRANT_LISTENER", "true");
		lbConfig.getCommonConfiguration().setIpv6Host("::1");
		lbConfig.getSipConfiguration().getExternalLegConfiguration().setTcpPort(5060);
		lbConfig.getSipConfiguration().getInternalLegConfiguration().setTcpPort(5065);
		lbConfig.getSipConfiguration().getExternalLegConfiguration().setIpv6TcpPort(5070);
		lbConfig.getSipConfiguration().getInternalLegConfiguration().setIpv6TcpPort(5075);
		balancer = new BalancerRunner();
		balancer.start(lbConfig);
		Thread.sleep(2000);
		ipv6Server = new AppServer(true , "node" ,4060 , "127.0.0.1", 2000, 5070, 5075, "0", ListeningPoint.TCP);
		ipv6Server.start();
		ipv4Server = new AppServer("node" ,4061 , "127.0.0.1", 2000, 5060, 5065, "0", ListeningPoint.TCP,2223);
		ipv4Server.start();
		Thread.sleep(5000);
	}
	
	@After
	public void tearDown() throws Exception 
	{
		shootistipv4.stop();
		shootistipv6.stop();
		ipv4Server.stop();
		ipv6Server.stop();
		balancer.stop();
		
	}
	
	@Test
	public void testExternalTcpRequest() throws Exception
	{
		boolean isShootistipv4GetOK = false;
		boolean isShootistipv6GetOK = false;
		setUp();
		shootistipv6.sendInitialInvite();
		Thread.sleep(5000);
		shootistipv6.sendBye();
		Thread.sleep(5000);
		shootistipv4.sendInitialInvite();
		Thread.sleep(5000);
		shootistipv4.sendBye();
		Thread.sleep(5000);
		
		for(Response res : shootistipv4.responses)
			if(res.getStatusCode() == Response.OK)
				isShootistipv4GetOK = true;
		for(Response res : shootistipv6.responses)
			if(res.getStatusCode() == Response.OK)
				isShootistipv6GetOK = true;
		assertTrue(isShootistipv4GetOK);
		assertTrue(isShootistipv6GetOK);
		assertTrue(ipv6Server.getTestSipListener().isInviteReceived());
		assertTrue(ipv6Server.getTestSipListener().isAckReceived());
		assertTrue(ipv6Server.getTestSipListener().getByeReceived());
		assertTrue(ipv4Server.getTestSipListener().isInviteReceived());
		assertTrue(ipv4Server.getTestSipListener().isAckReceived());
		assertTrue(ipv4Server.getTestSipListener().getByeReceived());
	}
	
	@Test
	public void testInternalTcpRequest() throws Exception
	{
		setUp();
		String fromName = "sender";
		String fromHost = "sip-servlets.com";
		SipURI fromAddress6 = ipv6Server.protocolObjects.addressFactory.createSipURI(fromName, fromHost);
		String toUser = "replaces";
		String toHost = "sip-servlets.com";
		SipURI toAddress6 = ipv6Server.protocolObjects.addressFactory.createSipURI(toUser, toHost);
		SipURI ruriIpv6 = ipv6Server.protocolObjects.addressFactory.createSipURI("usera", "[::1]:5034");
		ruriIpv6.setLrParam();
		SipURI routeIpv6 = ipv6Server.protocolObjects.addressFactory.createSipURI("lbint", "[::1]:5075");
		routeIpv6.setParameter("node_host", "::1");
		routeIpv6.setParameter("node_port", "4060");
		routeIpv6.setTransportParam(ListeningPoint.TCP);
		//ipv4
		SipURI fromAddress4 = ipv4Server.protocolObjects.addressFactory.createSipURI(fromName, fromHost);
		String toUser4 = "replaces";
		String toHost4 = "sip-servlets.com";
		SipURI toAddress4 = ipv6Server.protocolObjects.addressFactory.createSipURI(toUser4, toHost4);
		SipURI ruriIpv4 = ipv6Server.protocolObjects.addressFactory.createSipURI("usera", "127.0.0.1:5033");
		ruriIpv4.setLrParam();
		SipURI routeIpv4 = ipv6Server.protocolObjects.addressFactory.createSipURI("lbint", "127.0.0.1:5065");
		routeIpv4.setParameter("node_host", "127.0.0.1");
		routeIpv4.setParameter("node_port", "4061");
		routeIpv4.setTransportParam(ListeningPoint.TCP);
		routeIpv4.setLrParam();
		shootistipv4.start();
		shootistipv6.start();
		ipv4Server.sipListener.sendSipRequest("INVITE", fromAddress4, toAddress4, null, routeIpv4, false, null, null, ruriIpv4);
		ipv6Server.sipListener.sendSipRequest("INVITE", fromAddress6, toAddress6, null, routeIpv6, false, null, null, ruriIpv6);
		Thread.sleep(10000);
	}
}


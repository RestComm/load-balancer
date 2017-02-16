package org.mobicents.tools.sip.balancer.transport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import gov.nist.javax.sip.ListeningPointExt;

import javax.sip.ListeningPoint;
import javax.sip.address.SipURI;
import javax.sip.header.RecordRouteHeader;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mobicents.tools.configuration.LoadBalancerConfiguration;
import org.mobicents.tools.sip.balancer.AppServer;
import org.mobicents.tools.sip.balancer.BalancerRunner;
import org.mobicents.tools.sip.balancer.EventListener;
import org.mobicents.tools.sip.balancer.SinglePointTest;
import org.mobicents.tools.sip.balancer.WorstCaseUdpTestAffinityAlgorithm;
import org.mobicents.tools.sip.balancer.operation.Shootist;

//test switch external WSS transport to TCP internal
public class InternalTransportTest 
{
	BalancerRunner balancer;
	int numNodes = 2;
	AppServer[] servers = new AppServer[numNodes];
	Shootist shootist;
	static AppServer invite;
	static AppServer ack;
	static AppServer bye;
	AppServer ringingAppServer;
	AppServer okAppServer;

	@Before
	public void setUp() throws Exception {
		shootist = new Shootist(ListeningPointExt.WSS,5061);
		balancer = new BalancerRunner();
		LoadBalancerConfiguration lbConfig = new LoadBalancerConfiguration();
		lbConfig.getSipConfiguration().getExternalLegConfiguration().setTcpPort(5060);
		lbConfig.getSipConfiguration().getInternalLegConfiguration().setTcpPort(5065);
		lbConfig.getSipConfiguration().getExternalLegConfiguration().setWssPort(5061);
		lbConfig.getSipConfiguration().getInternalLegConfiguration().setWssPort(5066);
		lbConfig.getSipConfiguration().getAlgorithmConfiguration().setAlgorithmClass(WorstCaseUdpTestAffinityAlgorithm.class.getName());
		lbConfig.getSipConfiguration().getAlgorithmConfiguration().setEarlyDialogWorstCase(true);
		lbConfig.getSipStackConfiguration().getSipStackProperies().setProperty("javax.net.ssl.keyStore", SinglePointTest.class.getClassLoader().getResource("keystore").getFile());
		lbConfig.getSipStackConfiguration().getSipStackProperies().setProperty("javax.net.ssl.trustStorePassword", "123456");
		lbConfig.getSipStackConfiguration().getSipStackProperies().setProperty("javax.net.ssl.trustStore",SinglePointTest.class.getClassLoader().getResource("keystore").getFile());
		lbConfig.getSipStackConfiguration().getSipStackProperies().setProperty("javax.net.ssl.keyStorePassword","123456");
		//lbConfig.getSslConfiguration().setTerminateTLSTraffic(true);
		lbConfig.getSipConfiguration().setInternalTransport(ListeningPoint.TCP);
		balancer.start(lbConfig);
		
		for(int q=0;q<servers.length;q++) {
			servers[q] = new AppServer("node" + q,4060+q , "127.0.0.1", 2000, 5060, 5065, "0", ListeningPoint.TCP, 2222+q);			
			servers[q].start();		
		}
		
		Thread.sleep(5000);
	}

	@After
	public void tearDown() throws Exception {
		shootist.stop();
		for(int q=0;q<servers.length;q++) {
			servers[q].stop();
		}
		balancer.stop();
	}

	@Test
	public void testInviteAckLandOnDifferentNodes() throws Exception {
		EventListener failureEventListener = new EventListener() {

			@Override
			public void uasAfterResponse(int statusCode, AppServer source) {
				
			}
			
			@Override
			public void uasAfterRequestReceived(String method, AppServer source) {
				if(method.equals("INVITE")) invite = source;
				if(method.equals("ACK")) {
					ack = source;
				
					}
				if(method.equals("BYE")) {
					bye = source;

				}

				
			}

			@Override
			public void uacAfterRequestSent(String method, AppServer source) {
				// TODO Auto-generated method stub
				
			}

			@Override
			public void uacAfterResponse(int statusCode, AppServer source) {
				// TODO Auto-generated method stub
				
			}
		};
		for(AppServer as:servers) as.setEventListener(failureEventListener);
		
		shootist.callerSendsBye = true;
		shootist.sendInitialInvite();
		Thread.sleep(5000);
		shootist.sendBye();
		Thread.sleep(15000);
		assertNotNull(invite);
		assertNotNull(ack);
		assertEquals(ack, invite);
		assertNotSame(ack, bye);		
	}
	
	@Test
	public void testOKRingingLandOnDifferentNodes() throws Exception {
		
		EventListener failureEventListener = new EventListener() {
			
			@Override
			public void uasAfterResponse(int statusCode, AppServer source) {
				
				
			}
			
			@Override
			public void uasAfterRequestReceived(String method, AppServer source) {
				// TODO Auto-generated method stub
				
			}

			@Override
			public void uacAfterRequestSent(String method, AppServer source) {
				// TODO Auto-generated method stub
				
			}

			@Override
			public void uacAfterResponse(int statusCode, AppServer source) {
				if(statusCode == 180) {					
					ringingAppServer = source;	
				} else if(statusCode == 200){
					okAppServer = source;
					
				}
			}
		};
		for(AppServer as:servers) as.setEventListener(failureEventListener);
		shootist.callerSendsBye = true;
		
		String fromName = "sender";
		String fromHost = "sip-servlets.com";
		SipURI fromAddress = servers[0].protocolObjects.addressFactory.createSipURI(
				fromName, fromHost);
				
		String toUser = "replaces";
		String toHost = "sip-servlets.com";
		SipURI toAddress = servers[0].protocolObjects.addressFactory.createSipURI(
				toUser, toHost);
		
		SipURI ruri = servers[0].protocolObjects.addressFactory.createSipURI(
				"usera", "127.0.0.1:5033");
		ruri.setTransportParam(ListeningPointExt.WSS);
		ruri.setLrParam();
		
		SipURI route = servers[0].protocolObjects.addressFactory.createSipURI(
				"lbint", "127.0.0.1:5065");
		route.setParameter("node_host", "127.0.0.1");
		route.setParameter("node_port", "4060");
		route.setTransportParam(ListeningPoint.TCP);
		route.setLrParam();
		shootist.start();
		servers[0].sipListener.sendSipRequest("INVITE", fromAddress, toAddress, null, route, false, null, null, ruri);
		Thread.sleep(16000);
		assertTrue(shootist.inviteRequest.getHeader(RecordRouteHeader.NAME).toString().contains("node_host"));
		assertNotSame(ringingAppServer, okAppServer);
		assertNotNull(ringingAppServer);
		assertNotNull(okAppServer);
	}
}


package org.mobicents.tools.sip.balancer;

import java.text.ParseException;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.sip.SipFactory;
import javax.sip.message.Request;

import junit.framework.TestCase;


public class ClusterSubdomainAffinityAlgorithmTest extends TestCase {
	static final String failoverGroup = 	"(original,partner)";
	static final String groups2 = 	"(127.0.0.1,4.322.5345.5,234235235435345)    (55345435345,546,345,23,52   63456,546546 ,46345 34,45)(3435345345345345,43543fdg,ffdgdfg.f.f.f.ff.f.f.f..f,f,f,5,gggderf,dfgafa)";
	static final String groups = 	"(a,b)(c,d,g)";
	static final String inviteRequest = "INVITE sip:joe@company.com SIP/2.0\r\n"+
	"To: sip:joe@company.com\r\n"+
	"From: sip:caller@university.edu ;tag=1234\r\n"+
	"Call-ID: cid\r\n"+
	"CSeq: 9 INVITE\r\n"+
	"Via: SIP/2.0/UDP 135.180.130.133\r\n"+
	"Content-Type: application/sdp\r\n"+
	"\r\n"+
	"v=0\r\n"+
	"o=mhandley 29739 7272939 IN IP4 126.5.4.3\r\n" +
	"c=IN IP4 135.180.130.88\r\n" +
	"m=video 3227 RTP/AVP 31\r\n" +
	"m=audio 4921 RTP/AVP 12\r\n" +
	"a=rtpmap:31 LPC\r\n\r\n";
	
	// Test if groups are parsed correctly
	public void testGroupsLoad() throws Exception, ParseException {
		ClusterSubdomainAffinityAlgorithm algorithm = new ClusterSubdomainAffinityAlgorithm();
		algorithm.loadSubclusters(groups);
		String data = algorithm.dumpSubcluster();
		String[] lines = data.split("\n");
		System.out.println(data);
		assertEquals(5, lines.length);
		assertTrue(data.contains("a:"));
		assertTrue(data.contains("b:"));
		assertTrue(data.contains("c:"));
		assertTrue(data.contains("d:"));
		assertTrue(data.contains("g:"));
	}
	
	// Test validation, no duplicate nodes allowed
	public void testGroupsLoad2() throws Exception, ParseException {
		ClusterSubdomainAffinityAlgorithm algorithm = new ClusterSubdomainAffinityAlgorithm();
		try {
			algorithm.loadSubclusters(groups2);
		} catch (Exception e) {
			return;
		}
		fail("Excpeted excpetion");
	}

	// Test actual failover by adding a lot of noise nodes and only 1 partner for the original
	public void partnerFailoverTest() throws Exception, ParseException {
		try {
			BalancerContext.balancerContext.nodes = new CopyOnWriteArrayList<SIPNode>();
			ClusterSubdomainAffinityAlgorithm algorithm = new ClusterSubdomainAffinityAlgorithm();
			for(int q=0;q<100;q++) {
				BalancerContext.balancerContext.nodes.add(new SIPNode("alphabeticalNoise"+q, "alphabeticalNoise"+q));
			}
			for(int q=0;q<100;q++) {
				BalancerContext.balancerContext.nodes.add(new SIPNode(q+"alphabeticalNoise"+q, q+"alphabeticalNoise"+q));
			}
			SIPNode originalNode = new SIPNode("original", "original");
			SIPNode partnerNode = new SIPNode("partner", "partner");

			// This is dead BalancerContext.balancerContext.nodes.add(originalNode);
			BalancerContext.balancerContext.nodes.add(partnerNode);
			for(int q=0;q<100;q++) {
				BalancerContext.balancerContext.nodes.add(new SIPNode("nonParner"+q, "nonPartner"+q));
			}
			algorithm.callIdMap.put("cid", originalNode);
			Request request = SipFactory.getInstance().createMessageFactory().createRequest(inviteRequest);
			algorithm.loadSubclusters(failoverGroup);
			SIPNode resultNode = algorithm.processExternalRequest(request);
			assertEquals("partner", resultNode.getIp());
		} finally {
			BalancerContext.balancerContext.nodes = null;
		}
	}
}

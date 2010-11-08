package org.mobicents.tools.sip.balancer.algorithms;

import java.text.ParseException;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.sip.PeerUnavailableException;
import javax.sip.SipFactory;
import javax.sip.message.Response;

import org.mobicents.tools.sip.balancer.BalancerContext;
import org.mobicents.tools.sip.balancer.CallIDAffinityBalancerAlgorithm;
import org.mobicents.tools.sip.balancer.SIPNode;

import junit.framework.TestCase;

public class RingingFailoverTest extends TestCase {
	
	static final String ringing = 	"SIP/2.0 180 Ringing\n" + "To: <sip:LittleGuy@there.com>;tag=5432\n" +
	"Via: SIP/2.0/UDP 127.0.0.1:1111;branch=z9hG4bK-3530-488ff2840f609639903eff914df9870f202e2zsd,SIP/2.0/UDP 127.0.0.1:2222;branch=z9hG4bK-3530-488ff2840f609639903eff914df9870f202e2,SIP/2.0/UDP 127.0.0.1:5033;branch=z9hG4bK-3530-488ff2840f609639903eff914df9870f\n"+
	"Record-Route: <sip:127.0.0.1:5065;transport=udp;lr>,<sip:127.0.0.1:5060;transport=udp;lr>\n"+
	"CSeq: 1 INVITE\n"+
	"Call-ID: 202e236d75a43c17b234a992873c3c74@127.0.0.1\n"+
	"From: <sip:BigGuy@here.com>;tag=12345\n"+
	"Content-Length: 0\n";
	
	public void testViaHeaderRewrite() throws Exception, ParseException {
		CallIDAffinityBalancerAlgorithm algorithm = new CallIDAffinityBalancerAlgorithm();
		Response response = SipFactory.getInstance().createMessageFactory().createResponse(ringing);
			
		String node = "1.2.3.4";
		Integer port = 1234;
		SIPNode adNode = new SIPNode(node, node);
		adNode.getProperties().put("udpPort", port);
		BalancerContext.balancerContext.nodes = new CopyOnWriteArrayList<SIPNode>(new SIPNode[]{adNode});
		algorithm.processExternalResponse(response);
		
		if(!response.toString().contains(node+":" + port)) {
			fail("Expected " + node);
		}
	}
}

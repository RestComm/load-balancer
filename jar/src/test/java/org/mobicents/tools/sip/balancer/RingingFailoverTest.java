/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, Red Hat, Inc. and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.mobicents.tools.sip.balancer;

import static org.junit.Assert.assertTrue;

import java.text.ParseException;

import javax.sip.SipFactory;
import javax.sip.message.Response;

import org.junit.Test;
import org.mobicents.tools.heartbeat.impl.Node;
import org.mobicents.tools.sip.balancer.BalancerContext;
import org.mobicents.tools.sip.balancer.CallIDAffinityBalancerAlgorithm;
import org.mobicents.tools.smpp.multiplexer.SmppToNodeRoundRobinAlgorithm;
import org.mobicents.tools.smpp.multiplexer.SmppToProviderRoundRobinAlgorithm;

public class RingingFailoverTest {
	
	static final String ringing = 	"SIP/2.0 180 Ringing\n" + "To: <sip:LittleGuy@there.com>;tag=5432\n" +
	"Via: SIP/2.0/UDP 127.0.0.1:1111;branch=z9hG4bK-3530-488ff2840f609639903eff914df9870f202e2zsd,SIP/2.0/UDP 127.0.0.1:2222;branch=z9hG4bK-3530-488ff2840f609639903eff914df9870f202e2,SIP/2.0/UDP 127.0.0.1:5033;branch=z9hG4bK-3530-488ff2840f609639903eff914df9870f\n"+
	"Record-Route: <sip:127.0.0.1:5065;transport=udp;lr>,<sip:127.0.0.1:5060;transport=udp;lr>\n"+
	"CSeq: 1 INVITE\n"+
	"Call-ID: 202e236d75a43c17b234a992873c3c74@127.0.0.1\n"+
	"From: <sip:BigGuy@here.com>;tag=12345\n"+
	"Content-Length: 0\n";
	
	@Test
	public void testViaHeaderRewrite() throws Exception, ParseException {
		CallIDAffinityBalancerAlgorithm algorithm = new CallIDAffinityBalancerAlgorithm();
		Response response = SipFactory.getInstance().createMessageFactory().createResponse(ringing);
			
		String node = "1.2.3.4";
		Integer port = 1234;
		Node adNode = new Node(node, node);
		adNode.getProperties().put("udpPort", ""+port);
		algorithm.balancerContext = new BalancerContext();
		algorithm.balancerContext.algorithmClassName = CallIDAffinityBalancerAlgorithm.class.getName();
		algorithm.balancerContext.smppToNodeAlgorithmClassName = SmppToNodeRoundRobinAlgorithm.class.getName();
		algorithm.balancerContext.smppToProviderAlgorithmClassName = SmppToProviderRoundRobinAlgorithm.class.getName();
		InvocationContext ctx = new InvocationContext("0",algorithm.balancerContext);

		ctx.sipNodeMap(false).put(new KeySip(adNode), adNode); 
				
		algorithm.invocationContext = ctx;
		algorithm.processExternalResponse(response,false);
		
		algorithm.stop();
		assertTrue(response.toString().contains(node+":" + port));			
	}
}

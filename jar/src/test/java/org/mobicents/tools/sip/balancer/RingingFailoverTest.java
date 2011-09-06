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
		algorithm.balancerContext = new BalancerContext();
		InvocationContext ctx = new InvocationContext("0",algorithm.balancerContext);
		ctx.nodes = new CopyOnWriteArrayList<SIPNode>(new SIPNode[]{adNode});
		algorithm.processExternalResponse(response);
		
		if(!response.toString().contains(node+":" + port)) {
			fail("Expected " + node);
		}
	}
}

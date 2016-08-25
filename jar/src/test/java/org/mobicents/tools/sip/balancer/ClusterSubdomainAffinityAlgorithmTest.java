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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.text.ParseException;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.sip.SipFactory;
import javax.sip.message.Request;

import org.junit.Test;


public class ClusterSubdomainAffinityAlgorithmTest{
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
	@Test
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
	@Test
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
	@Test
	public void testPartnerFailover() throws Exception, ParseException {
		try {
			
			ClusterSubdomainAffinityAlgorithm algorithm = new ClusterSubdomainAffinityAlgorithm();
			algorithm.balancerContext = new BalancerContext();
			algorithm.balancerContext.properties = new Properties();
			algorithm.balancerContext.properties.setProperty("subclusterMap", failoverGroup);
			
			algorithm.balancerContext.algorithmClassName = ClusterSubdomainAffinityAlgorithm.class.getName();
			InvocationContext ctx = new InvocationContext("0",algorithm.balancerContext);
			
			//ctx.nodes = new CopyOnWriteArrayList<SIPNode>();
			ctx.sipNodeMap = new ConcurrentHashMap<>();
			for(int q=0;q<100;q++) {
				SIPNode node = new SIPNode("alphabeticalNoise"+q, "alphabeticalNoise"+q);
				ctx.sipNodeMap.put(new KeySip(node),node);
			}
			for(int q=0;q<100;q++) {
				SIPNode node = new SIPNode(q+"alphabeticalNoise"+q, q+"alphabeticalNoise"+q);
				ctx.sipNodeMap.put(new KeySip(node),node);
			}
			SIPNode originalNode = new SIPNode("original", "original");
			SIPNode partnerNode = new SIPNode("partner", "partner");

			// This is dead BalancerContext.balancerContext.nodes.add(originalNode);
			ctx.sipNodeMap.put(new KeySip(partnerNode), partnerNode);
			for(int q=0;q<100;q++) {
				SIPNode node = new SIPNode("nonParner"+q, "nonPartner"+q);
				ctx.sipNodeMap.put(new KeySip(node),node);
			}
			algorithm.callIdMap.put("cid", originalNode);
			Request request = SipFactory.getInstance().createMessageFactory().createRequest(inviteRequest);
			algorithm.loadSubclusters(failoverGroup);
			algorithm.invocationContext = ctx;
			SIPNode resultNode = algorithm.processExternalRequest(request);
			assertEquals("partner", resultNode.getIp());
			originalNode = null;
			partnerNode = null;
			resultNode = null;
		} finally {
//			BalancerContext.balancerContext.nodes = null;
		}
	}
}

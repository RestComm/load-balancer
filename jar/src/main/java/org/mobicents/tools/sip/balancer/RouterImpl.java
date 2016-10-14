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

import gov.nist.javax.sip.header.CallID;
import gov.nist.javax.sip.stack.HopImpl;

import java.util.LinkedList;
import java.util.ListIterator;

import javax.sip.SipException;
import javax.sip.SipStack;
import javax.sip.address.Hop;
import javax.sip.address.Router;
import javax.sip.header.ViaHeader;
import javax.sip.message.Request;

/**
 * @deprecated
 * This custom implementation is not used anymore in the new sip balancer version
 * 
 * @author M. Ranganathan
 * @author baranowb 
 * @author <A HREF="mailto:jean.deruelle@gmail.com">Jean Deruelle</A> 
 *
 */
public class RouterImpl implements Router {

	private static NodeRegister register = null;

	public RouterImpl(SipStack sipStack, String proxyPort)
			throws Exception {
	}

	public static void setRegister(NodeRegister register) {
		RouterImpl.register = register;
	}

	public Hop getNextHop(Request request) throws SipException {
		String callID = ((CallID) request.getHeader(CallID.NAME)).getCallId();

		SIPNode node = null;
		Hop hop = null;
		
		String transport = ((ViaHeader)request.getHeader(ViaHeader.NAME)).getTransport().toLowerCase();

		String method = request.getMethod();

		if (method.equals(Request.INVITE) || method.equals(Request.SUBSCRIBE)) {
			node = register.stickSessionToNode(callID, null);
		} else if (method.equals(Request.BYE) || method.equals(Request.CANCEL)) {
			// We have to clean, other side wants to go BYE ;/
			node = register.getGluedNode(callID);
			if (node == null) {
				for (int i = 0; i < 5 && node == null; i++) {
					try {
						node = register.getNextNode();
					} catch (IndexOutOfBoundsException ioobe) {
					}
				}
			}
		} else {

			node = register.getGluedNode(callID);

			if (node == null)
				for (int i = 0; i < 5 && node == null; i++) {
					try {
						node = register.getNextNode();
					} catch (IndexOutOfBoundsException ioobe) {
					}
				}
		}
		Integer port = (Integer) node.getProperties().get(transport + "Port");
		if(port == null) {
			throw new RuntimeException("No port available for transport " + transport + " for node " + node);
		}
		hop = new HopImpl(node.getIp(), port, transport);

		System.out.println(this.getClass().getName()+".getNextHop() returning hop:"+hop );
		return hop;
	}

	public ListIterator<HopImpl> getNextHops(Request request) {

		SIPNode node = null;
		for (int i = 0; i < 5 && node == null; i++) {
			try {
				node = register.getNextNode();
			} catch (IndexOutOfBoundsException ioobe) {
			}
		}

		if (node == null) {
			return null;
		} else {
			String transport = ((ViaHeader)request.getHeader(ViaHeader.NAME)).getTransport().toLowerCase();
			LinkedList<HopImpl> retval = new LinkedList<HopImpl>();
			Integer port = (Integer) node.getProperties().get(transport + "Port");
			if(port == null) {
				throw new RuntimeException("No port available for transport " + transport + " for node " + node);
			}
			retval.add(new HopImpl(node.getIp(), port, transport));
			return retval.listIterator();
		}
	}

	public Hop getOutboundProxy() {
		// TODO Auto-generated method stub
		return null;
	}

}

package org.mobicents.tools.sip.balancer;

import gov.nist.javax.sip.header.CallID;
import gov.nist.javax.sip.stack.HopImpl;

import java.util.LinkedList;
import java.util.ListIterator;

import javax.sip.SipException;
import javax.sip.SipStack;
import javax.sip.address.Hop;
import javax.sip.address.Router;
import javax.sip.message.Request;

/**
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

		String method = request.getMethod();

		if (method.equals(Request.INVITE) || method.equals(Request.SUBSCRIBE)) {
			node = register.stickSessionToNode(callID);
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

		hop = new HopImpl(node.getIp(), node.getPort(), node.getTransports()[0]);

		System.out.println(this.getClass().getName()+".getNextHop() returning hop:"+hop );
		return hop;
	}

	public ListIterator getNextHops(Request request) {

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
			LinkedList retval = new LinkedList();
			retval.add(new HopImpl(node.getIp(), node.getPort(), node
					.getTransports()[0]));
			return retval.listIterator();
		}
	}

	public Hop getOutboundProxy() {
		// TODO Auto-generated method stub
		return null;
	}

}

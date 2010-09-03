package org.mobicents.tools.sip.balancer;

import gov.nist.javax.sip.header.SIPHeader;

import java.util.Collections;
import java.util.HashMap;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.sip.address.SipURI;
import javax.sip.header.FromHeader;
import javax.sip.header.ToHeader;
import javax.sip.message.Message;
import javax.sip.message.Request;

import org.jboss.netty.handler.codec.http.HttpRequest;

public class HeaderConsistentHashBalancerAlgorithm extends DefaultBalancerAlgorithm {
	
	protected String sipHeaderAffinityKey;
	protected String httpAffinityKey;
	
	// We will maintain a sorted list of the nodes so all SIP LBs will see them in the same order
	// no matter at what order the events arrived
	private SortedSet nodes = Collections.synchronizedSortedSet(new TreeSet<SIPNode>());
	
	// And we also keep a copy in the array because it is faster to query by index
	private Object[] nodesArray;
	
	private boolean nodesAreDirty = true;
	
	public HeaderConsistentHashBalancerAlgorithm() {
	}
	
	public HeaderConsistentHashBalancerAlgorithm(String headerName) {
		this.sipHeaderAffinityKey = headerName;
	}

	public SIPNode processExternalRequest(Request request) {
		Integer nodeIndex = hashHeader(request);
		if(nodeIndex<0) {
			return null;
		} else {
			BalancerContext balancerContext = getBalancerContext();
			if(nodesAreDirty) {
				synchronized(this) {
					nodes.clear();
					nodes.add(balancerContext.nodes);
					nodesArray = nodes.toArray(new Object[]{});
					nodesAreDirty = false;
				}
			}
			try {
				SIPNode node = (SIPNode) nodesArray[nodeIndex];
				return node;
			} catch (Exception e) {
				return null;
			}
		}
	}

	public synchronized void nodeAdded(SIPNode node) {
		nodes.add(node);
		nodesArray = nodes.toArray(new Object[]{});
		nodesAreDirty = false;
	}

	public synchronized void nodeRemoved(SIPNode node) {
		nodes.remove(node);
		nodesArray = nodes.toArray(new Object[]{});
		nodesAreDirty = false;
	}
	
	private void dumpNodes() {
		System.out.println("0----------------------------------------------------0");
		for(Object object : nodesArray) {
			SIPNode node = (SIPNode) object;
			System.out.println(node);
		}
	}
	
	private Integer hashHeader(Message message) {
		String headerValue = null;
		if(sipHeaderAffinityKey.equals("from.user")) {
			headerValue = ((SipURI)((FromHeader) message.getHeader(FromHeader.NAME))
					.getAddress().getURI()).getUser();
		} else if(sipHeaderAffinityKey.equals("to.user")) {
			headerValue = ((SipURI)((ToHeader) message.getHeader(ToHeader.NAME))
			.getAddress().getURI()).getUser();
		} else {
			headerValue = ((SIPHeader) message.getHeader(sipHeaderAffinityKey))
			.getValue();
		}

		if(nodes.size() == 0) throw new RuntimeException("No Application Servers registered. All servers are dead.");
		
		int nodeIndex = hashAffinityKeyword(headerValue);

		return nodeIndex;
		
	}
	
	public SIPNode processHttpRequest(HttpRequest request) {
		String affinityKeyword = getUrlParameters(request.getUri()).get(this.httpAffinityKey);
		if(affinityKeyword == null) {
			return super.processHttpRequest(request);
		}
		return (SIPNode) nodesArray[hashAffinityKeyword(affinityKeyword)];
	}
	
	protected int hashAffinityKeyword(String keyword) {
		int nodeIndex = Math.abs(keyword.hashCode()) % nodes.size();
		return nodeIndex;
	}

    HashMap<String,String> getUrlParameters(String url) {
    	HashMap<String,String> parameters = new HashMap<String, String>();
    	int start = url.lastIndexOf('?');
    	if(start>0 && url.length() > start +1) {
    		url = url.substring(start + 1);
    	} else {
    		return parameters;
    	}
    	String[] tokens = url.split("&");
    	for(String token : tokens) {
    		String[] params = token.split("=");
    		if(params.length<2) {
    			parameters.put(token, "");
    		} else {
    			parameters.put(params[0], params[1]);
    		}
    	}
    	return parameters;
    }

    public void init() {
    	this.httpAffinityKey = getProperties().getProperty("httpAffinityKey", "appsession");
    	this.sipHeaderAffinityKey = getProperties().getProperty("sipHeaderAffinityKey", "Call-ID");

    }
}

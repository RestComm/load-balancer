package org.mobicents.tools.sip.balancer;

import gov.nist.javax.sip.header.SIPHeader;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.sip.address.SipURI;
import javax.sip.header.FromHeader;
import javax.sip.header.RouteHeader;
import javax.sip.header.ToHeader;
import javax.sip.message.Message;
import javax.sip.message.Request;

import org.jboss.cache.Cache;
import org.jboss.cache.CacheFactory;
import org.jboss.cache.DefaultCacheFactory;
import org.jboss.cache.Fqn;
import org.jboss.cache.notifications.annotation.CacheListener;
import org.jboss.cache.notifications.annotation.NodeModified;
import org.jboss.cache.notifications.annotation.ViewChanged;
import org.jboss.cache.notifications.event.Event;
import org.jboss.cache.notifications.event.ViewChangedEvent;
import org.jboss.netty.handler.codec.http.HttpRequest;

/**
 * Persistent Consistent Hash algorithm - see http://docs.google.com/present/view?id=dc5jp5vx_89cxdvtxcm Example algorithms section
 * @author vralev
 *
 */
@CacheListener
public class PersistentConsistentHashBalancerAlgorithm extends DefaultBalancerAlgorithm {
	private static Logger logger = Logger.getLogger(PersistentConsistentHashBalancerAlgorithm.class.getCanonicalName());
	
	protected String sipHeaderAffinityKey;
	protected String httpAffinityKey;
	
	protected Cache cache;
	
	// And we also keep a copy in the array because it is faster to query by index
	private Object[] nodesArray = new Object[]{};
	
	private boolean nodesAreDirty = true;
	
	public PersistentConsistentHashBalancerAlgorithm() {
	}
	
	public PersistentConsistentHashBalancerAlgorithm(String headerName) {
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
					syncNodes();
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
	
	@NodeModified
	public void modified(Event event) {
		logger.fine(event.toString());
	}

	public synchronized void nodeAdded(SIPNode node) {
		addNode(node);
		syncNodes();
	}
	
	private void addNode(SIPNode node) {
		Fqn nodes = Fqn.fromString("/BALANCER/NODES");
		cache.put(nodes, node, "");
		dumpNodes();
	}

	public synchronized void nodeRemoved(SIPNode node) {
		dumpNodes();
	}
	
	private void dumpNodes() {
		logger.info("The following nodes are in cache right now:");
		for(Object object : nodesArray) {
			SIPNode node = (SIPNode) object;
			logger.info(node.toString() + " [ALIVE:" + isAlive(node) + "]");
		}
	}
	
	private boolean isAlive(SIPNode node) {
		if(getBalancerContext().nodes.contains(node)) return true;
		return false;
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

		if(nodesArray.length == 0) throw new RuntimeException("No Application Servers registered. All servers are dead.");
		
		int nodeIndex = hashAffinityKeyword(headerValue);
		
		if(isAlive((SIPNode)nodesArray[nodeIndex])) {
			return nodeIndex;
		} else {
			return -1;
		}
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
    
	public SIPNode processHttpRequest(HttpRequest request) {
		String affinityKeyword = getUrlParameters(request.getUri()).get(this.httpAffinityKey);
		if(affinityKeyword == null) {
			return super.processHttpRequest(request);
		}
		return (SIPNode) nodesArray[hashAffinityKeyword(affinityKeyword)];
	}
	
	protected int hashAffinityKeyword(String keyword) {
		int nodeIndex = Math.abs(keyword.hashCode()) % nodesArray.length;

		SIPNode computedNode = (SIPNode) nodesArray[nodeIndex];
		
		if(!isAlive(computedNode)) {
			// If the computed node is dead, find a new one
			for(int q = 0; q<nodesArray.length; q++) {
				nodeIndex = (nodeIndex + 1) % nodesArray.length;
				if(isAlive(((SIPNode)nodesArray[nodeIndex]))) {
					break;
				}
			}
		}
		return nodeIndex;
	}

	
	@ViewChanged
	public void viewChanged(ViewChangedEvent event) {
		logger.info(event.toString());
	}
	
	public void init() {
		CacheFactory cacheFactory = new DefaultCacheFactory();
		InputStream configurationInputStream = null;
		String configFile = getProperties().getProperty("persistentConsistentHashCacheConfiguration");
		if(configFile != null) {
			logger.info("Try to use cache configuration from " + configFile);
			try {
				configurationInputStream = new FileInputStream(configFile);
			} catch (FileNotFoundException e1) {
				logger.log(Level.SEVERE, "File not found", e1);
				throw new RuntimeException(e1);
			}
		} else {
			logger.info("Using default cache settings");
			configurationInputStream = this.getClass().getClassLoader().getResourceAsStream("META-INF/PHA-balancer-cache.xml");
			if(configurationInputStream == null) throw new RuntimeException("Problem loading resource META-INF/PHA-balancer-cache.xml");
		}

		Cache cache = cacheFactory.createCache(configurationInputStream);
		cache.addCacheListener(this);
		cache.create();
		cache.start();
		this.cache = cache;
		
		for (SIPNode node : getBalancerContext().nodes) {
			addNode(node);
		}
		syncNodes();

		this.httpAffinityKey = getProperties().getProperty("httpAffinityKey", "appsession");
		this.sipHeaderAffinityKey = getProperties().getProperty("sipHeaderAffinityKey", "Call-ID");
	}
	
	private void syncNodes() {
		Set nodes = cache.getKeys("/BALANCER/NODES");
		if(nodes != null) {
			ArrayList nodeList = new ArrayList();
			nodeList.addAll(nodes);
			Collections.sort(nodeList);
			this.nodesArray = nodeList.toArray();
		}
		dumpNodes();
	}
	
	public void configurationChanged() {
		logger.info("Configuration changed");
		this.httpAffinityKey = getProperties().getProperty("httpAffinityKey", "appsession");
		this.sipHeaderAffinityKey = getProperties().getProperty("sipHeaderAffinityKey", "Call-ID");
	}

}

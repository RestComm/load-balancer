package org.mobicents.tools.sip.balancer;

import java.util.Properties;

import javax.sip.SipProvider;
import javax.sip.message.Request;
import javax.sip.message.Response;

/**
 * The BalancerAlgortihm interface exposes the methods implemented by decision making algorithms
 * 
 * @author vralev
 *
 */
public interface BalancerAlgorithm {
	/**
	 * When a request comes it is passed to the algorithm. The algorithm must add the Route headers
	 * to the node or nodes where the request should go. The actual proxying is done at the LB.
	 * 
	 * Allowing the algorithm to add headers allows more flexibility for example when you need to
	 * pass some information to the AS or the application to make further LB decisions or multiprotocol
	 * cooperative load balancing. It is very little effort for great flexibility.
	 * 
	 * @param request
	 * @return
	 */
	SIPNode processRequest(SipProvider sipProvider, Request request);
	
	/**
	 * Allow algorithms to process responses
	 * 
	 * @param response
	 */
	void processResponse(SipProvider provider, Response response);
	
	/**
	 * Notifying the algorithm when a node is dead.
	 * 
	 * @param node
	 */
	void nodeRemoved(SIPNode node);
	
	/**
	 * Notify the algorithm when a node is added.
	 * @param node
	 */
	void nodeAdded(SIPNode node);
	
	/**
	 * Get the properties used to load the load balancer. This way you can read algorithm-specific settings
	 * from the main configuration file - the lb.properties.
	 * 
	 * @return
	 */
	Properties getProperties();
	
	/**
	 * Also allows to change the properties completely when it makes sense
	 * @param properties
	 */
	void setProperties(Properties properties);
	
	/**
	 * Get the balancer context, which exposes useful information such as the available AS nodes at the moment
	 * or the listening points if you need the local address.
	 * @return
	 */
	BalancerContext getBalancerContext();
	
	/**
	 * Move load from one node to another to follow mod_jk/mod_cluster
	 * @param fromJvmRoute
	 * @param toJvmRoute
	 */
	void jvmRouteSwitchover(String fromJvmRoute, String toJvmRoute);
	
	/**
	 * Lifecycle method. Notifies the algorithm when it's initialized with properties and balancer context.
	 */
	void init();
	
	/**
	 * Lifecucle method. Notifies the algorithm when it's being shut down.
	 */
	void stop();
	
	/**
	 * Assign callid to node
	 */
	void assignToNode(String id, SIPNode node);
}

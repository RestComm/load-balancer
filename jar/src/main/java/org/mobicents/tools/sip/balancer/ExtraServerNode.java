package org.mobicents.tools.sip.balancer;

/**
 * Instances of this class represent Application Server nodes that do not participate in the hearbeats
 * and are assumed to always be alive. For example these could be fallback servers or the IP of a fault tolerant
 * cluster gateway (load balancer).
 * 
 * Those are only needed to identify the direction of the call. If a request comes from such node we must indicate
 * this by using this instance. Otherwise it might be interpreted as a client node and requests coming from it will
 * be directed to the application servers once again.
 * 
 * @author vladimirralev
 *
 */
public class ExtraServerNode extends SIPNode {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public ExtraServerNode() {
		super(null,null);
	}
	
	public static ExtraServerNode extraServerNode = new ExtraServerNode();

}

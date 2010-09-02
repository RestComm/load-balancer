package org.mobicents.tools.sip.balancer;

/**
 * If your algorthm return an instance of this class the load balancer will not forward the request anywhere.
 * For instance this is useful if you detected a call in unrecoverable state such as failure before ACK, then
 * you would want to just drop the ACK.
 * 
 * @author vladimirralev
 *
 */
public class NullServerNode extends SIPNode {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	public NullServerNode() {
		super(null, null);
	}
	public static NullServerNode nullServerNode = new NullServerNode();
}

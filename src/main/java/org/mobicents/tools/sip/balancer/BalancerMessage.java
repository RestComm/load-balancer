package org.mobicents.tools.sip.balancer;

import java.io.Serializable;

/**
 * @deprecated
 * @author deruelle
 *
 */
public interface BalancerMessage extends Serializable {

	public BalancerMessageType getType();
	public Object getContent();
}

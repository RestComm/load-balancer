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

import java.io.Serializable;
import java.util.HashMap;

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
	
	public ExtraServerNode(String hostName, String ip){
		super(hostName, ip);
	}
	
	public static ExtraServerNode extraServerNode = new ExtraServerNode();

	public void setProperties(HashMap<String, Serializable> properties){
		super.getProperties().putAll(properties);
	}
}

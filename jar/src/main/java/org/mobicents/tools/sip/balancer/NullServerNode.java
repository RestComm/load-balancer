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

import org.mobicents.tools.heartbeat.impl.Node;

/**
 * If your algorthm return an instance of this class the load balancer will not forward the request anywhere.
 * For instance this is useful if you detected a call in unrecoverable state such as failure before ACK, then
 * you would want to just drop the ACK.
 * 
 * @author vladimirralev
 *
 */
public class NullServerNode extends Node {

	/**
	 * 
	 */
	public NullServerNode() {
		super(null, null);
	}
	public static NullServerNode nullServerNode = new NullServerNode();
}

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

/*
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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.mobicents.tools.heartbeat.api.Node;
import org.mobicents.tools.heartbeat.api.Protocol;

/**
 * @author <A HREF="mailto:yukinobu.imai@gmail.com">Yukinobu Imai</A> 
 *
 */
public class NodeTest {

	@Test
    public void testEquals() throws Exception {
        Node node1 = new Node("mobicents.org", "192.168.0.10");
        node1.getProperties().put(Protocol.UDP_PORT, "5060");
        Node node2 = node1;
        assertTrue(node1.equals(node2));
        
        node2 = new Node("mobicents.org", "192.168.0.20");
        node2.getProperties().put(Protocol.UDP_PORT, "5060");
        assertFalse(node1.equals(node2));
        
        node2 = new Node("mobicents.org", "192.168.0.10");
        node2.getProperties().put(Protocol.UDP_PORT, "5060");
        assertTrue(node1.equals(node2));
        
        node1.getProperties().put(Protocol.TCP_PORT, "5060");
        node2.getProperties().put(Protocol.TCP_PORT, "5060");
        assertTrue(node1.equals(node2));
        
        node1.getProperties().put(Protocol.TLS_PORT, "5061");
        assertFalse(node1.equals(node2));
        
        node2.getProperties().put(Protocol.TLS_PORT, "5061");
        node2.getProperties().put(Protocol.WS_PORT, "5062");
        assertFalse(node1.equals(node2));
        
    }
    
}
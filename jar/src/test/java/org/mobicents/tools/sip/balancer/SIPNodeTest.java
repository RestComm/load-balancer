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

import junit.framework.TestCase;

/**
 * @author <A HREF="mailto:yukinobu.imai@gmail.com">Yukinobu Imai</A> 
 *
 */
public class SIPNodeTest extends TestCase {

    /**
     * @param name
     */
    public SIPNodeTest(String name) {
       super(name);
    } 

    /* (non-Javadoc)
     * @see junit.framework.TestCase#setUp()
     */
    protected void setUp() throws Exception {
        super.setUp();
    }

    /* (non-Javadoc)
     * @see junit.framework.TestCase#tearDown()
     */
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void testEquals() throws Exception {
        SIPNode node1 = new SIPNode("mobicents.org", "192.168.0.10");
        SIPNode node2 = node1;
        assertTrue(node1.equals(node2));
        
        node2 = new SIPNode("mobicents.org", "192.168.0.20");
        assertFalse(node1.equals(node2));
        
        node2 = new SIPNode("mobicents.org", "192.168.0.10");
        assertTrue(node1.equals(node2));
        
        node1.getProperties().put("key", "value");
        node2.getProperties().put("key", "value");
        assertTrue(node1.equals(node2));
        
        node1.getProperties().put("key2", "value");
        assertFalse(node1.equals(node2));
        
        node2.getProperties().put("key2", "value");
        node2.getProperties().put("key3", "value");
        assertFalse(node1.equals(node2));
        
    }
    
}
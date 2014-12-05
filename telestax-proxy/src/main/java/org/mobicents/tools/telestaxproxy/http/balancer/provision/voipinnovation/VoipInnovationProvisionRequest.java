/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2013, Telestax Inc and individual contributors
 * by the @authors tag.
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
package org.mobicents.tools.telestaxproxy.http.balancer.provision.voipinnovation;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamAsAttribute;

/**
 * @author <a href="mailto:gvagenas@gmail.com">gvagenas</a>
 *
 */
@XmlRootElement(name="request")
@XmlAccessorType(XmlAccessType.FIELD)
@SuppressWarnings("unused")
public class VoipInnovationProvisionRequest {

    private Header header;

    @XStreamAlias("id")
    @XStreamAsAttribute
    private String id;
    private Body body;

    public String getId() {
        return this.id;
    }
    
    public String getEndpointGroup() {
        return this.body.item.endpointgroup;
    }
    
    public String getRequestType() {
        return this.body.requesttype;
    }
    
//    public String getProvider() {
//        return this.body.item.provider;
//    }
    
    private class Header {
        private String username;
        private String password;
    }
    
    private class Body {
        private String requesttype;
        private Item item;
    }
    
    private class Item {
        private String did;
        private String refid;
        private String endpointgroup;
//        private String provider;
        private String cnam;
    }
}

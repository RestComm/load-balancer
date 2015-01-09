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
package org.mobicents.tools.telestaxproxy.http.balancer.provision.bandwidth;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import com.thoughtworks.xstream.annotations.XStreamAlias;

/**
 * @author <a href="mailto:gvagenas@gmail.com">gvagenas</a>
 *
 */
@XmlRootElement(name="DisconnectTelephoneNumberOrder")
@XmlAccessorType(XmlAccessType.FIELD)
public class BandwidthReleaseRequest extends BandwidthRequest {

    // Post request : /accounts/<accountid>/disconnects
    //    <?xml version="1.0"?>
    //    <DisconnectTelephoneNumberOrder>
    //    <name>test disconnect order 4</name>
    //    <DisconnectTelephoneNumberOrderType>
    //    <TelephoneNumberList>
    //    <TelephoneNumber>9192755378</TelephoneNumber>
    //    <TelephoneNumber>9192755703</TelephoneNumber>
    //    </TelephoneNumberList>
    //    </DisconnectTelephoneNumberOrderType>
    //    </DisconnectTelephoneNumberOrder>

    private String siteId;

    public void setSiteId(String siteId) {
        this.siteId = siteId;
    }

    public String getSiteId() {
        return siteId;
    }

    @XStreamAlias("CustomerOrderId")
    private String customerOrderId;

    public String getCustomerOrderId() {
        return customerOrderId;
    }

    public void setCustomerOrderId(String customerOrderId) {
        this.customerOrderId = customerOrderId;
    }

    @XStreamAlias("DisconnectTelephoneNumberOrderType")
    private DisconnectTelephoneNumberOrderType disconnectTelephoneNumberOrderType;

    private class DisconnectTelephoneNumberOrderType {
        @XStreamAlias("TelephoneNumberList")
        private TelephoneNumberList telephoneNumberList;
    }

    private class TelephoneNumberList {
        @XStreamAlias("TelephoneNumber")
        private String telephoneNumber;
    }

}

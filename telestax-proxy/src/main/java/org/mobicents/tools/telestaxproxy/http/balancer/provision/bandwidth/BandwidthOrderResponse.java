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
import com.thoughtworks.xstream.annotations.XStreamAsAttribute;

/**
 * @author <a href="mailto:gvagenas@gmail.com">gvagenas</a>
 *
 */
@XmlRootElement(name="OrderResponse")
@XmlAccessorType(XmlAccessType.FIELD)
public class BandwidthOrderResponse extends BandwidthResponse {

//    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
//    <OrderResponse>
//      <Order>
//      <CustomerOrderId>i-54321</CustomerOrderId>
//      <OrderCreateDate>2015-01-07T17:01:17.478Z</OrderCreateDate>
//      <BackOrderRequested>false</BackOrderRequested>
//      <id>577edbd0-89e2-487c-80c1-c5ff3d9b0c55</id>
//      <ExistingTelephoneNumberOrderType>
//        <TelephoneNumberList>
//          <TelephoneNumber>2052355024</TelephoneNumber>
//        </TelephoneNumberList>
//      </ExistingTelephoneNumberOrderType>
//      <PartialAllowed>true</PartialAllowed>
//      <SiteId>1381</SiteId>
//      </Order>
//      <OrderStatus>RECEIVED</OrderStatus>
//    </OrderResponse>
    
    @XStreamAlias("Order")
    private Order order;
    
    @XStreamAlias("OrderStatus")
    private String orderStatus;
    
    public String getId() {
        return order.id;
    }
    
    public String getOrderStatus() {
        return orderStatus;
    }
    
    public String getSiteId() {
        return order.siteId;
    }

    public String getCustomerOrderId() {
        return order.customerOrderId;
    }
    
    public String getTelephoneNumber() {
        return order.existingTelephoneNumberOrderType.telephoneNumberList.telephoneNumber;
    }
    
    private class Order {
        @XStreamAlias("CustomerOrderId")
        private String customerOrderId;
        private String id;
        @XStreamAlias("SiteId")
        private String siteId;
        @XStreamAlias("ExistingTelephoneNumberOrderType")
        private ExistingTelephoneNumberOrderType existingTelephoneNumberOrderType;
    }
    
    private class ExistingTelephoneNumberOrderType {
        @XStreamAlias("TelephoneNumberList")
//        @XStreamAsAttribute
        private TelephoneNumberList telephoneNumberList;
    }
    
    private class TelephoneNumberList {
        @XStreamAlias("TelephoneNumber")
//        @XStreamAsAttribute
        private String telephoneNumber;
    }
    
}

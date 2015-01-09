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
@XmlRootElement(name = "DisconnectTelephoneNumberOrderResponse")
@XmlAccessorType(XmlAccessType.FIELD)
public class BandwidthReleaseResponse extends BandwidthResponse {

    //    <?xml version="1.0" encoding="UTF-8" standalone="yes" ?>
    //    <DisconnectTelephoneNumberOrderResponse>
    //      <orderRequest>
    //        <Name>Disconnect Order For Number: 2052355024</Name>
    //        <OrderCreateDate>2015-01-08T17:33:39.167Z</OrderCreateDate>
    //        <id>46d176be-d75c-44c9-92dd-c51996275a5d</id>
    //        <DisconnectTelephoneNumberOrderType>
    //          <TelephoneNumberList>
    //            <TelephoneNumber>2052355024</TelephoneNumber>
    //          </TelephoneNumberList>
    //          <DisconnectMode>normal</DisconnectMode>
    //        </DisconnectTelephoneNumberOrderType>
    //      </orderRequest>
    //      <OrderStatus>RECEIVED</OrderStatus>
    //    </DisconnectTelephoneNumberOrderResponse>


    @XStreamAlias("orderRequest")
    private OrderRequest orderRequest;

    @XStreamAlias("OrderStatus")
    private String orderStatus;

    private class OrderRequest {
        @XStreamAlias("Name")
        private String name;
        @XStreamAlias("OrderCreateDate")
        private String orderCreateDate;
        @XStreamAlias("id")
        private String id;
        @XStreamAlias("DisconnectTelephoneNumberOrderType")
        private DisconnectTelephoneNumberOrderType disconnectTelephoneNumberOrderType; 
    }
    
    private class DisconnectTelephoneNumberOrderType {
        @XStreamAlias("TelephoneNumberList")
        private TelephoneNumberList telephoneNumberList;
    }
    
    private class TelephoneNumberList {
        @XStreamAlias("TelephoneNumber")
        private String TelephoneNumber;
    }
    
    public String getOrderStatus() {
        return orderStatus;
    }
    
    public String getId() {
        return orderRequest.id;
    }
    
    public String getTelephoneNumber() {
        return orderRequest.disconnectTelephoneNumberOrderType.telephoneNumberList.TelephoneNumber;
    }
}
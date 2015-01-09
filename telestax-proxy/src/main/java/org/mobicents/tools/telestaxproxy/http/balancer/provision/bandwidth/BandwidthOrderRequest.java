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
@XmlRootElement(name="Order")
@XmlAccessorType(XmlAccessType.FIELD)
public class BandwidthOrderRequest extends BandwidthRequest {

//    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
//    <Order>
//      <BackOrderRequested>false</BackOrderRequested>
//      <Name>Order For Number: 2052355024</Name>
//      <SiteId>1381</SiteId>
//      <PartialAllowed>false</PartialAllowed>
//      <ExistingTelephoneNumberOrderType>
//        <TelephoneNumberList>
//          <TelephoneNumber>2052355024</TelephoneNumber>
//        </TelephoneNumberList>
//      </ExistingTelephoneNumberOrderType>
//    </Order>
    
//    <Order>
//    <Name>Available Telephone Number order</Name>
//    <SiteId>385</SiteId>
//    <CustomerOrderId>123456789</CustomerOrderId>
//    <ExistingTelephoneNumberOrderType>
//    <TelephoneNumberList>
//    <TelephoneNumber>9193752369</TelephoneNumber>
//    <TelephoneNumber>9193752720</TelephoneNumber>
//    <TelephoneNumber>9193752648</TelephoneNumber>
//    </TelephoneNumberList>
//    </ExistingTelephoneNumberOrderType>
//    </Order>
    
    @XStreamAlias("CustomerOrderId")
    private String customerOrderId;
    
    public String getCustomerOrderId() {
        return customerOrderId;
    }
    
    public void setCustomerOrderId(String customerOrderId) {
        this.customerOrderId = customerOrderId;
    }
    
    @XStreamAlias("SiteId")
//    @XStreamAsAttribute    
    private String siteId;
    
    public String getSiteId() {
        return siteId;
    }
    
    public void setSiteId(String siteId) {
        this.siteId = siteId;
    }
    
    public String getTelephoneNumberList() {
        return existingTelephoneNumberOrderType.telephoneNumberList.telephoneNumber;
    }
    
    @XStreamAlias("ExistingTelephoneNumberOrderType")
//    @XStreamAsAttribute
    private ExistingTelephoneNumberOrderType existingTelephoneNumberOrderType;
    
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
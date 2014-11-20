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
package org.mobicents.tools.telestaxproxy.sip.balancer.entities;

import java.util.Date;
import java.util.List;

import org.mobicents.tools.telestaxproxy.http.balancer.provision.common.ProvisionProvider;


/**
 * @author <a href="mailto:gvagenas@gmail.com">gvagenas</a>
 *
 */
public class RestcommInstance {
    
    private String id;
    private ProvisionProvider provisionProvider;
    private String publicIpAddress;
    private String udpInterface;
    private String tcpInterface;
    private String tlsInterface;
    private String wsInterface;
    private Date dateCreated;
    List<String> addresses;

    public RestcommInstance() {
    }
    
    public RestcommInstance(final String restcommInstanceId, final List<String> addresses) {
        this.id = restcommInstanceId;
        prepareOutboundInterfaces(addresses);
        dateCreated = new Date();
        this.addresses = addresses;
    }
    
    public RestcommInstance(final String restcommInstanceId, final String provisionProvider, final List<String> addresses, final String publicIpAddress) {
        this.publicIpAddress = publicIpAddress;
        this.id = restcommInstanceId;
        if (provisionProvider.toLowerCase().contains("voipinnovations")) {
            this.setProvisionProvider(ProvisionProvider.VOIPINNOVATIONS);
        } else if (provisionProvider.toLowerCase().contains("Bandwidth")) {
            this.setProvisionProvider(ProvisionProvider.BANDWIDTH);
        }
        prepareOutboundInterfaces(addresses);
        dateCreated = new Date();
        this.addresses = addresses;
    }

    private void prepareOutboundInterfaces(List<String> addresses) {
        //Addresses from Restcomm will be: HOST:PORT:TRANSPORT
        for (String address: addresses) {
            String[] elements = address.split(":");
            String transport = elements[2];
            if(transport.equalsIgnoreCase("UDP")){
                udpInterface = elements[0]+":"+elements[1];
            } else if (transport.equalsIgnoreCase("TCP")) {
                tcpInterface = elements[0]+":"+elements[1];
            } else if (transport.equalsIgnoreCase("TLS")) {
                tlsInterface = elements[0]+":"+elements[1];
            } else if (transport.equalsIgnoreCase("WS")) {
                wsInterface = elements[0]+":"+elements[1];
            }
        }
    }

    public String getId() {
        return id;
    }
    
    public ProvisionProvider getProvisionProvider() {
        return provisionProvider;
    }

    public void setProvisionProvider(ProvisionProvider provisionProvider) {
        this.provisionProvider = provisionProvider;
    }

    public String getPublicIpAddress() {
        return publicIpAddress;
    }
    
    public void setPublicIpAddress() {
        this.publicIpAddress = publicIpAddress;
    }
    
    public String getUdpInterface() {
        return udpInterface;
    }

    public void setUdpInterface(String udpInterface) {
        this.udpInterface = udpInterface;
    }

    public String getTcpInterface() {
        return tcpInterface;
    }

    public void setTcpInterface(String tcpInterface) {
        this.tcpInterface = tcpInterface;
    }

    public String getTlsInterface() {
        return tlsInterface;
    }

    public void setTlsInterface(String tlsInterface) {
        this.tlsInterface = tlsInterface;
    }

    public String getWsInterface() {
        return wsInterface;
    }

    public void setWsInterface(String wsInterface) {
        this.wsInterface = wsInterface;
    }

    public String getAddressForTransport(String transport) {
        String address = null;
        if(transport.equalsIgnoreCase("UDP")){
            address = udpInterface;
        } else if (transport.equalsIgnoreCase("TCP")) {
            address = tcpInterface;
        } else if (transport.equalsIgnoreCase("TLS")) {
            address = tlsInterface;
        } else if (transport.equalsIgnoreCase("WS")) {
            address = wsInterface;
        }
        return address;
    }

    public Date getDateCreated() {
        return dateCreated;
    }  

    @Override
    public String toString() {
        return "<Restcomm instance id: "+id+" | publicIpAddress: "+publicIpAddress+" | interfaces: "+addresses+" | Date created: "+dateCreated+">";
    }
}

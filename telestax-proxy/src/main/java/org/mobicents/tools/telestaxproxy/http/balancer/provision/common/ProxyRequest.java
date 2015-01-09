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
package org.mobicents.tools.telestaxproxy.http.balancer.provision.common;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.mobicents.tools.telestaxproxy.http.balancer.provision.bandwidth.BandwidthRequest;
import org.mobicents.tools.telestaxproxy.http.balancer.provision.voipinnovation.VoipInnovationProvisionRequest;

/**
 * Includes the HTTP Request and the Provision Request object after been parsed from the XML body
 * It is used for the matching the Response to the Request and store any useful information to the associated restcomm instance.
 * @author <a href="mailto:gvagenas@gmail.com">gvagenas</a>
 *
 */
public class ProxyRequest {

    private final ChannelHandlerContext context;
    private final MessageEvent event;
    private final HttpRequest request;
    private VoipInnovationProvisionRequest viProvisionRequest;
    private BandwidthRequest bwProvisionRequest;

    public ProxyRequest(final ChannelHandlerContext context, final MessageEvent event, final HttpRequest request, final VoipInnovationProvisionRequest viProvisionRequest) {
        this.context = context;
        this.event = event;
        this.request = request;
        this.viProvisionRequest = viProvisionRequest;
    }
    
    public ProxyRequest(final ChannelHandlerContext context, final MessageEvent event, final HttpRequest request, final BandwidthRequest bwProvisionRequest) {
        this.context = context;
        this.event = event;
        this.request = request;
        this.bwProvisionRequest = bwProvisionRequest;
    }

    public ChannelHandlerContext getContext() {
        return context;
    }

    public MessageEvent getEvent() {
        return event;
    }

    public HttpRequest getRequest() {
        return request;
    }

    public VoipInnovationProvisionRequest getProvisionRequest() {
        return viProvisionRequest;
    }
}

/*
 * JBoss, Home of Professional Open Source
 * Copyright 2015-2016, Red Hat, Inc. and individual contributors
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

package org.mobicents.tools.smpp.balancer.impl;

import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import javax.net.ssl.SSLEngine;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.handler.ssl.SslHandler;
import org.mobicents.tools.smpp.balancer.api.LbServerListener;
import org.mobicents.tools.smpp.balancer.core.BalancerDispatcher;
import org.mobicents.tools.smpp.balancer.core.BalancerServer;

import com.cloudhopper.smpp.channel.SmppChannelConstants;
import com.cloudhopper.smpp.channel.SmppSessionPduDecoder;
import com.cloudhopper.smpp.ssl.SslConfiguration;
import com.cloudhopper.smpp.ssl.SslContextFactory;
import com.cloudhopper.smpp.transcoder.DefaultPduTranscoder;
import com.cloudhopper.smpp.transcoder.DefaultPduTranscoderContext;

/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */

public class ServerChannelConnector extends SimpleChannelUpstreamHandler {

    private ChannelGroup channels;
    private BalancerServer server;
    private LbServerListener lbServerListener;
    private Properties properties;
    private ScheduledExecutorService monitorExecutor  = Executors.newScheduledThreadPool(16);

    public ServerChannelConnector(ChannelGroup channels, BalancerServer smppServer, Properties properties) 
    {
        this.channels = channels;
        this.server = smppServer;
        this.lbServerListener = new BalancerDispatcher(properties, monitorExecutor);
        this.properties = properties;
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception 
    {
 
        Channel channel = e.getChannel();
        channels.add(channel);       

        if (server.getConfiguration().isUseSsl()) 
        {
		    SslConfiguration sslConfig = server.getConfiguration().getSslConfiguration();
		    if (sslConfig == null) throw new IllegalStateException("sslConfiguration must be set");
		    SslContextFactory factory = new SslContextFactory(sslConfig);
		    SSLEngine sslEngine = factory.newSslEngine();
		    sslEngine.setUseClientMode(false);
		    channel.getPipeline().addLast(SmppChannelConstants.PIPELINE_SESSION_SSL_NAME, new SslHandler(sslEngine));
        }

        channel.getPipeline().addLast(SmppChannelConstants.PIPELINE_SESSION_PDU_DECODER_NAME, new SmppSessionPduDecoder(new DefaultPduTranscoder(new DefaultPduTranscoderContext())));
        ServerConnectionImpl serverConnectionImpl = new ServerConnectionImpl(server.nextSessionId(),channel,lbServerListener, properties, monitorExecutor);
        channel.getPipeline().addLast(SmppChannelConstants.PIPELINE_SESSION_WRAPPER_NAME, new ServerConnectionHandlerImpl(serverConnectionImpl));
      }

    @Override
    public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception 
    {
    	channels.remove(e.getChannel());
    	this.server.getCounters().incrementChannelDisconnectsAndGet();
    }
}

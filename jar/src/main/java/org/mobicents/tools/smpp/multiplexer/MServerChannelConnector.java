/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2015, Telestax Inc and individual contributors
 * by the @authors tag.
 *
 * This program is free software: you can redistribute it and/or modify
 * under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */

package org.mobicents.tools.smpp.multiplexer;

import java.util.concurrent.ScheduledExecutorService;

import javax.net.ssl.SSLEngine;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.handler.ssl.SslHandler;
import org.mobicents.tools.sip.balancer.BalancerRunner;

import com.cloudhopper.smpp.SmppServerConfiguration;
import com.cloudhopper.smpp.channel.SmppChannelConstants;
import com.cloudhopper.smpp.channel.SmppSessionPduDecoder;
import com.cloudhopper.smpp.ssl.SslConfiguration;
import com.cloudhopper.smpp.ssl.SslContextFactory;
import com.cloudhopper.smpp.transcoder.DefaultPduTranscoder;
import com.cloudhopper.smpp.transcoder.DefaultPduTranscoderContext;

/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */

public class MServerChannelConnector extends SimpleChannelUpstreamHandler {

    private ChannelGroup channels;
    private MServer server;
    private MBalancerDispatcher lbServerListener;
    private BalancerRunner balancerRunner;
    private ScheduledExecutorService monitorExecutor;
    private SmppServerConfiguration configuration;
    
    public MServerChannelConnector(ChannelGroup channels, MServer smppServer, SmppServerConfiguration configuration, BalancerRunner balancerRunner, 
    		MBalancerDispatcher lbServerListener,ScheduledExecutorService monitorExecutor) 
    {
        this.channels = channels;
        this.server = smppServer;
        this.lbServerListener = lbServerListener;
        this.balancerRunner = balancerRunner;
        this.monitorExecutor = monitorExecutor;
        this.configuration=configuration;
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception 
    {
        Channel channel = e.getChannel();
        channels.add(channel);       
        if (configuration.isUseSsl()) 
        {
		    SslConfiguration sslConfig = configuration.getSslConfiguration();
		    if (sslConfig == null) throw new IllegalStateException("sslConfiguration must be set");
		    SslContextFactory factory = new SslContextFactory(sslConfig);
		    SSLEngine sslEngine = factory.newSslEngine();
		    sslEngine.setUseClientMode(false);
		    channel.getPipeline().addLast(SmppChannelConstants.PIPELINE_SESSION_SSL_NAME, new SslHandler(sslEngine));
        }

        channel.getPipeline().addLast(SmppChannelConstants.PIPELINE_SESSION_PDU_DECODER_NAME, new SmppSessionPduDecoder(new DefaultPduTranscoder(new DefaultPduTranscoderContext())));
        MServerConnectionImpl serverConnectionImpl = new MServerConnectionImpl(server.nextSessionId(),channel,lbServerListener, balancerRunner, monitorExecutor, configuration.isUseSsl());
        channel.getPipeline().addLast(SmppChannelConstants.PIPELINE_SESSION_WRAPPER_NAME, new MServerConnectionHandlerImpl(serverConnectionImpl));
      }

    @Override
    public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception 
    {
    	channels.remove(e.getChannel());
    	this.server.getCounters().incrementChannelDisconnectsAndGet();
    }
}

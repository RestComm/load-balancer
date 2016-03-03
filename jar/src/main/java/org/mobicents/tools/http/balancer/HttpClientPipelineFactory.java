/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2013, Telestax Inc and individual contributors
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

package org.mobicents.tools.http.balancer;

import static org.jboss.netty.channel.Channels.*;

import javax.net.ssl.SSLEngine;

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpRequestEncoder;
import org.jboss.netty.handler.codec.http.HttpResponseDecoder;
import org.jboss.netty.handler.ssl.SslHandler;
import org.mobicents.tools.sip.balancer.BalancerRunner;

import com.cloudhopper.smpp.ssl.SslConfiguration;
import com.cloudhopper.smpp.ssl.SslContextFactory;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Andy Taylor (andy.taylor@jboss.org)
 * @author Trustin Lee (trustin@gmail.com)
 * @author Vladimir Ralev (vladimir.ralev@jboss.org)
 * @author Jean Deruelle (jean.deruelle@telestax.com)
 * 
 */
public class HttpClientPipelineFactory implements ChannelPipelineFactory {
    int maxContentLength = 1048576;
    BalancerRunner balancerRunner;
    
    public HttpClientPipelineFactory(BalancerRunner balancerRunner, int maxContentLength) {
        this.balancerRunner = balancerRunner;
        this.maxContentLength = maxContentLength;
    }

    public ChannelPipeline getPipeline() throws Exception {
        // Create a default pipeline implementation.
        ChannelPipeline pipeline = pipeline();
        String isRemoteServerSsl = balancerRunner.balancerContext.properties.getProperty("isRemoteServerSsl","false");
        pipeline.addLast("decoder", new HttpResponseDecoder());
        // Remove the following line if you don't want automatic content decompression.
        //pipeline.addLast("inflater", new HttpContentDecompressor()); 
        pipeline.addLast("encoder", new HttpRequestEncoder());
        // http://code.google.com/p/commscale/issues/detail?id=5 support for HttpChunks, 
        // https://telestax.atlassian.net/browse/LB-8 if commented accessing the RestComm Management console fails, so making the maxContentLength Configurable
        pipeline.addLast("aggregator", new HttpChunkAggregator(maxContentLength));
        pipeline.addLast("handler", new HttpResponseHandler(balancerRunner));
        
        if(Boolean.parseBoolean(isRemoteServerSsl)){
        	SslConfiguration sslConfig = new SslConfiguration();
   	     	sslConfig.setTrustAll(true);
   	     	sslConfig.setValidateCerts(true);
   	     	sslConfig.setValidatePeerCerts(true);
        	SslContextFactory factory = new SslContextFactory(sslConfig);
    	    SSLEngine sslEngine = factory.newSslEngine();
    	    sslEngine.setUseClientMode(true);
            pipeline.addFirst("ssl", new SslHandler(sslEngine));
        }
        
        return pipeline;
    }

    public void setMaxContentLength(int maxContentLength) {
	this.maxContentLength = maxContentLength;
    }
}

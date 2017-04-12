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

package org.mobicents.tools.http.balancer;

import static org.jboss.netty.channel.Channels.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLEngine;

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.jboss.netty.handler.ssl.SslHandler;

import com.cloudhopper.smpp.ssl.SslConfiguration;
import com.cloudhopper.smpp.ssl.SslContextFactory;

/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */

public class TestHttpServerPipelineFactory implements ChannelPipelineFactory 
{
	private int maxContentLength = 1048576;
    private Boolean terminateTLSTraffic;
    private AtomicInteger requestCount;
    private List <String> requests;
    
    public TestHttpServerPipelineFactory(Boolean terminateTLSTraffic, AtomicInteger requestCount,List <String> requests) 
    {
        this.terminateTLSTraffic = terminateTLSTraffic;
        this.requestCount = requestCount;
        this.requests = requests;
    }

    public ChannelPipeline getPipeline() throws Exception 
    {
    	ChannelPipeline pipeline = pipeline();
        if(!terminateTLSTraffic)
        {
        	SslConfiguration sslConfig = new SslConfiguration();
	        sslConfig.setKeyStorePath(TestHttpServerPipelineFactory.class.getClassLoader().getResource("keystore").getFile());
	        sslConfig.setKeyStorePassword("123456");
	        sslConfig.setTrustStorePath(TestHttpServerPipelineFactory.class.getClassLoader().getResource("keystore").getFile());
	        sslConfig.setTrustStorePassword("123456");
        	SslContextFactory factory = new SslContextFactory(sslConfig);
    	    SSLEngine sslEngine = factory.newSslEngine();
    	    sslEngine.setUseClientMode(false);
            pipeline.addLast("ssl", new SslHandler(sslEngine));
        }
        pipeline.addLast("decoder", new HttpRequestDecoder());
        // http://code.google.com/p/commscale/issues/detail?id=5 support for HttpChunks
	// https://telestax.atlassian.net/browse/LB-8 if commented accessing the RestComm Management console fails, so making the maxContentLength Configurable
        pipeline.addLast("aggregator", new HttpChunkAggregator(maxContentLength));
        pipeline.addLast("encoder", new HttpResponseEncoder());
        // Remove the following line if you don't want automatic content compression.
        //pipeline.addLast("deflater", new HttpContentCompressor());
        pipeline.addLast("handler", new HttpServerRequestHandler(requestCount,requests));
        

        return pipeline;
    }
}

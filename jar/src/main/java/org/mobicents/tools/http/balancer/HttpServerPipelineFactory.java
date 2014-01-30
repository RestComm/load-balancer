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

import static org.jboss.netty.channel.Channels.pipeline;

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.mobicents.tools.sip.balancer.BalancerRunner;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Andy Taylor (andy.taylor@jboss.org)
 * @author Trustin Lee (trustin@gmail.com)
 * @author Vladimir Ralev (vladimir.ralev@jboss.org)
 * @author Jean Deruelle (jean.deruelle@telestax.com)
 *
 * @version $Rev: 1868 $, $Date: 2009-11-03 01:48:39 -0500 (Tue, 03 Nov 2009) $
 */
public class HttpServerPipelineFactory implements ChannelPipelineFactory {
	BalancerRunner balancerRunner;

    int maxContentLength = 1048576;

	public HttpServerPipelineFactory(BalancerRunner balancerRunner, int maxContentLength) {
		this.balancerRunner = balancerRunner;
		this.maxContentLength = maxContentLength;
	}

    public HttpClientPipelineFactory(int maxContentLength) {
	this.maxContentLength = maxContentLength;
    }

    public ChannelPipeline getPipeline() throws Exception {
        // Create a default pipeline implementation.
        ChannelPipeline pipeline = pipeline();

        // Uncomment the following line if you want HTTPS
        //SSLEngine engine = SecureChatSslContextFactory.getServerContext().createSSLEngine();
        //engine.setUseClientMode(false);
        //pipeline.addLast("ssl", new SslHandler(engine));

        pipeline.addLast("decoder", new HttpRequestDecoder());
        // http://code.google.com/p/commscale/issues/detail?id=5 support for HttpChunks
	// https://telestax.atlassian.net/browse/LB-8 if commented accessing the RestComm Management console fails, so making the maxContentLength Configurable
        pipeline.addLast("aggregator", new HttpChunkAggregator(maxContentLength));
        pipeline.addLast("encoder", new HttpResponseEncoder());
        // Remove the following line if you don't want automatic content compression.
        //pipeline.addLast("deflater", new HttpContentCompressor());
        pipeline.addLast("handler", new HttpRequestHandler(balancerRunner));
        return pipeline;
    }
}

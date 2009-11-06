package org.mobicents.tools.http.balancer;

import java.net.InetSocketAddress;
import java.util.logging.Logger;

import org.mobicents.tools.sip.balancer.BalancerContext;

public class HttpBalancerForwarder {
	private static final Logger logger = Logger.getLogger(HttpBalancerForwarder.class.getCanonicalName());
	public void start() {
		Integer httpPort = 2222;
		if(BalancerContext.balancerContext.properties != null) {
			String httpPortString = BalancerContext.balancerContext.properties.getProperty("httpPort", "2080");
			httpPort = Integer.parseInt(httpPortString);
		}
		logger.info("HTTP LB listening on port " + httpPort);
		HttpChannelAssocialtions.serverBootstrap.setPipelineFactory(new HttpServerPipelineFactory());
		HttpChannelAssocialtions.serverBootstrap.bind(new InetSocketAddress(httpPort));
        HttpChannelAssocialtions.inboundBootstrap.setPipelineFactory(new HttpClientPipelineFactory());
	}
	
	public void stop() {
		HttpChannelAssocialtions.serverBootstrap.releaseExternalResources();
		HttpChannelAssocialtions.inboundBootstrap.releaseExternalResources();
	}
}

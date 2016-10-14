package org.mobicents.tools.smpp.multiplexer;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.log4j.Logger;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.ChannelException;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.channel.socket.oio.OioServerSocketChannelFactory;
import org.mobicents.tools.sip.balancer.BalancerRunner;
import org.mobicents.tools.smpp.balancer.core.BalancerServer;

import com.cloudhopper.smpp.SmppServerConfiguration;
import com.cloudhopper.smpp.channel.SmppChannelConstants;
import com.cloudhopper.smpp.impl.DefaultSmppServerCounters;

public class MServer 
{
		private static final Logger logger = Logger.getLogger(BalancerServer.class);

	    private final ChannelGroup channels;
	    private final MServerChannelConnector serverConnector;
	    private SmppServerConfiguration regularConfiguration;
	    private ServerBootstrap regularBootstrap;
	    private MServerChannelConnector securedConnector;
	    private SmppServerConfiguration securedConfiguration;
	    private ServerBootstrap securedBootstrap;
	    private ExecutorService bossThreadPool;
	    private ChannelFactory channelFactory;
	    private final AtomicLong sessionIdSequence;
	    private DefaultSmppServerCounters counters;
	     
		public MServer (SmppServerConfiguration regularConfiguration,SmppServerConfiguration securedConfiguration,ExecutorService executor, 
				BalancerRunner balancerRunner, MBalancerDispatcher lbServerListener, ScheduledExecutorService monitorExecutor) {
	        this.regularConfiguration = regularConfiguration;
	        this.securedConfiguration=securedConfiguration;
	        this.channels = new DefaultChannelGroup();
	        this.bossThreadPool = Executors.newCachedThreadPool();
	        if (regularConfiguration.isNonBlockingSocketsEnabled()) 
	            this.channelFactory = new NioServerSocketChannelFactory(this.bossThreadPool, executor, regularConfiguration.getMaxConnectionSize());
	        else 
	            this.channelFactory = new OioServerSocketChannelFactory(this.bossThreadPool, executor);
	        
	        this.regularBootstrap = new ServerBootstrap(this.channelFactory);
	        this.regularBootstrap.setOption("reuseAddress", regularConfiguration.isReuseAddress());
	        this.serverConnector = new MServerChannelConnector(channels, this, regularConfiguration, balancerRunner, lbServerListener, monitorExecutor);
	        this.regularBootstrap.getPipeline().addLast(SmppChannelConstants.PIPELINE_SERVER_CONNECTOR_NAME, this.serverConnector);
	        
	        if(this.securedConfiguration!=null)
	        {
	        	this.securedBootstrap = new ServerBootstrap(this.channelFactory);
	            this.securedBootstrap.setOption("reuseAddress", securedConfiguration.isReuseAddress());
	            this.securedConnector = new MServerChannelConnector(channels, this, securedConfiguration, balancerRunner, lbServerListener, monitorExecutor);
	            this.securedBootstrap.getPipeline().addLast(SmppChannelConstants.PIPELINE_SERVER_CONNECTOR_NAME, this.securedConnector);            
	        }
	        
	        this.sessionIdSequence = new AtomicLong(0);        
	        this.counters = new DefaultSmppServerCounters();
	    }	
		
		public DefaultSmppServerCounters getCounters() 
		{
			return counters;
		}
		/**
		*Start load balancer server
		*/
		public void start() 
		{
		        try 
		        {
		            this.regularBootstrap.bind(new InetSocketAddress(regularConfiguration.getHost(), regularConfiguration.getPort()));
		            if(logger.isInfoEnabled()) {
		            logger.info(regularConfiguration.getName() + " started at " + regularConfiguration.getHost() + " : " + regularConfiguration.getPort());
		            }
		            if(this.securedConfiguration!=null)
		            {
		            	this.securedBootstrap.bind(new InetSocketAddress(securedConfiguration.getHost(), securedConfiguration.getPort()));	
		            	if(logger.isInfoEnabled()) {
		            	logger.info(securedConfiguration.getName() + " uses port : " + securedConfiguration.getPort() + " for TLS clients.");
		            	}
		            }
		        } 
		        catch (ChannelException e) 
		        {
		        	logger.error("Smpp Channel Exception:", e);
		        }
		}
		/**
		*Generate id for sessions(clients)
		*/
	    public Long nextSessionId() 
	    {
	    	this.sessionIdSequence.compareAndSet(Long.MAX_VALUE, 0);
	        return this.sessionIdSequence.getAndIncrement();
	    }
	    /**
		*Stop load balancer server
		*/
		public void stop() {
			if (this.channels.size() > 0) 
			{
	            logger.info(regularConfiguration.getName() + " currently has [" + this.channels.size() + "] open child channel(s) that will be closed as part of stop()");
	        }
			this.channelFactory.shutdown();
			this.channels.close().awaitUninterruptibly();
			this.regularBootstrap.shutdown();
			if(this.securedBootstrap!=null)
			this.securedBootstrap.shutdown();
	        logger.info(regularConfiguration.getName() + " stopped at " + regularConfiguration.getHost());
		}
}

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

package org.mobicents.tools.smpp.balancer.core;

import java.net.InetSocketAddress;
import java.util.Properties;
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
import org.mobicents.tools.smpp.balancer.impl.ServerChannelConnector;

import com.cloudhopper.smpp.SmppServerConfiguration;
import com.cloudhopper.smpp.channel.SmppChannelConstants;
import com.cloudhopper.smpp.impl.DefaultSmppServerCounters;

/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */

public class BalancerServer{

	private static final Logger logger = Logger.getLogger(BalancerServer.class);

    private final ChannelGroup channels;
    private final ServerChannelConnector serverConnector;
    private final SmppServerConfiguration configuration;
	private ExecutorService bossThreadPool;
    private ChannelFactory channelFactory;
    private ServerBootstrap serverBootstrap;
    private final AtomicLong sessionIdSequence;
    private DefaultSmppServerCounters counters;
     
	public BalancerServer (final SmppServerConfiguration configuration,ExecutorService executor, Properties properties, BalancerDispatcher lbServerListener, ScheduledExecutorService monitorExecutor) {
        this.configuration = configuration;
        this.channels = new DefaultChannelGroup();
        this.bossThreadPool = Executors.newCachedThreadPool();
        if (configuration.isNonBlockingSocketsEnabled()) 
            this.channelFactory = new NioServerSocketChannelFactory(this.bossThreadPool, executor, configuration.getMaxConnectionSize());
        else 
            this.channelFactory = new OioServerSocketChannelFactory(this.bossThreadPool, executor);
        
        this.serverBootstrap = new ServerBootstrap(this.channelFactory);
        this.serverBootstrap.setOption("reuseAddress", configuration.isReuseAddress());
        this.serverConnector = new ServerChannelConnector(channels, this, properties, lbServerListener, monitorExecutor);
        this.serverBootstrap.getPipeline().addLast(SmppChannelConstants.PIPELINE_SERVER_CONNECTOR_NAME, this.serverConnector);
        this.sessionIdSequence = new AtomicLong(0);        
        this.counters = new DefaultSmppServerCounters();
    }
	public SmppServerConfiguration getConfiguration()
	{
		return configuration;
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
	            this.serverBootstrap.bind(new InetSocketAddress(configuration.getHost(), configuration.getPort()));	            
	            logger.info(configuration.getName() + " started at " + configuration.getHost() + " : " + configuration.getPort());
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
	public void stop() {
		if (this.channels.size() > 0) 
		{
            logger.info(configuration.getName() + " currently has [" + this.channels.size() + "] open child channel(s) that will be closed as part of stop()");
        }
        this.channels.close().awaitUninterruptibly();
        
        logger.info(configuration.getName() + " stopped at " + configuration.getHost()+" : " + configuration.getPort());
		
	}
}
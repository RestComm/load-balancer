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

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;

import org.mobicents.tools.sip.balancer.BalancerRunner;

import com.cloudhopper.smpp.SmppServerConfiguration;
import com.cloudhopper.smpp.ssl.SslConfiguration;

/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */

public class SmppBalancerRunner {
	
	private BalancerDispatcher balancerDispatcher;
	private ThreadPoolExecutor executor = (ThreadPoolExecutor)Executors.newCachedThreadPool();
	private ScheduledExecutorService monitorExecutor  = Executors.newScheduledThreadPool(4);
	private BalancerServer smppLbServer;
	private BalancerRunner balancerRunner;

	/**
	 * Start load balancer
	 * @param balancerRunner
	 */
	public SmppBalancerRunner(BalancerRunner balancerRunner)
	{
		this.balancerRunner = balancerRunner;
	}
	public void start()
	{
        SmppServerConfiguration regularConfiguration = new SmppServerConfiguration();
        regularConfiguration.setName(balancerRunner.balancerContext.properties.getProperty("smppName"));
        regularConfiguration.setHost(balancerRunner.balancerContext.properties.getProperty("smppHost"));
        regularConfiguration.setPort(Integer.parseInt(balancerRunner.balancerContext.properties.getProperty("smppPort")));
        regularConfiguration.setMaxConnectionSize(Integer.parseInt(balancerRunner.balancerContext.properties.getProperty("maxConnectionSize")));
        regularConfiguration.setNonBlockingSocketsEnabled(Boolean.parseBoolean(balancerRunner.balancerContext.properties.getProperty("nonBlockingSocketsEnabled")));
        regularConfiguration.setDefaultSessionCountersEnabled(Boolean.parseBoolean(balancerRunner.balancerContext.properties.getProperty("defaultSessionCountersEnabled")));
        regularConfiguration.setUseSsl(false);                
        
        SmppServerConfiguration securedConfiguration = null;
        if(balancerRunner.balancerContext.properties.getProperty("smppSslPort")!=null)
        {
        	securedConfiguration = new SmppServerConfiguration();
        	securedConfiguration.setName(balancerRunner.balancerContext.properties.getProperty("smppName"));
        	securedConfiguration.setHost(balancerRunner.balancerContext.properties.getProperty("smppHost"));
	        securedConfiguration.setPort(Integer.parseInt(balancerRunner.balancerContext.properties.getProperty("smppSslPort")));
	        securedConfiguration.setMaxConnectionSize(Integer.parseInt(balancerRunner.balancerContext.properties.getProperty("maxConnectionSize")));
	        securedConfiguration.setNonBlockingSocketsEnabled(Boolean.parseBoolean(balancerRunner.balancerContext.properties.getProperty("nonBlockingSocketsEnabled")));
	        securedConfiguration.setDefaultSessionCountersEnabled(Boolean.parseBoolean(balancerRunner.balancerContext.properties.getProperty("defaultSessionCountersEnabled")));
	        securedConfiguration.setUseSsl(true);
            SslConfiguration sslConfig = new SslConfiguration();
	        sslConfig.setKeyStorePath(balancerRunner.balancerContext.properties.getProperty("javax.net.ssl.keyStore"));
	        sslConfig.setKeyStorePassword(balancerRunner.balancerContext.properties.getProperty("javax.net.ssl.keyStorePassword"));
	        sslConfig.setTrustStorePath(balancerRunner.balancerContext.properties.getProperty("javax.net.ssl.trustStore"));
	        sslConfig.setTrustStorePassword(balancerRunner.balancerContext.properties.getProperty("javax.net.ssl.trustStorePassword"));
	        securedConfiguration.setSslConfiguration(sslConfig);        
        } 
        
        balancerDispatcher = new BalancerDispatcher(balancerRunner,monitorExecutor);
		smppLbServer = new BalancerServer(regularConfiguration, securedConfiguration, executor, balancerRunner, balancerDispatcher, monitorExecutor);
        smppLbServer.start();
	}
	public void stop()
	{
		smppLbServer.stop();
        executor.shutdown();
        monitorExecutor.shutdown();
	}

	public BalancerDispatcher getBalancerDispatcher() 
	{
		return balancerDispatcher;
	}
	//Statistic
	/**
     * @return the smppRequestsToServer
     */
	public long getNumberOfSmppRequestsToServer() 
	{
		return balancerRunner.balancerContext.smppRequestsToServer.get();
	}
	/**
     * @return the smppRequestsToClient
     */
	public long getNumberOfSmppRequestsToClient()
	{
		return balancerRunner.balancerContext.smppRequestsToClient.get();
	}
	
	/**
     * @return the smppBytesToServer
     */
	public long getNumberOfSmppBytesToServer()
	{
		return balancerRunner.balancerContext.smppBytesToServer.get();
	}
	
	/**
     * @return the smppBytesToClient
     */
	public long getNumberOfSmppBytesToClient()
	{
		return balancerRunner.balancerContext.smppBytesToClient.get();
	}
	
	/**
     * @return the smppRequestsProcessedById
     */
	public long getSmppRequestsProcessedById(Integer id) 
	{
	        AtomicLong smppRequestsProcessed = balancerRunner.balancerContext.smppRequestsProcessedById.get(id);
	        if(smppRequestsProcessed != null) {
	            return smppRequestsProcessed.get();
	        }
	        return 0;
	}
	
	/**
     * @return the smppResponsesProcessedById
     */
	public long getSmppResponsesProcessedById(Integer id) 
	{
	        AtomicLong smppResponsesProcessed = balancerRunner.balancerContext.smppResponsesProcessedById.get(id);
	        if(smppResponsesProcessed != null) {
	            return smppResponsesProcessed.get();
	        }
	        return 0;
	}
	
	/**
     * @return the NumberOfActiveSmppConnections
     */
	public int getNumberOfActiveSmppConnections()
	{
		return balancerDispatcher.getClientSessions().size() + balancerDispatcher.getServerSessions().size();
	}

}

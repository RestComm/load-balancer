/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, Red Hat, Inc. and individual contributors
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

package org.mobicents.tools.sip.balancer;

import gov.nist.javax.sip.stack.StatsRetreiver;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.LogManager;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.xml.DOMConfigurator;
import org.mobicents.tools.configuration.LoadBalancerConfiguration;
import org.mobicents.tools.configuration.XmlConfigurationLoader;
import org.mobicents.tools.heartbeat.api.HeartbeatConfig;
import org.mobicents.tools.heartbeat.api.Node;
import org.mobicents.tools.heartbeat.impl.HeartbeatConfigHttp;
import org.mobicents.tools.http.balancer.HttpBalancerForwarder;
import org.mobicents.tools.smpp.balancer.core.SmppBalancerRunner;
import org.restcomm.commons.statistics.reporter.RestcommStatsReporter;

import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;
/**
 * @author jean.deruelle@gmail.com
 *
 */
public class BalancerRunner implements BalancerRunnerMBean {

	public static final String SIP_BALANCER_JMX_NAME = "mobicents:type=LoadBalancer,name=LoadBalancer";
	public static final String HTML_ADAPTOR_JMX_NAME = "mobicents:name=htmladapter,port=";
	protected static final String STATISTICS_SERVER = "statistics.server";
	protected static final String DEFAULT_STATISTICS_SERVER = "https://statistics.restcomm.com/rest/";
	
	private SipBalancerShutdownHook shutdownHook=null;
	
	RestcommStatsReporter statsReporter = new RestcommStatsReporter();
	MetricRegistry metrics = RestcommStatsReporter.getMetricRegistry();
	//define metric name
    Counter counterCalls = metrics.counter("calls");
    //Counter counterSeconds = metrics.counter("seconds");
    Counter counterMessages = metrics.counter("messages");	
	
	ConcurrentHashMap<String, InvocationContext> contexts = new ConcurrentHashMap<String, InvocationContext>();
	static {
		String logLevel = System.getProperty("logLevel", "INFO");
		String logConfigFile = System.getProperty("logConfigFile");

		if (logConfigFile != null)
		{
		    DOMConfigurator.configure(logConfigFile);
		}
		else if(!LogManager.getLogManager().getLoggerNames().hasMoreElements())
		{
			Logger.getRootLogger().addAppender(new ConsoleAppender(
					new PatternLayout("%r (%t) %p [%c{1}%x] %m%n")));
			Logger.getRootLogger().setLevel(Level.toLevel(logLevel));
		}
	}

	public InvocationContext getInvocationContext(String version) {
		if(version == null) version = "0";
		InvocationContext ct = contexts.get(version);
		if(ct==null) {
			ct = new InvocationContext(version, balancerContext);
			contexts.put(version, ct);
		}
		return ct;
	}
	public InvocationContext getLatestInvocationContext() {
		return getInvocationContext(reg.getLatestVersion());
	}
	
	private static Logger logger = Logger.getLogger(BalancerRunner.class
			.getCanonicalName());
	protected SIPBalancerForwarder sipForwarder = null;
	protected NodeRegisterImpl reg = null;
	HttpBalancerForwarder httpBalancerForwarder;
	public SmppBalancerRunner smppBalancerRunner;
	public BalancerContext balancerContext = new BalancerContext();
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		if (args.length < 1) {
			logger.error("Please specify mobicents-balancer-config argument. Usage is : java -DlogConfigFile=./lb-log4j.xml -jar sip-balancer-jar-with-dependencies.jar -mobicents-balancer-config=lb-configuration.xml");
			return;
		}
		
		if(!args[0].startsWith("-mobicents-balancer-config=")) {
			logger.error("Impossible to find the configuration file since you didn't specify the mobicents-balancer-config argument. Usage is : java -DlogConfigFile=./lb-log4j.xml -jar sip-balancer-jar-with-dependencies.jar -mobicents-balancer-config=lb-configuration.xml");
			return;
		}
		
		// Configuration file Location
		String configurationFileLocation = args[0].substring("-mobicents-balancer-config=".length());
		BalancerRunner balancerRunner = new BalancerRunner();
		balancerRunner.start(configurationFileLocation); 
	}
	
	public void start(LoadBalancerConfiguration lbConfig) {
		if(statsReporter==null)
			statsReporter = new RestcommStatsReporter();

		String ipAddress = lbConfig.getCommonConfiguration().getHost();
		if(ipAddress == null) {
			ipAddress = lbConfig.getSipConfiguration().getInternalLegConfiguration().getHost();
		}
		if(ipAddress == null) {
			ipAddress = lbConfig.getSipConfiguration().getExternalLegConfiguration().getHost();
		}
		InetAddress addr = null;
		try {
			addr = InetAddress.getByName(ipAddress);
		} catch (UnknownHostException e) {
			logger.error("Couldn't get the InetAddress from the host " + ipAddress, e);
			return;
		}

	    balancerContext.securityRequired = lbConfig.getCommonConfiguration().getSecurityRequired();
	    if(balancerContext.securityRequired)
	    {
	    	balancerContext.login = lbConfig.getCommonConfiguration().getLogin();
	    	balancerContext.password = lbConfig.getCommonConfiguration().getPassword();
	    }

		balancerContext.algorithmClassName = lbConfig.getSipConfiguration().getAlgorithmConfiguration().getAlgorithmClass();
		balancerContext.terminateTLSTraffic = lbConfig.getSslConfiguration().getTerminateTLSTraffic();
		balancerContext.smppToProviderAlgorithmClassName = lbConfig.getSmppConfiguration().getSmppToProviderAlgorithmClass();
		if(lbConfig.getSmppConfiguration().isMuxMode())
			balancerContext.smppToNodeAlgorithmClassName = lbConfig.getSmppConfiguration().getSmppToNodeAlgorithmClass();
		balancerContext.shutdownTimeout = lbConfig.getCommonConfiguration().getShutdownTimeout();
		
		try {
			MBeanServer server = ManagementFactory.getPlatformMBeanServer();
			RouterImpl.setRegister(reg);			

			reg = new NodeRegisterImpl(addr);
			reg.balancerRunner = this;
			reg.setNodeExpirationTaskInterval(lbConfig.getCommonConfiguration().getHeartbeatInterval());
			reg.setNodeExpiration(lbConfig.getCommonConfiguration().getNodeTimeout());
			if(logger.isInfoEnabled()) {
				logger.info("Node timeout" + " = " + reg.getNodeExpiration());
				logger.info("Heartbeat interval" + " = " + reg.getNodeExpirationTaskInterval());
			}
			
			if(logger.isDebugEnabled()) {
                logger.debug("LB will use next class for registry nodes : " + lbConfig.getHeartbeatConfigurationClass());
            }
			
			HeartbeatConfig heartbeatConfig = lbConfig.getHeartbeatConfiguration();
			if(heartbeatConfig ==null)
			{
				logger.warn("Configuration of heartbeat is not set, we will use http heartbeat protocol default values");
				heartbeatConfig = new HeartbeatConfigHttp();
			}
			balancerContext.nodeCommunicationProtocolClassName = heartbeatConfig.getProtocolClassName();
			reg.startRegistry(heartbeatConfig);
				
			if(logger.isDebugEnabled()) {
				logger.debug("adding shutdown hook");
			}
			
			String statisticsServer = Version.getVersionProperty(STATISTICS_SERVER);
			if(statisticsServer == null || !statisticsServer.contains("http")) {
				statisticsServer = DEFAULT_STATISTICS_SERVER;
			}
			//define remote server address (optionally)
	        statsReporter.setRemoteServer(statisticsServer);
	        String projectName = System.getProperty("RestcommProjectName", "loadbalancer");
	        String projectType = System.getProperty("RestcommProjectType", "community");
	        String projectVersion = System.getProperty("RestcommProjectVersion", Version.getVersionProperty(Version.RELEASE_VERSION));
	        if(logger.isDebugEnabled()) {
		 		logger.debug("Restcomm Stats " + projectName + " " + projectType + " " + projectVersion);
		 	}
	        statsReporter.setProjectName(projectName);
	        statsReporter.setProjectType(projectType);
	        statsReporter.setVersion(projectVersion);
	        //define periodicy - default to once a day
	        statsReporter.start(86400, TimeUnit.SECONDS);

	        Version.printVersion();
	        
			sipForwarder = new SIPBalancerForwarder(lbConfig, this, reg);
			sipForwarder.start();
			if(lbConfig.getHttpConfiguration().getHttpPort()!=null)
			{
			httpBalancerForwarder = new HttpBalancerForwarder();
			httpBalancerForwarder.balancerRunner = this;
			try {
				httpBalancerForwarder.start();
			} catch (org.jboss.netty.channel.ChannelException e) {
				logger.warn("HTTP forwarder could not be restarted.");
			}
			}
			//register the sip balancer
			ObjectName on = new ObjectName(SIP_BALANCER_JMX_NAME);
			if (server.isRegistered(on)) {
				server.unregisterMBean(on);
			}
			server.registerMBean(this, on);
			         
	        shutdownHook=new SipBalancerShutdownHook(this);
			Runtime.getRuntime().addShutdownHook(shutdownHook);
		} catch (Exception e) {
			logger.error("An unexpected error occurred while starting the load balancer", e);
			return;
		}
		if(lbConfig.getSmppConfiguration().getSmppPort()!=null)
		{
			smppBalancerRunner = new SmppBalancerRunner(this);
			smppBalancerRunner.start();
		}	
	}
	//start Jboss cache
//			String cacheConfigFile = lbConfig.getCommonConfiguration().getCacheConfigFile();
//			if(balancerContext.cacheListener==null&&cacheConfigFile!=null&&!cacheConfigFile.equals(""))
//				balancerContext.cacheListener = new HttpCacheListener(this);
	Timer timer;
	long lastupdate = 0;

	/**
	 * @param configurationFileLocation
	 */
	public void start(final String configurationFileLocation) {
		File file = new File(configurationFileLocation);
		lastupdate = file.lastModified();
		final XmlConfigurationLoader configLoader = new XmlConfigurationLoader();
        LoadBalancerConfiguration lbConfig = configLoader.load(file); 

		timer = new Timer();
		timer.scheduleAtFixedRate(new TimerTask() {
			public void run() {
				File conf = new File(configurationFileLocation);
				if(lastupdate < conf.lastModified()) {
					lastupdate = conf.lastModified();
					logger.info("Configuration file changed, applying changes.");
					try {
						for(InvocationContext ctx : contexts.values()) {
							balancerContext.lbConfig = configLoader.load(conf);
							ctx.balancerAlgorithm.configurationChanged();
							if(ctx.smppToNodeBalancerAlgorithm!=null)
								ctx.smppToNodeBalancerAlgorithm.configurationChanged();
							ctx.smppToProviderBalancerAlgorithm.configurationChanged();
						}
					} catch (Exception e) {
						logger.warn("Problem reloading configuration " + e);
					} 
				}
			}
		}, 3000, 2000);

		start(lbConfig);

	}
	
	public void stop() 
	{
		if(shutdownHook==null)
			return;
		
		try
		{
			Runtime.getRuntime().removeShutdownHook(shutdownHook);
		}
		catch(IllegalStateException ex)
		{
			//may occure due to shutdown already in progress
		}
		
		shutdownHook=null;
		
		if(timer != null)
		{
			timer.cancel();
			timer = null;
		}
		
		statsReporter.stop();
		statsReporter = null;
		
		if(sipForwarder!=null)
		{
			logger.info("Stopping the sip forwarder");		
			sipForwarder.stop();
			sipForwarder=null;
		}
		
		if(httpBalancerForwarder!=null)
		{
			logger.info("Stopping the http forwarder");
			httpBalancerForwarder.stop();
			httpBalancerForwarder=null;
		}
		
		if(smppBalancerRunner != null)
		{
			logger.info("Stopping the SMPP balancer");
			smppBalancerRunner.stop();
			smppBalancerRunner=null;
		}
					
		MBeanServer server = ManagementFactory.getPlatformMBeanServer();		
		try 
		{
			if(reg!=null)
			{
				ObjectName on = new ObjectName(BalancerRunner.SIP_BALANCER_JMX_NAME);
				if (server.isRegistered(on)) 
				{
					logger.info("Unregistering the node registry");				
					server.unregisterMBean(on);
				}
			}
		} 
		catch (Exception e) 
		{
			logger.error("An unexpected error occurred while stopping the load balancer", e);
		}

		try 
		{
			for(InvocationContext ctx : contexts.values())
			{
				ctx.balancerAlgorithm.stop();
				if(ctx.smppToNodeBalancerAlgorithm!=null)
					ctx.smppToNodeBalancerAlgorithm.stop();
				ctx.smppToProviderBalancerAlgorithm.stop();
			}
			
			logger.info("Stopping the node registry");
			reg.stopRegistry();
			reg = null;
			System.gc();
			
		} catch (Exception e) {
			logger.error("An unexpected error occurred while stopping the load balancer", e);
		}	
		
		
	}

	//JMX 
	//SIP Balancer
	public long getNodeExpiration() {		
		return reg.getNodeExpiration();
	}

	public long getNodeExpirationTaskInterval() {
		return reg.getNodeExpirationTaskInterval();
	}

	public long getNumberOfRequestsProcessed() {
		return sipForwarder.getNumberOfRequestsProcessed();
	}

	public long getNumberOfResponsesProcessed() {
		return sipForwarder.getNumberOfResponsesProcessed();
	}
	public long getNumberOfBytesTransferred()
	{
		return sipForwarder.getNumberOfBytesTransferred();	
	}

	public Map<String, AtomicLong> getNumberOfRequestsProcessedByMethod() {
		return sipForwarder.getNumberOfRequestsProcessedByMethod();
	}

	public Map<String, AtomicLong> getNumberOfResponsesProcessedByStatusCode() {
		return sipForwarder.getNumberOfResponsesProcessedByStatusCode();
	}
	
	public long getRequestsProcessedByMethod(String method) {
		return sipForwarder.getRequestsProcessedByMethod(method);
	}

	public long getResponsesProcessedByStatusCode(String statusCode) {
		return sipForwarder.getResponsesProcessedByStatusCode(statusCode);
	}
	
	public int getNumberOfActiveSipConnections()
	{
		return StatsRetreiver.getOpenConnections(balancerContext.sipStack);
	}
	
	//HTTP balancer
	
	public long getNumberOfHttpRequests() 
	{
		return httpBalancerForwarder.getNumberOfHttpRequests();
	}
	
	public long getNumberOfHttpBytesToServer() 
	{
		return httpBalancerForwarder.getNumberOfHttpBytesToServer();
	}
	
	public long getNumberOfHttpBytesToClient() 
	{
		return httpBalancerForwarder.getNumberOfHttpBytesToClient();
	}
	
	public long getHttpRequestsProcessedByMethod(String method) 
	{
		return httpBalancerForwarder.getHttpRequestsProcessedByMethod(method);
	}
	
	public long getHttpResponseProcessedByCode(String code) 
	{
		return httpBalancerForwarder.getHttpResponseProcessedByCode(code);
	}
	
	public int getNumberOfActiveHttpConnections()
	{
		return httpBalancerForwarder.getNumberOfActiveHttpConnections();
	}
	
	//SMPP balancer
	public long getNumberOfSmppRequestsToServer() 
	{
		if(smppBalancerRunner!=null)
			return smppBalancerRunner.getNumberOfSmppRequestsToServer();
		else
			return 0;
	}
	
	public long getNumberOfSmppRequestsToClient() 
	{
		if(smppBalancerRunner!=null)
			return smppBalancerRunner.getNumberOfSmppRequestsToClient();
		else
			return 0;
	}
	
	public long getNumberOfSmppBytesToServer() 
	{
		if(smppBalancerRunner!=null)
			return smppBalancerRunner.getNumberOfSmppBytesToServer();
		else
			return 0;
	}
	
	public long getNumberOfSmppBytesToClient() 
	{
		if(smppBalancerRunner!=null)
			return smppBalancerRunner.getNumberOfSmppBytesToClient();
		else
			return 0;
	}
	
	public long getSmppRequestsProcessedById(Integer id) 
	{
		if(smppBalancerRunner!=null)
			return smppBalancerRunner.getSmppRequestsProcessedById(id);
		else
			return 0;
	}
	
	public long getSmppResponsesProcessedById(Integer id) 
	{
		if(smppBalancerRunner!=null)
			return smppBalancerRunner.getSmppResponsesProcessedById(id);
		else
			return 0;
	}
	
	public int getNumberOfActiveSmppConnections()
	{
		if(smppBalancerRunner!=null)
			return smppBalancerRunner.getNumberOfActiveSmppConnections();
		else
			return 0;
	}
	
	public void incCalls() {
		counterCalls.inc();
	}

	public void incMessages() {
		counterMessages.inc();
	}

//	public void incSeconds(long seconds) {
//		counterSeconds.inc(seconds);
//	}
	
	public void setNodeExpiration(long value) {
		reg.setNodeExpiration(value);
	}

	public void setNodeExpirationTaskInterval(long value) {
		reg.setNodeExpirationTaskInterval(value);
	}

	public List<Node> getNodes() {
		return new LinkedList<Node>(balancerContext.aliveNodes);
	}
	
	public String[] getNodeList() {
		List<Node> nodes = getNodes();
		String[] nodeList = new String[nodes.size()];
		int i = 0;
		for (Node node : nodes) {			
			nodeList[i] = node.toString();
			i++;
		}
		return nodeList;
	}

	public LoadBalancerConfiguration getConfiguration() {
		return balancerContext.lbConfig;
	}
//TODO!!!!!!!!!
//	public String getProperty(String key) {
//		return properties.getProperty(key);
//	}
//
//	public void setProperty(String key, String value) {
//		balancerContext.properties.setProperty(key, value);
//		for(InvocationContext ctx : contexts.values()) {
//			ctx.balancerAlgorithm.configurationChanged();
//		}
//	}
	
	@SuppressWarnings("restriction")
	@Override
	public double getJvmCpuUsage() 
	{
		return ((com.sun.management.OperatingSystemMXBean)ManagementFactory.getOperatingSystemMXBean()).getProcessCpuLoad();
	}
	
	@Override
	public long getJvmHeapSize() 
	{
		return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
	}
}

class SipBalancerShutdownHook extends Thread {
	BalancerRunner balancerRunner;
	
	public SipBalancerShutdownHook(BalancerRunner balancerRunner) {
		this.balancerRunner = balancerRunner;
	}
	
	@Override
	public void run() {
		balancerRunner.stop();
	}
}
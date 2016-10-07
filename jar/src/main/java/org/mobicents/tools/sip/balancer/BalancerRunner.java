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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.xml.DOMConfigurator;
import org.mobicents.tools.configuration.LoadBalancerConfiguration;
import org.mobicents.tools.configuration.XmlConfigurationLoader;
import org.mobicents.tools.http.balancer.HttpBalancerForwarder;
import org.mobicents.tools.smpp.balancer.core.SmppBalancerRunner;
import org.restcomm.commons.statistics.reporter.RestcommStatsReporter;
import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;
import com.sun.jdmk.comm.HtmlAdaptorServer;
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
	
	RestcommStatsReporter statsReporter = RestcommStatsReporter.getRestcommStatsReporter();
	MetricRegistry metrics = RestcommStatsReporter.getMetricRegistry();
	//define metric name
    Counter counterCalls = metrics.counter("calls");
    //Counter counterSeconds = metrics.counter("seconds");
    Counter counterMessages = metrics.counter("messages");	
	
	ConcurrentHashMap<String, InvocationContext> contexts = new ConcurrentHashMap<String, InvocationContext>();
	static {
		String logLevel = System.getProperty("logLevel", "INFO");
		String logConfigFile = System.getProperty("logConfigFile");

		if(logConfigFile == null) {
			Logger.getRootLogger().addAppender(new ConsoleAppender(
					new PatternLayout("%r (%t) %p [%c{1}%x] %m%n")));
			Logger.getRootLogger().setLevel(Level.toLevel(logLevel));
		} else {
		    DOMConfigurator.configure(logConfigFile);
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
	HtmlAdaptorServer adapter;
	ObjectName adapterName = null;
	JMXConnectorServer cs = null;
	HttpBalancerForwarder httpBalancerForwarder;
	public SmppBalancerRunner smppBalancerRunner;
	public BalancerContext balancerContext = new BalancerContext();
	
	public String algorithClassName = null;

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
		adapter = new HtmlAdaptorServer();
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
		
		int jmxHtmlPort = -1;
		jmxHtmlPort = lbConfig.getCommonConfiguration().getJmxHtmlAdapterPort();

		int rmiRegistryPort = -1;
		rmiRegistryPort = lbConfig.getCommonConfiguration().getRmiRegistryPort();
	
		
		int remoteObjectPort = -1;
	    remoteObjectPort = lbConfig.getCommonConfiguration().getRmiRemoteObjectPort();
		
		this.algorithClassName = lbConfig.getSipConfiguration().getAlgorithmConfiguration().getAlgorithmClass();
		balancerContext.algorithmClassName = this.algorithClassName;
		balancerContext.terminateTLSTraffic = lbConfig.getSslConfiguration().getTerminateTLSTraffic();
		
		try {
			
			MBeanServer server = ManagementFactory.getPlatformMBeanServer();
			
			//register the jmx html adapter
			adapterName = new ObjectName(HTML_ADAPTOR_JMX_NAME + jmxHtmlPort);
	        adapter.setPort(jmxHtmlPort);	        	        
			server.registerMBean(adapter, adapterName);					
			
			RouterImpl.setRegister(reg);			

			reg = new NodeRegisterImpl(addr);
			reg.balancerRunner = this;
			
			try {
				reg.setNodeExpirationTaskInterval(lbConfig.getCommonConfiguration().getHeartbeatInterval());
				reg.setNodeExpiration(lbConfig.getCommonConfiguration().getNodeTimeout());
				if(logger.isInfoEnabled()) {
					logger.info("Node timeout" + " = " + reg.getNodeExpiration());
					logger.info("Heartbeat interval" + " = " + reg.getNodeExpirationTaskInterval());
				}
			} catch(NumberFormatException nfe) {
				logger.error("Couldn't convert rmiRegistryPort to a valid integer", nfe);
				return ; 
			}
			
			if(logger.isDebugEnabled()) {
                logger.debug("About to startRegistry at: "+rmiRegistryPort+" and remoteObjectPort: "+remoteObjectPort);
            }
			reg.startRegistry(rmiRegistryPort, remoteObjectPort);
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
			
			// Create an RMI connector and start it
	        JMXServiceURL url = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://" + ipAddress + ":" + rmiRegistryPort + "/server");
	        cs = JMXConnectorServerFactory.newJMXConnectorServer(url, null, server);
	        cs.start();
	        adapter.start();
	         
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
			
			if(adapter!=null)
			{
				if(server.isRegistered(adapterName)) 
				{
					logger.info("Unregistering the node adapter");
					server.unregisterMBean(adapterName);
				}
			}		
		} 
		catch (Exception e) 
		{
			logger.error("An unexpected error occurred while stopping the load balancer", e);
		}
		
		try 
		{
			if(cs != null) 
			{
				if(cs.isActive()) 
					cs.stop();
				
				cs = null;
				
				for(InvocationContext ctx : contexts.values()) 
					ctx.balancerAlgorithm.stop();
				
				logger.info("Stopping the node registry");
				adapter.stop();
				reg.stopRegistry();
				reg = null;
				adapter = null;
				System.gc();
			}
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
		return smppBalancerRunner.getNumberOfSmppRequestsToServer();
	}
	
	public long getNumberOfSmppRequestsToClient() 
	{
		return smppBalancerRunner.getNumberOfSmppRequestsToClient();
	}
	
	public long getNumberOfSmppBytesToServer() 
	{
		return smppBalancerRunner.getNumberOfSmppBytesToServer();
	}
	
	public long getNumberOfSmppBytesToClient() 
	{
		return smppBalancerRunner.getNumberOfSmppBytesToClient();
	}
	
	public long getSmppRequestsProcessedById(Integer id) 
	{
		return smppBalancerRunner.getSmppRequestsProcessedById(id);
	}
	
	public long getSmppResponsesProcessedById(Integer id) 
	{
		return smppBalancerRunner.getSmppResponsesProcessedById(id);
	}
	
	public int getNumberOfActiveSmppConnections()
	{
		return smppBalancerRunner.getNumberOfActiveSmppConnections();
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

	public List<SIPNode> getNodes() {
		return new LinkedList(balancerContext.aliveNodes);
	}
	
	public String[] getNodeList() {
		List<SIPNode> nodes = getNodes();
		String[] nodeList = new String[nodes.size()];
		int i = 0;
		for (SIPNode node : nodes) {			
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
	private static Logger logger = Logger.getLogger(SipBalancerShutdownHook.class
			.getCanonicalName());
	BalancerRunner balancerRunner;
	
	public SipBalancerShutdownHook(BalancerRunner balancerRunner) {
		this.balancerRunner = balancerRunner;
	}
	
	@Override
	public void run() {
		balancerRunner.stop();
	}
}
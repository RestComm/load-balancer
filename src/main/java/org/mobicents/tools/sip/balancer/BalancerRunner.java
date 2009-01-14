package org.mobicents.tools.sip.balancer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;

import com.sun.jdmk.comm.HtmlAdaptorServer;

/**
 * @author jean.deruelle@gmail.com
 *
 */
public class BalancerRunner implements BalancerRunnerMBean {

	private static final String HOST_PROP = "host";
	private static final String RMI_REGISTRY_PORT_PROP = "rmiRegistryPort";
	private static final String JMX_HTML_ADAPTER_PORT_PROP = "jmxHtmlAdapterPort";
	public static final String SIP_BALANCER_JMX_NAME = "mobicents:type=LoadBalancer,name=LoadBalancer";
	public static final String HTML_ADAPTOR_PORT = "8000";
	public static final String REGISTRY_PORT = "2000";
	public static final String HTML_ADAPTOR_JMX_NAME = "mobicents:name=htmladapter,port="+ HTML_ADAPTOR_PORT;
	private static Logger logger = Logger.getLogger(BalancerRunner.class
			.getCanonicalName());
	protected SIPBalancerForwarder fwd = null;
	protected NodeRegisterImpl reg = null;
	HtmlAdaptorServer adapter = new HtmlAdaptorServer();
	ObjectName adapterName = null;
	JMXConnectorServer cs = null;

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		if (args.length < 1) {
			logger.fine("Insufficient args");
			throw new IllegalArgumentException(
					"Bad args: supply configuration file location ");
		}
		
		// Configuration file Location
		String configurationFileLocation = args[0].substring("-mobicents-balancer-config=".length());
		BalancerRunner balancerRunner = new BalancerRunner();
		balancerRunner.start(configurationFileLocation); 
	}

	/**
	 * @param configurationFileLocation
	 */
	public void start(String configurationFileLocation) {
		File file = new File(configurationFileLocation);
        FileInputStream fileInputStream = null;
        try {
        	fileInputStream = new FileInputStream(file);
		} catch (FileNotFoundException e) {
			throw new IllegalArgumentException("the configuration file location " + configurationFileLocation + " does not exists !");
		}
        
        Properties properties = new Properties(System.getProperties());
        try {
			properties.load(fileInputStream);
		} catch (IOException e) {
			throw new IllegalArgumentException("Unable to load the properties configuration file located at " + configurationFileLocation);
		}

		String ipAddress = properties.getProperty(HOST_PROP);
				
		InetAddress addr = null;
		try {
			addr = InetAddress.getByName(ipAddress);
		} catch (UnknownHostException e) {
			logger.log(Level.SEVERE, "Couldn't get the InetAddress from the host " + ipAddress, e);
			return;
		}
		int jmxHtmlPort = -1;
		String portAsString = properties.getProperty(JMX_HTML_ADAPTER_PORT_PROP,HTML_ADAPTOR_PORT);
		try {
			jmxHtmlPort = Integer.parseInt(portAsString);
		} catch(NumberFormatException nfe) {
			logger.log(Level.SEVERE, "Couldn't convert jmxHtmlAdapterPort to a valid integer", nfe);
			return ; 
		}
		int rmiRegistryPort = -1;
		portAsString = properties.getProperty(RMI_REGISTRY_PORT_PROP,REGISTRY_PORT);
		try {
			rmiRegistryPort = Integer.parseInt(portAsString);
		} catch(NumberFormatException nfe) {
			logger.log(Level.SEVERE, "Couldn't convert rmiRegistryPort to a valid integer", nfe);
			return ; 
		}
		try {
			
			MBeanServer server = ManagementFactory.getPlatformMBeanServer();
			
			//register the jmx html adapter
			adapterName = new ObjectName(HTML_ADAPTOR_JMX_NAME);
	        adapter.setPort(jmxHtmlPort);	        	        
			server.registerMBean(adapter, adapterName);					
			
			RouterImpl.setRegister(reg);
			fwd = new SIPBalancerForwarder(properties, reg);
			fwd.start();

			reg = new NodeRegisterImpl(addr);	
			reg.startRegistry(rmiRegistryPort);
			if(logger.isLoggable(Level.FINEST)) {
				logger.finest("adding shutdown hook");
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
	         
			Runtime.getRuntime().addShutdownHook(new SipBalancerShutdownHook(this));
		} catch (Exception e) {
			logger.log(Level.SEVERE, "An unexpected error occurred while starting the load balancer", e);
			return;
		}
	}
	
	public void stop() {
		logger.info("Stopping the sip forwarder");
		fwd.stop();
		logger.info("Stopping the node registry");
		reg.stopRegistry();
		logger.info("Unregistering the node registry");
		MBeanServer server = ManagementFactory.getPlatformMBeanServer();		
		try {
			ObjectName on = new ObjectName(BalancerRunner.SIP_BALANCER_JMX_NAME);
			if (server.isRegistered(on)) {
				server.unregisterMBean(on);
			}
		} catch (Exception e) {
			logger.log(Level.SEVERE, "An unexpected error occurred while stopping the load balancer", e);
		}
		try {
			cs.stop();
		} catch (Exception e) {
			logger.log(Level.SEVERE, "An unexpected error occurred while stopping the load balancer", e);
		}	
		adapter.stop();
	}

	//JMX 
	public long getNodeExpiration() {		
		return reg.getNodeExpiration();
	}

	public long getNodeExpirationTaskInterval() {
		return reg.getNodeExpirationTaskInterval();
	}

	public long getNumberOfRequestsProcessed() {
		return fwd.getNumberOfRequestsProcessed();
	}

	public long getNumberOfResponsesProcessed() {
		return fwd.getNumberOfResponsesProcessed();
	}

	public void setNodeExpiration(long value) {
		reg.setNodeExpiration(value);
	}

	public void setNodeExpirationTaskInterval(long value) {
		reg.setNodeExpirationTaskInterval(value);
	}

	public String[] getNodes() {
		return reg.getNodes();
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
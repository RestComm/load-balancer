package org.mobicents.tools.sip.balancer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.MBeanServer;
import javax.management.ObjectName;

/**
 * @author jean.deruelle@gmail.com
 *
 */
public class BalancerRunner implements BalancerRunnerMBean {

	public static final String SIP_BALANCER_NODE_REGISTRAR_JMX_NAME = "mobicents:type=LoadBalancerNodeRegistrar,name=registrar";
	public static final String SIP_BALANCER_FORWARDER_JMX_NAME = "mobicents:type=LoadBalancerForwarder,name=forwarder";
	public static final String SIP_BALANCER_JMX_NAME = "mobicents:type=LoadBalancer,name=LoadBalancer";
	private static Logger logger = Logger.getLogger(BalancerRunner.class
			.getCanonicalName());
	protected SIPBalancerForwarder fwd = null;
	protected NodeRegisterImpl reg = null;
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
		String configurationFileLocation = args[0];
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

		String ipAddress = properties.getProperty("host");
				
		InetAddress addr = null;
		try {
			addr = InetAddress.getByName(ipAddress);
		} catch (UnknownHostException e1) {

			e1.printStackTrace();
			return;
		}
		try {								
			MBeanServer server = ManagementFactory.getPlatformMBeanServer();
			ObjectName on = new ObjectName(SIP_BALANCER_JMX_NAME);

			if (server.isRegistered(on)) {
				server.unregisterMBean(on);
			}
			server.registerMBean(this, on);
			
			reg = new NodeRegisterImpl(addr);			
			on = new ObjectName(SIP_BALANCER_NODE_REGISTRAR_JMX_NAME);
			if (server.isRegistered(on)) {
				server.unregisterMBean(on);
			}
			server.registerMBean(reg, on);			
			RouterImpl.setRegister(reg);
			fwd = new SIPBalancerForwarder(properties, reg);
			fwd.start();
			on = new ObjectName(SIP_BALANCER_FORWARDER_JMX_NAME);

			if (server.isRegistered(on)) {
				server.unregisterMBean(on);
			}
			server.registerMBean(fwd, on);
			
			reg.startServer();
			if(logger.isLoggable(Level.FINEST)) {
				logger.finest("adding shutdown hook");
			}
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
		reg.stopServer();
		logger.info("Unregistering the node registry");
		MBeanServer server = ManagementFactory.getPlatformMBeanServer();
		try {
			ObjectName on = new ObjectName(BalancerRunner.SIP_BALANCER_NODE_REGISTRAR_JMX_NAME);
			if (server.isRegistered(on)) {
				server.unregisterMBean(on);
			}
		} catch (Exception e) {
			logger.log(Level.SEVERE, "An unexpected error occurred while stopping the load balancer", e);
		}
		try {
			ObjectName on = new ObjectName(BalancerRunner.SIP_BALANCER_FORWARDER_JMX_NAME);
			if (server.isRegistered(on)) {
				server.unregisterMBean(on);
			}
		} catch (Exception e) {
			logger.log(Level.SEVERE, "An unexpected error occurred while stopping the load balancer", e);
		}
		try {
			ObjectName on = new ObjectName(BalancerRunner.SIP_BALANCER_JMX_NAME);
			if (server.isRegistered(on)) {
				server.unregisterMBean(on);
			}
		} catch (Exception e) {
			logger.log(Level.SEVERE, "An unexpected error occurred while stopping the load balancer", e);
		}
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
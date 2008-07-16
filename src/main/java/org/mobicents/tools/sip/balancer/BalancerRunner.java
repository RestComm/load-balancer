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

public class BalancerRunner {

	public static final String SIP_BALANCER_JMX_NAME = "slee:name=Balancer,type=sip";
	private static Logger logger = Logger.getLogger(BalancerRunner.class
			.getCanonicalName());

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
		File file = new File(configurationFileLocation);
        FileInputStream fileInputStream = null;
        try {
        	fileInputStream = new FileInputStream(file);
		} catch (FileNotFoundException e) {
			throw new IllegalArgumentException("the configuration file location " + configurationFileLocation + " does not exists !");
		}
        
        Properties properties = new Properties();
        try {
			properties.load(fileInputStream);
		} catch (IOException e) {
			throw new IllegalArgumentException("Unable to load the properties configuration file located at " + configurationFileLocation);
		}
		
//		int port;
//		int externalPort;
		//int serverPort;
//		try {
//			// This is the proxy port at which I am listening.
//			port = Integer.parseInt(args[1]);
//			externalPort = Integer.parseInt(args[2]);
//			//serverPort = Integer.parseInt(args[3]);
//
//		} catch (NumberFormatException nfe) {
//			nfe.printStackTrace();
//			logger.fine("arguments are IPAddress port");
//			throw nfe;
//		}

		String ipAddress = properties.getProperty("host");
		
		NodeRegisterImpl reg = null;
		InetAddress addr = null;
		try {
			addr = InetAddress.getByName(ipAddress);
		} catch (UnknownHostException e1) {

			e1.printStackTrace();
			return;
		}
		try {
			reg = new NodeRegisterImpl(addr);
			MBeanServer server = ManagementFactory.getPlatformMBeanServer();
			ObjectName on = new ObjectName(SIP_BALANCER_JMX_NAME);

			if (server.isRegistered(on)) {
				server.unregisterMBean(on);
			}
			server.registerMBean(reg, on);
			RouterImpl.setRegister(reg);
			SIPBalancerForwarder fwd = new SIPBalancerForwarder(properties, reg);
			fwd.start();
			reg.startServer();
			if(logger.isLoggable(Level.FINEST)) {
				logger.finest("adding shutdown hook");
			}
			Runtime.getRuntime().addShutdownHook(new SipBalancerShutdownHook(fwd, reg));
		} catch (Exception e) {
			e.printStackTrace();
			return;
		} 
	}
}

class SipBalancerShutdownHook extends Thread {
	private static Logger logger = Logger.getLogger(SipBalancerShutdownHook.class
			.getCanonicalName());
	SIPBalancerForwarder forwarder;
	NodeRegisterImpl registry;
	MBeanServer server;
	
	public SipBalancerShutdownHook(SIPBalancerForwarder fwd, NodeRegisterImpl registry) {
		this.forwarder = fwd;
		this.registry = registry;
		this.server = ManagementFactory.getPlatformMBeanServer();
	}
	
	@Override
	public void run() {
		logger.info("Stopping the sip forwarder");
		forwarder.stop();
		logger.info("Stopping the node registry");
		registry.stopServer();
		logger.info("Unregistering the node registry");
		try {
			ObjectName on = new ObjectName(BalancerRunner.SIP_BALANCER_JMX_NAME);
			if (server.isRegistered(on)) {
				server.unregisterMBean(on);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
package org.mobicents.tools.sip.balancer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;

import org.mobicents.tools.http.balancer.HttpBalancerForwarder;

import com.sun.jdmk.comm.HtmlAdaptorServer;

/**
 * @author jean.deruelle@gmail.com
 *
 */
public class BalancerRunner implements BalancerRunnerMBean {

	private static final String NODE_TIMEOUT = "nodeTimeout";
	private static final String HEARTBEAT_INTERVAL = "heartbeatInterval";
	private static final String HOST_PROP = "host";
	private static final String RMI_REGISTRY_PORT_PROP = "rmiRegistryPort";
	private static final String JMX_HTML_ADAPTER_PORT_PROP = "jmxHtmlAdapterPort";
	private static final String ALGORITHM_PROP = "algorithmClass";
	private static final String DEFAULT_ALGORITHM = CallIDAffinityBalancerAlgorithm.class.getCanonicalName();
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
	HttpBalancerForwarder httpBalancerForwarder;

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		if (args.length < 1) {
			logger.severe("Please specify mobicents-balancer-config argument. Usage is : java -jar sip-balancer-jar-with-dependencies.jar -mobicents-balancer-config=lb-configuration.properties");
			return;
		}
		
		if(!args[0].startsWith("-mobicents-balancer-config=")) {
			logger.severe("Impossible to find the configuration file since you didn't specify the mobicents-balancer-config argument. Usage is : java -jar sip-balancer-jar-with-dependencies.jar -mobicents-balancer-config=lb-configuration.properties");
			return;
		}
		
		// Configuration file Location
		String configurationFileLocation = args[0].substring("-mobicents-balancer-config=".length());
		BalancerRunner balancerRunner = new BalancerRunner();
		balancerRunner.start(configurationFileLocation); 
	}
	
	public void start(Properties properties) {
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
		
		String algorithmClassname = properties.getProperty(ALGORITHM_PROP, DEFAULT_ALGORITHM);
		
		try {
			Class clazz = Class.forName(algorithmClassname);
			BalancerContext.balancerContext.balancerAlgorithm = (BalancerAlgorithm) clazz.newInstance();
			BalancerContext.balancerContext.balancerAlgorithm.setProperties(properties);
			logger.info("Balancer algorithm " + algorithmClassname + " loaded succesfully");
		} catch (Exception e) {
			throw new RuntimeException("Error loading the algorithm class: " + algorithmClassname, e);
		}
		
		try {
			
			MBeanServer server = ManagementFactory.getPlatformMBeanServer();
			
			//register the jmx html adapter
			adapterName = new ObjectName(HTML_ADAPTOR_JMX_NAME);
	        adapter.setPort(jmxHtmlPort);	        	        
			server.registerMBean(adapter, adapterName);					
			
			RouterImpl.setRegister(reg);			

			reg = new NodeRegisterImpl(addr);
			
			try {
				reg.setNodeExpirationTaskInterval(Integer.parseInt(properties.getProperty(HEARTBEAT_INTERVAL, "150")));
				reg.setNodeExpiration(Integer.parseInt(properties.getProperty(NODE_TIMEOUT, "5200")));
				if(logger.isLoggable(Level.INFO)) {
					logger.info(NODE_TIMEOUT + "=" + reg.getNodeExpiration());
					logger.info(HEARTBEAT_INTERVAL + "=" + reg.getNodeExpirationTaskInterval());
				}
			} catch(NumberFormatException nfe) {
				logger.log(Level.SEVERE, "Couldn't convert rmiRegistryPort to a valid integer", nfe);
				return ; 
			}
			
			reg.startRegistry(rmiRegistryPort);
			if(logger.isLoggable(Level.FINEST)) {
				logger.finest("adding shutdown hook");
			}
			
			fwd = new SIPBalancerForwarder(properties, reg);
			fwd.start();
			httpBalancerForwarder = new HttpBalancerForwarder();
			try {
			httpBalancerForwarder.start();
			} catch (org.jboss.netty.channel.ChannelException e) {
				logger.warning("HTTP forwarder could not be restarted.");
			}
			
			BalancerContext.balancerContext.balancerAlgorithm.init();
			
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
	Timer timer;
	long lastupdate = 0;

	/**
	 * @param configurationFileLocation
	 */
	public void start(final String configurationFileLocation) {
		File file = new File(configurationFileLocation);
		lastupdate = file.lastModified();
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
		} finally {
			try {
				fileInputStream.close();
			} catch (IOException e) {
				logger.warning("Problem closing file " + e);
			}
		}
		timer = new Timer();
		timer.scheduleAtFixedRate(new TimerTask() {
			public void run() {
				File conf = new File(configurationFileLocation);
				if(lastupdate < conf.lastModified()) {
					lastupdate = conf.lastModified();
					logger.info("Configuration file changed, applying changes.");
					FileInputStream fileInputStream = null;
					try {
						fileInputStream = new FileInputStream(conf);
						BalancerContext.balancerContext.properties.load(fileInputStream);
						BalancerContext.balancerContext.balancerAlgorithm.configurationChanged();
					} catch (Exception e) {
						logger.warning("Problem reloading configuration " + e);
					} finally {
						if(fileInputStream != null) {
							try {
								fileInputStream.close();
							} catch (Exception e) {
								logger.severe("Problem closing stream " + e);
							}
						}
					}
				}
			}
		}, 3000, 2000);

		start(properties);

	}
	
	public void stop() {
		timer.cancel();
		timer = null;
		logger.info("Stopping the sip forwarder");
		fwd.stop();
		logger.info("Stopping the http forwarder");
		httpBalancerForwarder.stop();
		logger.info("Unregistering the node registry");
		MBeanServer server = ManagementFactory.getPlatformMBeanServer();		
		try {
			ObjectName on = new ObjectName(BalancerRunner.SIP_BALANCER_JMX_NAME);
			if (server.isRegistered(on)) {
				server.unregisterMBean(on);
			}
			if(server.isRegistered(adapterName)) {
				server.unregisterMBean(adapterName);
			}
		} catch (Exception e) {
			logger.log(Level.SEVERE, "An unexpected error occurred while stopping the load balancer", e);
		}
		try {
			if(cs != null) {
				if(cs.isActive()) {
					cs.stop();
				}
				cs = null;
				BalancerContext.balancerContext.balancerAlgorithm.stop();
				adapter.stop();
				logger.info("Stopping the node registry");
				reg.stopRegistry();
				reg = null;
				adapter = null;
				System.gc();
			}
		} catch (Exception e) {
			logger.log(Level.SEVERE, "An unexpected error occurred while stopping the load balancer", e);
		}	
		
		
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

	public Map<String, AtomicLong> getNumberOfRequestsProcessedByMethod() {
		return fwd.getNumberOfRequestsProcessedByMethod();
	}

	public Map<String, AtomicLong> getNumberOfResponsesProcessedByStatusCode() {
		return fwd.getNumberOfResponsesProcessedByStatusCode();
	}
	
	public long getRequestsProcessedByMethod(String method) {
		return fwd.getRequestsProcessedByMethod(method);
	}

	public long getResponsesProcessedByStatusCode(String statusCode) {
		return fwd.getResponsesProcessedByStatusCode(statusCode);
	}
	
	public void setNodeExpiration(long value) {
		reg.setNodeExpiration(value);
	}

	public void setNodeExpirationTaskInterval(long value) {
		reg.setNodeExpirationTaskInterval(value);
	}

	public List<SIPNode> getNodes() {
		return reg.getNodes();
	}
	
	public String[] getNodeList() {
		List<SIPNode> nodes = getNodes();
		String[] nodeList = new String[nodes.size()];
		int i = 0;
		for (SIPNode node : nodes) {			
			nodeList[0] = node.toString();
			i++;
		}
		return nodeList;
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
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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

import org.apache.log4j.Appender;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.xml.DOMConfigurator;

import com.cloudhopper.smpp.SmppServerConfiguration;
import com.cloudhopper.smpp.ssl.SslConfiguration;

/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */

public class SmppBalancerRunner {

	private static final Logger logger = Logger.getLogger(SmppBalancerRunner.class);
	
	private BalancerDispatcher balancerDispatcher;
	private ThreadPoolExecutor executor = (ThreadPoolExecutor)Executors.newCachedThreadPool();
	private ScheduledExecutorService monitorExecutor  = Executors.newScheduledThreadPool(4);
	private BalancerServer smppLbServer;
	
	static {
		String logLevel =  System.getProperty("logLevel", "INFO");
		String logConfigFile = System.getProperty("logConfigFile");

		if(logConfigFile == null) {
			@SuppressWarnings("unchecked")
			Enumeration<Appender> appenders=Logger.getRootLogger().getAllAppenders();
			Boolean found=false;
			while(appenders.hasMoreElements())
			{
				Appender curr=appenders.nextElement();
				if(curr instanceof ConsoleAppender)
				{
					curr.setLayout(new PatternLayout("%d %p %t - %m%n"));
					found=true;
					break;
				}
			}
			if(!found)
				Logger.getRootLogger().addAppender(new ConsoleAppender(new PatternLayout("%d %p %t - %m%n")));
			Logger.getRootLogger().setLevel(Level.toLevel(logLevel));
		} else {
		    DOMConfigurator.configure(logConfigFile);
		}
	}
	
	Timer timer;
	long lastupdate = 0;
	/**
	 * Start load balancer using configuration file
	 * and check changes in file for update
	 * @param configurationFileLocation
	 */
	public void start(final String configurationFileLocation){
		
		File file = new File(configurationFileLocation);
		lastupdate = file.lastModified();
        FileInputStream fileInputStream = null;
        try {
        	fileInputStream = new FileInputStream(file);
		} catch (FileNotFoundException e) {
			throw new IllegalArgumentException("the configuration file location " + configurationFileLocation + " does not exists !");
		}
        
        final Properties properties = new Properties(System.getProperties());
        try {
			properties.load(fileInputStream);
		} catch (IOException e) {
			throw new IllegalArgumentException("Unable to load the properties configuration file located at " + configurationFileLocation);
		} finally {
			try {
				fileInputStream.close();
			} catch (IOException e) {
				logger.warn("Problem closing file " + e);
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
							properties.load(fileInputStream);
							logger.info("Changes applied.");
						
					} catch (Exception e) {
						logger.warn("Problem reloading configuration " + e);
					} finally {
						if(fileInputStream != null) {
							try {
								fileInputStream.close();
							} catch (Exception e) {
								logger.error("Problem closing stream " + e);
							}
						}
					}
				}
			}
		}, 3000, 2000);

        start(properties);
	}
	/**
	 * Start load balancer using properies
	 * @param properties
	 */
	public void start(Properties properties)
	{
        SmppServerConfiguration regularConfiguration = new SmppServerConfiguration();
        regularConfiguration.setName(properties.getProperty("smppName"));
        regularConfiguration.setHost(properties.getProperty("smppHost"));
        regularConfiguration.setPort(Integer.parseInt(properties.getProperty("smppPort")));
        regularConfiguration.setMaxConnectionSize(Integer.parseInt(properties.getProperty("maxConnectionSize")));
        regularConfiguration.setNonBlockingSocketsEnabled(Boolean.parseBoolean(properties.getProperty("nonBlockingSocketsEnabled")));
        regularConfiguration.setDefaultSessionCountersEnabled(Boolean.parseBoolean(properties.getProperty("defaultSessionCountersEnabled")));
        regularConfiguration.setUseSsl(false);                
        
        SmppServerConfiguration securedConfiguration = null;
        if(Boolean.parseBoolean(properties.getProperty("isSslEnabled")))
        {
        	securedConfiguration = new SmppServerConfiguration();
        	securedConfiguration.setName(properties.getProperty("smppName"));
        	securedConfiguration.setHost(properties.getProperty("smppHost"));
	        securedConfiguration.setPort(Integer.parseInt(properties.getProperty("smppSslPort")));
	        securedConfiguration.setMaxConnectionSize(Integer.parseInt(properties.getProperty("maxConnectionSize")));
	        securedConfiguration.setNonBlockingSocketsEnabled(Boolean.parseBoolean(properties.getProperty("nonBlockingSocketsEnabled")));
	        securedConfiguration.setDefaultSessionCountersEnabled(Boolean.parseBoolean(properties.getProperty("defaultSessionCountersEnabled")));
	        securedConfiguration.setUseSsl(true);
            SslConfiguration sslConfig = new SslConfiguration();
	        sslConfig.setKeyStorePath(properties.getProperty("sslKeyPath"));
	        sslConfig.setKeyStorePassword(properties.getProperty("sslPasword"));
	        sslConfig.setKeyManagerPassword(properties.getProperty("sslPasword"));
	        sslConfig.setTrustStorePath(properties.getProperty("sslKeyPath"));
	        sslConfig.setTrustStorePassword(properties.getProperty("sslPasword"));
	        securedConfiguration.setSslConfiguration(sslConfig);        
        }
        
        balancerDispatcher = new BalancerDispatcher(properties,monitorExecutor);
		smppLbServer = new BalancerServer(regularConfiguration, securedConfiguration, executor, properties, balancerDispatcher, monitorExecutor);
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

}

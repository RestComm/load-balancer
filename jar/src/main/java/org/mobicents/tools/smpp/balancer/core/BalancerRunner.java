/*
 * JBoss, Home of Professional Open Source
 * Copyright 2015-2016, Red Hat, Inc. and individual contributors
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

package org.mobicents.tools.smpp.balancer.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.xml.DOMConfigurator;

import com.cloudhopper.smpp.SmppServerConfiguration;

/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */

public class BalancerRunner {

	private static final Logger logger = Logger.getLogger(BalancerRunner.class);

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
	
	public static void main(String[] args) {
		
		if (args.length < 1) {
			logger.error("Please specify mobicents-balancer-config argument. Usage is : java -DlogConfigFile=./lb-log4j.xml -jar sip-balancer-jar-with-dependencies.jar -mobicents-balancer-config=lb-configuration.properties");
			return;
		}
		
		if(!args[0].startsWith("-mobicents-balancer-config=")) {
			logger.error("Impossible to find the configuration file since you didn't specify the mobicents-balancer-config argument.  Usage is : java -DlogConfigFile=./lb-log4j.xml -jar sip-balancer-jar-with-dependencies.jar -mobicents-balancer-config=lb-configuration.properties");
			return;
		}
		
		String configurationFileLocation = args[0].substring("-mobicents-balancer-config=".length());
		BalancerRunner lbStarter = new BalancerRunner();
		lbStarter.start(configurationFileLocation);

	}
	
	Timer timer;
	long lastupdate = 0;
	
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
        //must reload property file in period 
        
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
	
	public void start(Properties properties)
	{
        ThreadPoolExecutor executor = (ThreadPoolExecutor)Executors.newCachedThreadPool();
        SmppServerConfiguration configuration = new SmppServerConfiguration();
        configuration.setName(properties.getProperty("smppName"));
        configuration.setHost(properties.getProperty("smppHost"));
        configuration.setPort(Integer.parseInt(properties.getProperty("smppPort")));
        configuration.setMaxConnectionSize(Integer.parseInt(properties.getProperty("maxConnectionSize")));
        configuration.setNonBlockingSocketsEnabled(Boolean.parseBoolean(properties.getProperty("nonBlockingSocketsEnabled")));
        configuration.setDefaultSessionCountersEnabled(Boolean.parseBoolean(properties.getProperty("defaultSessionCountersEnabled")));
		BalancerServer smppLbServer = new BalancerServer(configuration, executor, properties);
        smppLbServer.start();

	}

}

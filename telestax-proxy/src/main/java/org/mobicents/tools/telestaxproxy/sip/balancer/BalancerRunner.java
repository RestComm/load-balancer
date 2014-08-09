/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2014, Telestax Inc and individual contributors
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

package org.mobicents.tools.telestaxproxy.sip.balancer;

import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.xml.DOMConfigurator;
import org.mobicents.tools.sip.balancer.InvocationContext;

/**
 * @author jean.deruelle@gmail.com
 * @author gvagenas@gmail.com
 *
 */
public class BalancerRunner extends org.mobicents.tools.sip.balancer.BalancerRunner implements BalancerRunnerMBean {

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
    /**
     * @param args
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            logger.error("Please specify mobicents-balancer-config argument. Usage is : java -DlogConfigFile=./lb-log4j.xml -jar sip-balancer-jar-with-dependencies.jar -mobicents-balancer-config=lb-configuration.properties");
            return;
        }

        if(!args[0].startsWith("-mobicents-balancer-config=")) {
            logger.error("Impossible to find the configuration file since you didn't specify the mobicents-balancer-config argument. Usage is : java -DlogConfigFile=./lb-log4j.xml -jar sip-balancer-jar-with-dependencies.jar -mobicents-balancer-config=lb-configuration.properties");
            return;
        }

        // Configuration file Location
        String configurationFileLocation = args[0].substring("-mobicents-balancer-config=".length());
        BalancerRunner balancerRunner = new BalancerRunner();
        balancerRunner.start(configurationFileLocation); 
    }

}
@SuppressWarnings("unused")
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

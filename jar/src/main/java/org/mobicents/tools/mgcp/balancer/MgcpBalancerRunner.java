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
package org.mobicents.tools.mgcp.balancer;

import jain.protocol.ip.mgcp.CreateProviderException;
import jain.protocol.ip.mgcp.JainMgcpProvider;
import jain.protocol.ip.mgcp.message.parms.NotifiedEntity;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.TooManyListenersException;

import org.apache.log4j.Logger;
import org.mobicents.tools.configuration.MgcpConfiguration;
import org.mobicents.tools.sip.balancer.BalancerRunner;
import org.restcomm.media.client.mgcp.stack.JainMgcpStackImpl;
import org.restcomm.media.client.mgcp.stack.JainMgcpStackProviderImpl;
/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */
public class MgcpBalancerRunner {
	
	private static final Logger logger = Logger.getLogger(MgcpBalancerRunner.class);

	private BalancerRunner balancerRunner;
	private MgcpExternalListener externalListener;
	private MgcpInternalListener internalListener;
	private JainMgcpStackImpl externalStack;
	private JainMgcpStackImpl internalStack;
	private MgcpBalancerDispatcher dispatcher;
	
	public MgcpBalancerRunner(BalancerRunner balancerRunner) 
	{
		this.balancerRunner = balancerRunner;
	}

	public void start() 
	{
		MgcpConfiguration mgcpConfig = balancerRunner.balancerContext.lbConfig.getMgcpConfiguration();
        String mgcpExternalHost = mgcpConfig.getMgcpExternalHost();
        String mgcpInternalHost = mgcpConfig.getMgcpInternalHost();
        if (mgcpExternalHost == null || mgcpExternalHost.equals("")||
        		mgcpInternalHost == null || mgcpInternalHost.equals(""))
        {
        	logger.info("External and Internal hosts not set for MGCP LB, it will use common host");
        	mgcpExternalHost = mgcpConfig.getMgcpHost();
        	mgcpInternalHost = mgcpConfig.getMgcpHost();
        }
        InetAddress inetAddresExternal = null;
        InetAddress inetAddresInternal = null;
		try {
			inetAddresExternal = InetAddress.getByName(mgcpExternalHost);
			inetAddresInternal = InetAddress.getByName(mgcpInternalHost);
		} catch (UnknownHostException e) {
			logger.error("Couldn't get the InetAddress from the host " + mgcpExternalHost + " or " + mgcpInternalHost, e);
			logger.error("Start of MGCP balancer aborted");
			return;
		}
		//create MGCP stack
		externalStack = new JainMgcpStackImpl(inetAddresExternal, mgcpConfig.getMgcpExternalPort());
		internalStack = new JainMgcpStackImpl(inetAddresInternal, mgcpConfig.getMgcpInternalPort());
		JainMgcpStackProviderImpl externalProvider = null;
		JainMgcpStackProviderImpl internalProvider = null;
		try {
			externalProvider = (JainMgcpStackProviderImpl) externalStack.createProvider();
			internalProvider = (JainMgcpStackProviderImpl) internalStack.createProvider();
		} catch (CreateProviderException e) {
			logger.error("Couldn't create MGCP provider! " , e);
		}
		String lBHost = mgcpExternalHost+":"+ mgcpConfig.getMgcpExternalPort();
		dispatcher = new MgcpBalancerDispatcher(balancerRunner, lBHost,externalProvider,internalProvider);
		try {
			externalListener = new MgcpExternalListener(dispatcher);
			internalListener = new MgcpInternalListener(dispatcher);
			externalProvider.addJainMgcpListener(externalListener);
			internalProvider.addJainMgcpListener(internalListener);
		} catch (TooManyListenersException e) {
			logger.error("Couldn't create add MGCP listener! " , e);
		}
		
	}

	public void stop() 
	{
		if(externalStack != null)
			externalStack.close();
		if(internalStack != null)
			internalStack.close();
	}
	public MgcpBalancerDispatcher getMgcpBalancerDispatcher()
	{
		return dispatcher;
	}
	
}

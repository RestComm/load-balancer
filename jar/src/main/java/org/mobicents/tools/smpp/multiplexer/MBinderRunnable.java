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

package org.mobicents.tools.smpp.multiplexer;

import org.mobicents.tools.sip.balancer.SIPNode;

import com.cloudhopper.smpp.SmppSessionConfiguration;

/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */

public class MBinderRunnable implements Runnable {

	private SIPNode node;
	private MClientConnectionImpl client;
	private String systemId;
	private String password;
	private String systemType;
	private boolean isUseSsl;
	
	public MBinderRunnable(MClientConnectionImpl connection, String systemId, String password, String systemType)
	{
		this.client = connection;
		this.node = connection.getNode();
		this.systemId = systemId;
		this.password = password;
		this.systemType = systemType;
		this.isUseSsl = connection.isSslConnection();

	}

	@Override
	public void run() {			
		SmppSessionConfiguration config = client.getConfig();
		config.setName("Loadbalancer");
		config.setHost(node.getIp());
		config.setPort(Integer.parseInt(node.getProperties().get("smppPort").toString()));
		config.setSystemId(systemId);
		config.setPassword(password);
		config.setSystemType(systemType);
		config.setUseSsl(isUseSsl);
		if (client.connect()) 
			client.bind();	
	}
}

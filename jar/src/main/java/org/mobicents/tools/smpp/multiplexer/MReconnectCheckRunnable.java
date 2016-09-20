package org.mobicents.tools.smpp.multiplexer;

import java.util.Map;

import org.apache.log4j.Logger;

public class MReconnectCheckRunnable implements Runnable 
{
	private static final Logger logger = Logger.getLogger(MReconnectCheckRunnable.class);
	private MClientConnectionImpl connection;
	private Map connectionsToServers;
	private Long serverSessionID;
	
	public MReconnectCheckRunnable (MClientConnectionImpl connection, Map connectionsToServers, Long serverSessionID)
	{
		this.connection = connection;
		this.connectionsToServers = connectionsToServers;
		this.serverSessionID = serverSessionID;
	}
	
	@Override
	public void run() 
	{	
		switch(connection.getClientState())
		{
			case BOUND:
				logger.debug("LB rebinded to server : " +  serverSessionID);
				connectionsToServers.put(serverSessionID,connection);
				break;
			default:
				logger.debug("LB did not rebind to server : " +  serverSessionID);
				break;
		}
	}
}

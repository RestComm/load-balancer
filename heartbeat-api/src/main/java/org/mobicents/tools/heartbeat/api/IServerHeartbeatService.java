package org.mobicents.tools.heartbeat.api;

import java.net.InetAddress;


public interface IServerHeartbeatService {

	void startServer();
	void stopServer();
	void init(IServerListener listener, InetAddress host, Integer ... heartbeatPort);
	void sendPacket(String ip, int parseInt);
	
}

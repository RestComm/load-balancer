package org.mobicents.tools.heartbeat.api;

import java.net.InetAddress;

public interface IServerHeartbeatService<T> {

	void startServer();
	void stopServer();
	void init(IServerListener listener, InetAddress host, T properties);
	void sendPacket(String ip, int parseInt);
	
}

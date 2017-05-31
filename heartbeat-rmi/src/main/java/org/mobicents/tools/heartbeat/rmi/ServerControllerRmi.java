/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2017, Telestax Inc and individual contributors
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
package org.mobicents.tools.heartbeat.rmi;

import java.io.IOException;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMISocketFactory;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Logger;
import org.mobicents.tools.heartbeat.api.HeartbeatRequestPacket;
import org.mobicents.tools.heartbeat.api.IListener;
import org.mobicents.tools.heartbeat.api.IServerHeartbeatService;
import org.mobicents.tools.heartbeat.api.IServerListener;
import org.mobicents.tools.heartbeat.api.NodeShutdownRequestPacket;
import org.mobicents.tools.heartbeat.api.NodeStopRequestPacket;
import org.mobicents.tools.heartbeat.api.Protocol;
import org.mobicents.tools.heartbeat.api.StartRequestPacket;
import org.mobicents.tools.sip.balancer.NodeRegisterRMIStub;
import org.mobicents.tools.sip.balancer.SIPNode;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */
public class ServerControllerRmi implements IListener,IServerHeartbeatService<HeartbeatConfigRmi> {

	private static Logger logger = Logger.getLogger(ServerControllerRmi.class.getCanonicalName());
	private JsonParser parser = new JsonParser();
	private Gson gson = new Gson();
	private Registry registry;
	private int rmiRegistryPort = 2000;
	private int remoteObjectPort = 2001;
	private IServerListener listener;
	private ConcurrentHashMap<String, SIPNode> activeNodes = new ConcurrentHashMap<>();
	private ConcurrentHashMap<String, SIPNode> shutdownNodes = new ConcurrentHashMap<>();
	
	@Override
	public void startServer() {
		 try 
		 {
			 registry.bind("SIPBalancer", new RegisterRMIStub(remoteObjectPort));
	         logger.info("RMI heartbeat server bound to ports rmiRegistryPort " + rmiRegistryPort +" and remoteObjectPort "+ remoteObjectPort);
	     } catch (Exception e) 
	     {
	    	 throw new RuntimeException("Failed to bind due to:", e);
	     }
	}

	@Override
	public void stopServer() {
		try {
            registry.unbind("SIPBalancer");
            UnicastRemoteObject.unexportObject(registry, false);
        } catch (RemoteException e) {
            throw new RuntimeException("Failed to unbind due to", e);
        } catch (NotBoundException e) {
            throw new RuntimeException("Failed to unbind due to", e);
        }

	}

	@Override
	public void init(IServerListener listener, InetAddress serverAddress, HeartbeatConfigRmi config) {
		this.rmiRegistryPort = config.getRmiRegistryPort();
		this.remoteObjectPort = config.getRmiRemoteObjectPort();
		this.listener = listener;
			try {
				registry = LocateRegistry.createRegistry(rmiRegistryPort, null, new BindingAddressCorrectnessSocketFactory(serverAddress));
			} catch (RemoteException e) {
				 logger.info("RMI LocateRegistry creating failed, port " + rmiRegistryPort);
			}
	}

	@Override
	public void sendPacket(String ip, int parseInt)
	{
		
	}
	
	public static class BindingAddressCorrectnessSocketFactory extends RMISocketFactory implements Serializable {
		
		private static final long serialVersionUID = 1L;
		private InetAddress bindingAddress = null;
	    public BindingAddressCorrectnessSocketFactory(InetAddress ipInterface) 
	    {
	        this.bindingAddress = ipInterface;
	    }
	    public ServerSocket createServerSocket(int port) 
	    {
	       ServerSocket serverSocket = null;
	       try { 
	              serverSocket = new ServerSocket(port, 50, bindingAddress);
	           } catch (Exception e) {
	             throw new RuntimeException(e);
	           }
	           return (serverSocket);
	    }
	    public Socket createSocket(String dummy, int port) throws IOException 
	    {
	    	return (new Socket(bindingAddress, port));
	    }

	    public boolean equals(Object other) 
	    {
	        return (other != null && other.getClass() == this.getClass());
	    }
	  }
	  private class RegisterRMIStub extends UnicastRemoteObject implements NodeRegisterRMIStub {

	        /**
			 * 
			 */
			private static final long serialVersionUID = 1L;

			protected RegisterRMIStub(int port) throws RemoteException {
	            super(port);
	        }

	        /*
	         * (non-Javadoc)
	         * @see org.mobicents.tools.sip.balancer.NodeRegisterRMIStub#handlePing(java.util.ArrayList)
	         */
	        public void handlePing(ArrayList<SIPNode> ping) throws RemoteException {
	        	for(SIPNode node : ping)
	        	{
	        		String sessionId = (String)node.getProperties().get(Protocol.SESSION_ID);
	        		
	        		if(node.getProperties().get("GRACEFUL_SHUTDOWN")!=null&&node.getProperties().get("GRACEFUL_SHUTDOWN").equals("true"))
	        		{
	        			if(!shutdownNodes.containsKey(sessionId))
	        			{
	        				shutdownNodes.put(sessionId, node);
	        				JsonObject jsonObject = parser.parse(gson.toJson(new NodeShutdownRequestPacket(node))).getAsJsonObject();
	        				listener.shutdownRequestReceived(null, jsonObject);
	        			}
	        			continue;
	        		}
	        		
	        		if(!activeNodes.containsKey(sessionId))
	        		{
	        			activeNodes.put(sessionId, node);
	        			JsonObject jsonObject = parser.parse(gson.toJson(new StartRequestPacket(node))).getAsJsonObject();
	        			listener.startRequestReceived(null, jsonObject);
	        		}
	        		else
	        		{
	        			JsonObject jsonObject = parser.parse(gson.toJson(new HeartbeatRequestPacket(node))).getAsJsonObject();
	        			listener.heartbeatRequestReceived(null, jsonObject);
	        		}
	        		
	        	}
	        }

	        /*
	         * (non-Javadoc)
	         * @see org.mobicents.tools.sip.balancer.NodeRegisterRMIStub#forceRemoval(java.util.ArrayList)
	         */
	        public void forceRemoval(ArrayList<SIPNode> ping) throws RemoteException {
	        	for(SIPNode node : ping)
	        	{
	        		String sessionId = (String)node.getProperties().get(Protocol.SESSION_ID);
	        		activeNodes.remove(sessionId);
	        		JsonObject jsonObject = parser.parse(gson.toJson(new NodeStopRequestPacket(node))).getAsJsonObject();
	        		listener.stopRequestReceived(null, jsonObject);
	        	}
	        }

	        public void switchover(String fromJvmRoute, String toJvmRoute) throws RemoteException {
	        	//listener.jvmRouteSwitchover(fromJvmRoute, toJvmRoute);

	        }

	    }

//	    private boolean deregister(InetAddress serverAddress) {
//	        
//
//	    }
}

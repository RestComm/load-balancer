/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, Red Hat, Inc. and individual contributors
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

package org.mobicents.tools.sip.balancer;

import java.io.IOException;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.AlreadyBoundException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMISocketFactory;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import org.apache.log4j.Logger;

/**
 * <p>
 * This is the placeholder for maintening information about alive nodes and 
 * the relation between a Call-Id and its attributed node.  
 * </p>
 * 
 * @author M. Ranganathan
 * @author baranowb 
 * @author <A HREF="mailto:jean.deruelle@gmail.com">Jean Deruelle</A> 
 *
 */
public class NodeRegisterImpl  implements NodeRegister {
	private static Logger logger = Logger.getLogger(NodeRegisterImpl.class.getCanonicalName());
	
	public static final int POINTER_START = 0;
	private long nodeInfoExpirationTaskInterval = 5000;
	private long nodeExpiration = 5100;
	
	private Registry registry;
	private Timer taskTimer = new Timer();
	private TimerTask nodeExpirationTask = null;
	private InetAddress serverAddress = null;
	
	private String latestVersion = Integer.MIN_VALUE + "";
	private int numberOfOldServers, numberOfNewServers;
	
	BalancerRunner balancerRunner;

	
	public NodeRegisterImpl(InetAddress serverAddress) throws RemoteException {
		super();
		this.serverAddress = serverAddress;		
	}
	
		
	/**
	 * {@inheritDoc}
	 */
	public boolean startRegistry(int rmiRegistryPort) {
		if(logger.isInfoEnabled()) {
			logger.info("Node registry starting...");
		}
		try {
			balancerRunner.balancerContext.aliveNodes = new CopyOnWriteArrayList<SIPNode>();;
			balancerRunner.balancerContext.jvmRouteToSipNode = new ConcurrentHashMap<String, SIPNode>();
			register(serverAddress, rmiRegistryPort);
			
			this.nodeExpirationTask = new NodeExpirationTimerTask();
			this.taskTimer.scheduleAtFixedRate(this.nodeExpirationTask,
					this.nodeInfoExpirationTaskInterval,
					this.nodeInfoExpirationTaskInterval);
			if(logger.isInfoEnabled()) {
				logger.info("Node expiration task created");							
				logger.info("Node registry started");
			}
		} catch (Exception e) {
			logger.error("Unexpected exception while starting the registry", e);
			return false;
		}

		return true;
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean stopRegistry() {
		if(logger.isInfoEnabled()) {
			logger.info("Stopping node registry...");
		}
		boolean isDeregistered = deregister(serverAddress);
		boolean taskCancelled = nodeExpirationTask.cancel();
		if(logger.isInfoEnabled()) {
			logger.info("Node Expiration Task cancelled " + taskCancelled);
		}
		balancerRunner.balancerContext.allNodesEver.clear();
		balancerRunner.balancerContext.allNodesEver = null;
		if(logger.isInfoEnabled()) {
			logger.info("Node registry stopped.");
		}
		return isDeregistered;
	}

	
	// ********* CLASS TO BE EXPOSED VIA RMI
	private class RegisterRMIStub extends UnicastRemoteObject implements NodeRegisterRMIStub {

		protected RegisterRMIStub() throws RemoteException {
			super();
		}
		
		/*
		 * (non-Javadoc)
		 * @see org.mobicents.tools.sip.balancer.NodeRegisterRMIStub#handlePing(java.util.ArrayList)
		 */
		public void handlePing(ArrayList<SIPNode> ping) throws RemoteException {
			handlePingInRegister(ping);
		}

		/*
		 * (non-Javadoc)
		 * @see org.mobicents.tools.sip.balancer.NodeRegisterRMIStub#forceRemoval(java.util.ArrayList)
		 */
		public void forceRemoval(ArrayList<SIPNode> ping)
				throws RemoteException {
			forceRemovalInRegister(ping);
		}

		public void switchover(String fromJvmRoute, String toJvmRoute) throws RemoteException {
			jvmRouteSwitchover(fromJvmRoute, toJvmRoute);
			
		}
		
	}
	
	public static class BindingAddressCorrectnessSocketFactory extends RMISocketFactory implements
	Serializable
	{
		private InetAddress bindingAddress
		= null;
		public BindingAddressCorrectnessSocketFactory() {}
		public BindingAddressCorrectnessSocketFactory(InetAddress ipInterface) {
			this.bindingAddress = ipInterface;
		}
		public ServerSocket createServerSocket(int port) {
			ServerSocket serverSocket = null;
			try {
				serverSocket = new ServerSocket(port, 50, bindingAddress);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
			return (serverSocket);
		}
		
		public Socket createSocket(String dummy, int port) throws IOException {
			return (
				new Socket(bindingAddress, port));
		}
		
		public boolean equals(Object other) {
			return (other != null && 
					other.getClass() == this.getClass());
		}
	}
	// ***** SOME PRIVATE HELPERS

	private void register(InetAddress serverAddress, int rmiRegistryPort) {

		try {
			registry = LocateRegistry.createRegistry(
					rmiRegistryPort, null, 
					new BindingAddressCorrectnessSocketFactory(serverAddress));
			registry.bind("SIPBalancer", new RegisterRMIStub());
			logger.info("RMI heartbeat listener bound to internalHost, port " + rmiRegistryPort);
		} catch (Exception e) {
			throw new RuntimeException("Failed to bind due to:", e);
		}
	}
	
	private boolean deregister(InetAddress serverAddress) {
		try {
			registry.unbind("SIPBalancer");
			return UnicastRemoteObject.unexportObject(registry, false);
		} catch (RemoteException e) {
			throw new RuntimeException("Failed to unbind due to", e);
		} catch (NotBoundException e) {
			throw new RuntimeException("Failed to unbind due to", e);
		}

	}

	// ***** NODE MGMT METHODS

	/**
	 * {@inheritDoc}
	 */
	public void unStickSessionFromNode(String callID) {		
		if(logger.isDebugEnabled()) {
			logger.debug("unsticked  CallId " + callID + " from node " + null);
		}
	}


	
	/**
	 * {@inheritDoc}
	 */
	public SIPNode stickSessionToNode(String callID, SIPNode sipNode) {
		
		if(logger.isDebugEnabled()) {
			logger.debug("sticking  CallId " + callID + " to node " + null);
		}
		return null;
	}
	
	/**
	 * {@inheritDoc}
	 */
	public SIPNode getGluedNode(String callID) {
		if(logger.isDebugEnabled()) {
			logger.debug("glueued node " + null + " for CallId " + callID);
		}
		return null;
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean isSIPNodePresent(String host, int port, String transport, String version)  {		
		if(getNode(host, port, transport, version) != null) {
			return true;
		}
		return false;
	}
	
	/**
	 * {@inheritDoc}
	 */
	public SIPNode getNode(String host, int port, String transport, String version)  {		
		for (SIPNode node : balancerRunner.balancerContext.aliveNodes) {
			if(logger.isDebugEnabled()) {
				logger.debug("node to check against " + node);
			}
			if(node.getIp().equals(host)) {
				Integer nodePort = (Integer) node.getProperties().get(transport + "Port");
				if(nodePort != null) {
					if(nodePort == port) {
						if(version == null) {
							return node;
						} else {
							String nodeVersion = (String) node.getProperties().get("version");
							if(nodeVersion == null) nodeVersion = "0";
							if(version.equals(nodeVersion)) {
								return node;
							}
						}
					}
				}
			}
		}
		if(logger.isDebugEnabled()) {
			logger.debug("checking if the node is still alive for " + host + ":" + port + "/" + transport + " : false");
		}
		return null;
	}
	
	class NodeExpirationTimerTask extends TimerTask {
		
		public void run() {
			if(logger.isTraceEnabled()) {
				logger.trace("NodeExpirationTimerTask Running");
			}
			for (SIPNode node : balancerRunner.balancerContext.aliveNodes) {
				long expirationTime = node.getTimeStamp() + nodeExpiration;
				if (expirationTime < System
						.currentTimeMillis()) {
					InvocationContext ctx = balancerRunner.getInvocationContext(
							(String) node.getProperties().get("version"));
					balancerRunner.balancerContext.aliveNodes.remove(node);
					ctx.nodes.remove(node);
					ctx.balancerAlgorithm.nodeRemoved(node);
					if(logger.isInfoEnabled()) {
						logger.info("NodeExpirationTimerTask Run NSync["
							+ node + "] removed. Last timestamp: " + node.getTimeStamp() + 
							", current: " + System.currentTimeMillis()
							 + " diff=" + ((double)System.currentTimeMillis()-node.getTimeStamp() ) +
							 "ms and tolerance=" + nodeExpiration + " ms");
					}
				} else {
					if(logger.isTraceEnabled()) {
						logger.trace("node time stamp : " + expirationTime + " , current time : "
							+ System.currentTimeMillis());
					}
				}
			}
			if(logger.isTraceEnabled()) {
				logger.trace("NodeExpirationTimerTask Done");
			}
		}

	}
	
	/**
	 * {@inheritDoc}
	 */
	public synchronized void handlePingInRegister(ArrayList<SIPNode> ping) {
		for (SIPNode pingNode : ping) {
			String version = (String) pingNode.getProperties().get("version");
			if(version == null) version = "0";
			InvocationContext ctx = balancerRunner.getInvocationContext(
					version);
			pingNode.updateTimerStamp();
			//logger.info("Pingnode updated " + pingNode);
			if(pingNode.getProperties().get("jvmRoute") != null) {
				// Let it leak, we will have 10-100 nodes, not a big deal if it leaks.
				// We need info about inactive nodes to do the failover
				balancerRunner.balancerContext.jvmRouteToSipNode.put(
						(String)pingNode.getProperties().get("jvmRoute"), pingNode);				
			}
			SIPNode nodePresent = null;
			Iterator<SIPNode> nodesIterator = ctx.nodes.iterator();
			while (nodesIterator.hasNext() && nodePresent == null) {
				SIPNode node = (SIPNode) nodesIterator.next();
				if (node.equals(pingNode)) {
					nodePresent = node;
				}
			}
			// adding done afterwards to avoid ConcurrentModificationException when adding the node while going through the iterator
			if(nodePresent != null) {
				nodePresent.updateTimerStamp();
				if(logger.isDebugEnabled()) {
					logger.debug("Ping " + nodePresent.getTimeStamp());
				}
			} else {
				Integer current = Integer.parseInt(version);
				Integer latest = Integer.parseInt(latestVersion);
				latestVersion = Math.max(current, latest) + "";
				balancerRunner.balancerContext.aliveNodes.add(pingNode);
				ctx.nodes.add(pingNode);
				ctx.balancerAlgorithm.nodeAdded(pingNode);
				balancerRunner.balancerContext.allNodesEver.add(pingNode);
				pingNode.updateTimerStamp();
				if(logger.isInfoEnabled()) {
					logger.info("NodeExpirationTimerTask Run NSync["
						+ pingNode + "] added");
				}
			}					
		}
	}
	
	public String getLatestVersion() {
		return latestVersion;
	}
	
	/**
	 * {@inheritDoc}
	 */
	public void forceRemovalInRegister(ArrayList<SIPNode> ping) {
		for (SIPNode pingNode : ping) {
			InvocationContext ctx = balancerRunner.getInvocationContext(
					(String) pingNode.getProperties().get("version"));
			ctx.nodes.remove(pingNode);
			boolean nodePresent = false;
			Iterator<SIPNode> nodesIterator = balancerRunner.balancerContext.aliveNodes.iterator();
			while (nodesIterator.hasNext() && !nodePresent) {
				SIPNode node = (SIPNode) nodesIterator.next();
				if (node.equals(pingNode)) {
					nodePresent = true;
				}
			}
			// removal done afterwards to avoid ConcurrentModificationException when removing the node while goign through the iterator
			if(nodePresent) {
				balancerRunner.balancerContext.aliveNodes.remove(pingNode);
				ctx.balancerAlgorithm.nodeRemoved(pingNode);
				if(logger.isInfoEnabled()) {
					logger.info("NodeExpirationTimerTask Run NSync["
						+ pingNode + "] forcibly removed due to a clean shutdown of a node. Numbers of nodes present in the balancer : " 
						+ balancerRunner.balancerContext.aliveNodes.size());
				}
			}					
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public InetAddress getAddress() {

		return this.serverAddress;
	}

	/**
	 * {@inheritDoc}
	 */
	public long getNodeExpiration() {

		return this.nodeExpiration;
	}

	/**
	 * {@inheritDoc}
	 */
	public long getNodeExpirationTaskInterval() {

		return this.nodeInfoExpirationTaskInterval;
	}

	/**
	 * {@inheritDoc}
	 */
	public void setNodeExpiration(long value) throws IllegalArgumentException {
		if (value < 15)
			throw new IllegalArgumentException("Value cant be less than 15");
		this.nodeExpiration = value;

	}
	/**
	 * {@inheritDoc}
	 */
	public void setNodeExpirationTaskInterval(long value) {
		if (value < 15)
			throw new IllegalArgumentException("Value cant be less than 15");
		this.nodeInfoExpirationTaskInterval = value;
	}

	public SIPNode[] getAllNodes() {
		return balancerRunner.balancerContext.aliveNodes.toArray(new SIPNode[]{});
	}

	public SIPNode getNextNode() throws IndexOutOfBoundsException {
		// TODO Auto-generated method stub
		return null;
	}

	public void jvmRouteSwitchover(String fromJvmRoute, String toJvmRoute) {
		for(InvocationContext ctx : balancerRunner.contexts.values()) {
			ctx.balancerAlgorithm.jvmRouteSwitchover(fromJvmRoute, toJvmRoute);
		}
	}
}

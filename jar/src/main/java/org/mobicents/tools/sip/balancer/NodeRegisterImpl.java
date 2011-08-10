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

import java.net.InetAddress;
import java.rmi.AlreadyBoundException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

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
	
	BalancerRunner balancerRunner;

	
	public NodeRegisterImpl(InetAddress serverAddress) throws RemoteException {
		super();
		this.serverAddress = serverAddress;		
	}
	
	/**
	 * {@inheritDoc}
	 */
	public CopyOnWriteArrayList<SIPNode> getNodes() {
		return balancerRunner.balancerContext.nodes;
	}
		
	/**
	 * {@inheritDoc}
	 */
	public boolean startRegistry(int rmiRegistryPort) {
		if(logger.isLoggable(Level.INFO)) {
			logger.info("Node registry starting...");
		}
		try {
			balancerRunner.balancerContext.nodes = new CopyOnWriteArrayList<SIPNode>();;
			balancerRunner.balancerContext.jvmRouteToSipNode = new ConcurrentHashMap<String, SIPNode>();
			register(serverAddress, rmiRegistryPort);
			
			this.nodeExpirationTask = new NodeExpirationTimerTask();
			this.taskTimer.scheduleAtFixedRate(this.nodeExpirationTask,
					this.nodeInfoExpirationTaskInterval,
					this.nodeInfoExpirationTaskInterval);
			if(logger.isLoggable(Level.INFO)) {
				logger.info("Node expiration task created");							
				logger.info("Node registry started");
			}
		} catch (Exception e) {
			logger.log(Level.SEVERE, "Unexpected exception while starting the registry", e);
			return false;
		}

		return true;
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean stopRegistry() {
		if(logger.isLoggable(Level.INFO)) {
			logger.info("Stopping node registry...");
		}
		boolean isDeregistered = deregister(serverAddress);
		boolean taskCancelled = nodeExpirationTask.cancel();
		if(logger.isLoggable(Level.INFO)) {
			logger.info("Node Expiration Task cancelled " + taskCancelled);
		}
		balancerRunner.balancerContext.nodes.clear();
		balancerRunner.balancerContext.nodes = null;
		if(logger.isLoggable(Level.INFO)) {
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
	// ***** SOME PRIVATE HELPERS

	private void register(InetAddress serverAddress, int rmiRegistryPort) {

		try {
			registry = LocateRegistry.createRegistry(rmiRegistryPort);
			registry.bind("SIPBalancer", new RegisterRMIStub());
		} catch (RemoteException e) {
			throw new RuntimeException("Failed to bind due to:", e);
		} catch (AlreadyBoundException e) {
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
		if(logger.isLoggable(Level.FINEST)) {
			logger.finest("unsticked  CallId " + callID + " from node " + null);
		}
	}


	
	/**
	 * {@inheritDoc}
	 */
	public SIPNode stickSessionToNode(String callID, SIPNode sipNode) {
		
		if(logger.isLoggable(Level.FINEST)) {
			logger.finest("sticking  CallId " + callID + " to node " + null);
		}
		return null;
	}
	
	/**
	 * {@inheritDoc}
	 */
	public SIPNode getGluedNode(String callID) {
		if(logger.isLoggable(Level.FINEST)) {
			logger.finest("glueued node " + null + " for CallId " + callID);
		}
		return null;
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean isSIPNodePresent(String host, int port, String transport)  {		
		if(getNode(host, port, transport) != null) {
			return true;
		}
		return false;
	}
	
	/**
	 * {@inheritDoc}
	 */
	public SIPNode getNode(String host, int port, String transport)  {		
		for (SIPNode node : balancerRunner.balancerContext.nodes) {
			if(logger.isLoggable(Level.FINEST)) {
				logger.finest("node to check against " + node);
			}
			if(node.getIp().equals(host)) {
				Integer nodePort = (Integer) node.getProperties().get(transport + "Port");
				if(nodePort != null) {
					if(nodePort == port) {
						return node;
					}
				}
			}
		}
		if(logger.isLoggable(Level.FINEST)) {
			logger.finest("checking if the node is still alive for " + host + ":" + port + "/" + transport + " : false");
		}
		return null;
	}
	
	class NodeExpirationTimerTask extends TimerTask {
		
		public void run() {
			if(logger.isLoggable(Level.FINEST)) {
				logger.finest("NodeExpirationTimerTask Running");
			}
			for (SIPNode node : balancerRunner.balancerContext.nodes) {
				long expirationTime = node.getTimeStamp() + nodeExpiration;
				if (expirationTime < System
						.currentTimeMillis()) {
					balancerRunner.balancerContext.nodes.remove(node);
					balancerRunner.balancerContext.balancerAlgorithm.nodeRemoved(node);
					if(logger.isLoggable(Level.INFO)) {
						logger.info("NodeExpirationTimerTask Run NSync["
							+ node + "] removed. Last timestamp: " + node.getTimeStamp() + 
							", current: " + System.currentTimeMillis()
							 + " diff=" + ((double)System.currentTimeMillis()-node.getTimeStamp() ) +
							 "ms and tolerance=" + nodeExpiration + " ms");
					}
				} else {
					if(logger.isLoggable(Level.FINEST)) {
						logger.finest("node time stamp : " + expirationTime + " , current time : "
							+ System.currentTimeMillis());
					}
				}
			}
			if(logger.isLoggable(Level.FINEST)) {
				logger.finest("NodeExpirationTimerTask Done");
			}
		}

	}
	
	/**
	 * {@inheritDoc}
	 */
	public void handlePingInRegister(ArrayList<SIPNode> ping) {
		for (SIPNode pingNode : ping) {
			pingNode.updateTimerStamp();
			if(pingNode.getProperties().get("jvmRoute") != null) {
				// Let it leak, we will have 10-100 nodes, not a big deal if it leaks.
				// We need info about inactive nodes to do the failover
				balancerRunner.balancerContext.jvmRouteToSipNode.put(
						(String)pingNode.getProperties().get("jvmRoute"), pingNode);				
			}
			if(balancerRunner.balancerContext.nodes.size() < 1) {
				balancerRunner.balancerContext.nodes.add(pingNode);
				balancerRunner.balancerContext.balancerAlgorithm.nodeAdded(pingNode);
				balancerRunner.balancerContext.allNodesEver.add(pingNode);
				
				if(logger.isLoggable(Level.INFO)) {
					logger.info("NodeExpirationTimerTask Run NSync["
						+ pingNode + "] added");
				}
				return ;
			}
			SIPNode nodePresent = null;
			Iterator<SIPNode> nodesIterator = balancerRunner.balancerContext.nodes.iterator();
			while (nodesIterator.hasNext() && nodePresent == null) {
				SIPNode node = (SIPNode) nodesIterator.next();
				if (node.equals(pingNode)) {
					nodePresent = node;
				}
			}
			// adding done afterwards to avoid ConcurrentModificationException when adding the node while going through the iterator
			if(nodePresent != null) {
				nodePresent.updateTimerStamp();
				if(logger.isLoggable(Level.FINE)) {
					logger.fine("Ping " + nodePresent.getTimeStamp());
				}
			} else {
				balancerRunner.balancerContext.nodes.add(pingNode);
				balancerRunner.balancerContext.balancerAlgorithm.nodeAdded(pingNode);
				balancerRunner.balancerContext.allNodesEver.add(pingNode);
				pingNode.updateTimerStamp();
				if(logger.isLoggable(Level.INFO)) {
					logger.info("NodeExpirationTimerTask Run NSync["
						+ pingNode + "] added");
				}
			}					
		}
	}
	
	/**
	 * {@inheritDoc}
	 */
	public void forceRemovalInRegister(ArrayList<SIPNode> ping) {
		for (SIPNode pingNode : ping) {
			if(balancerRunner.balancerContext.nodes.size() < 1) {
				balancerRunner.balancerContext.nodes.remove(pingNode);
				balancerRunner.balancerContext.balancerAlgorithm.nodeRemoved(pingNode);
				if(logger.isLoggable(Level.INFO)) {
					logger.info("NodeExpirationTimerTask Run NSync["
						+ pingNode + "] forcibly removed due to a clean shutdown of a node");
				}
				return ;
			}
			boolean nodePresent = false;
			Iterator<SIPNode> nodesIterator = balancerRunner.balancerContext.nodes.iterator();
			while (nodesIterator.hasNext() && !nodePresent) {
				SIPNode node = (SIPNode) nodesIterator.next();
				if (node.equals(pingNode)) {
					nodePresent = true;
				}
			}
			// removal done afterwards to avoid ConcurrentModificationException when removing the node while goign through the iterator
			if(nodePresent) {
				balancerRunner.balancerContext.nodes.remove(pingNode);
				balancerRunner.balancerContext.balancerAlgorithm.nodeRemoved(pingNode);
				if(logger.isLoggable(Level.INFO)) {
					logger.info("NodeExpirationTimerTask Run NSync["
						+ pingNode + "] forcibly removed due to a clean shutdown of a node. Numbers of nodes present in the balancer : " 
						+ balancerRunner.balancerContext.nodes.size());
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
		return balancerRunner.balancerContext.nodes.toArray(new SIPNode[]{});
	}

	public SIPNode getNextNode() throws IndexOutOfBoundsException {
		// TODO Auto-generated method stub
		return null;
	}

	public void jvmRouteSwitchover(String fromJvmRoute, String toJvmRoute) {
		balancerRunner.balancerContext.balancerAlgorithm.jvmRouteSwitchover(fromJvmRoute, toJvmRoute);
	}
}

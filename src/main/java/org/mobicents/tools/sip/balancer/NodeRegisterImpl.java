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
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
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
public class NodeRegisterImpl  implements NodeRegister, NodeRegisterImplMBean {
	private static Logger logger = Logger.getLogger(NodeRegisterImpl.class.getCanonicalName());
	
	//FIXME make them configurable
	public static final int REGISTRY_PORT = 2000;
	public static final int POINTER_START = 0;
	private long nodeInfoExpirationTaskInterval = 5000;
	private long nodeExpiration = 5100;
	
	private Registry registry;
	private Timer taskTimer = new Timer();
	private TimerTask nodeExpirationTask = null;

	private AtomicInteger pointer;

	private List<SIPNode> nodes;
	private ConcurrentHashMap<String, SIPNode> gluedSessions;
	
	private InetAddress serverAddress = null;

	
	public NodeRegisterImpl(InetAddress serverAddress) throws RemoteException {
		super();
		this.serverAddress = serverAddress;		
	}
	
	/**
	 * {@inheritDoc}
	 */
	public List<SIPNode> getGatheredInfo() {
		return this.nodes;	
	}
	
	/**
	 * {@inheritDoc}
	 */
	public boolean startServer() {
		if(logger.isLoggable(Level.INFO)) {
			logger.info("Node registry starting...");
		}
		try {
			nodes = new CopyOnWriteArrayList<SIPNode>();
			gluedSessions = new ConcurrentHashMap<String, SIPNode>();
			pointer = new AtomicInteger(POINTER_START);
			
			register(serverAddress);
			
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
	public boolean stopServer() {
		if(logger.isLoggable(Level.INFO)) {
			logger.info("Stopping node registry...");
		}
		boolean isDeregistered = deregister(serverAddress);
		boolean taskCancelled = nodeExpirationTask.cancel();
		if(logger.isLoggable(Level.INFO)) {
			logger.info("Node Expiration Task cancelled " + taskCancelled);
		}
		nodes.clear();
		nodes = null;
		gluedSessions.clear();
		gluedSessions = null;
		pointer = new AtomicInteger(POINTER_START);
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
		
	}
	// ***** SOME PRIVATE HELPERS

	private void register(InetAddress serverAddress) {

		try {
			registry = LocateRegistry.createRegistry(REGISTRY_PORT);
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
		SIPNode node = gluedSessions.remove(callID);
		if(logger.isLoggable(Level.FINEST)) {
			logger.finest("unsticked  CallId " + callID + " from node " + node);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public SIPNode getNextNode() {
		int nodesSize = nodes.size(); 
		if(nodesSize < 1) {
			return null;
		}
		int index = pointer.getAndIncrement() % nodesSize;
		return this.nodes.get(index);
	}
	
	/**
	 * {@inheritDoc}
	 */
	public SIPNode stickSessionToNode(String callID, SIPNode sipNode) {
		SIPNode node = null;
		if(sipNode != null) {
			node = gluedSessions.putIfAbsent(callID, sipNode);
			if(node == null) {
				node = sipNode; 
			}
		} else {
			node = gluedSessions.get(callID);
		}
		// Issue 308 (http://code.google.com/p/mobicents/issues/detail?id=308)
		// if we already stick a request to this node, but the server crashed and this is a retransmission
		// we need to check if the node is still alive and pick another one if not
		if(node != null && !isSIPNodePresent(node.getIp(), node.getPort(), node.getTransports()[0])) {
			node = null;
		}
		if(node == null) {
			SIPNode newStickyNode = this.getNextNode();
			if (newStickyNode  != null) {
				node = gluedSessions.putIfAbsent(callID, newStickyNode);
				if(node == null) {
					node = newStickyNode; 
				}
			}
		}		
		if(logger.isLoggable(Level.FINEST)) {
			logger.finest("sticking  CallId " + callID + " to node " + node);
		}
		return node;
	}
	
	/**
	 * {@inheritDoc}
	 */
	public SIPNode getGluedNode(String callID) {
		SIPNode sipNode = this.gluedSessions.get(callID);;
		if(logger.isLoggable(Level.FINEST)) {
			logger.finest("glueued node " + sipNode + " for CallId " + callID);
		}
		return sipNode;
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
		for (SIPNode node : nodes) {
			if(logger.isLoggable(Level.FINEST)) {
				logger.finest("node to check against " + node);
			}
			if(node.getIp().equals(host) && node.getPort() == port) {
				String[] nodeTransports = node.getTransports();
				if(nodeTransports.length > 0) {
					for(String nodeTransport : nodeTransports) {
						if(nodeTransport.equalsIgnoreCase(transport)) {
							if(logger.isLoggable(Level.FINEST)) {
								logger.finest("checking if the node is still alive for " + host + ":" + port + "/" + transport + " : true");
							}
							return node;
						}
					}
				} else {
					if(logger.isLoggable(Level.FINEST)) {
						logger.finest("checking if the node is still alive for " + host + ":" + port + "/" + transport + " : true");
					}
					return node;
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
			for (SIPNode node : nodes) {
				
				if (node.getTimeStamp() + nodeExpiration < System
						.currentTimeMillis()) {
					nodes.remove(node);
					if(logger.isLoggable(Level.INFO)) {
						logger.info("NodeExpirationTimerTask Run NSync["
							+ node + "] removed");
					}
				} else {
					if(logger.isLoggable(Level.FINEST)) {
						logger.finest("node time stamp : " + (node.getTimeStamp() + nodeExpiration) + " , current time : "
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
			if(nodes.size() < 1) {
				nodes.add(pingNode);
				if(logger.isLoggable(Level.INFO)) {
					logger.info("NodeExpirationTimerTask Run NSync["
						+ pingNode + "] added");
				}
				return ;
			}
			SIPNode nodePresent = null;
			Iterator<SIPNode> nodesIterator = nodes.iterator();
			while (nodesIterator.hasNext() && nodePresent == null) {
				SIPNode node = (SIPNode) nodesIterator.next();
				if (node.equals(pingNode)) {
					nodePresent = node;
				}
			}
			// adding done afterwards to avoid ConcurrentModificationException when adding the node while going through the iterator
			if(nodePresent != null) {
				nodePresent.updateTimerStamp();
			} else {
				nodes.add(pingNode);
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
			if(nodes.size() < 1) {
				nodes.remove(pingNode);
				if(logger.isLoggable(Level.INFO)) {
					logger.info("NodeExpirationTimerTask Run NSync["
						+ pingNode + "] forcibly removed due to a clean shutdown of a node");
				}
				return ;
			}
			boolean nodePresent = false;
			Iterator<SIPNode> nodesIterator = nodes.iterator();
			while (nodesIterator.hasNext() && !nodePresent) {
				SIPNode node = (SIPNode) nodesIterator.next();
				if (node.equals(pingNode)) {
					nodePresent = true;
				}
			}
			// removal done afterwards to avoid ConcurrentModificationException when removing the node while goign through the iterator
			if(nodePresent) {
				nodes.remove(pingNode);
				if(logger.isLoggable(Level.INFO)) {
					logger.info("NodeExpirationTimerTask Run NSync["
						+ pingNode + "] forcibly removed due to a clean shutdown of a node. Numbers of nodes present in the balancer : " + nodes.size());
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
		if (value < 150)
			throw new IllegalArgumentException("Value cant be less than 150");
		this.nodeExpiration = value;

	}
	/**
	 * {@inheritDoc}
	 */
	public void setNodeExpirationTaskInterval(long value) {
		if (value < 150)
			throw new IllegalArgumentException("Value cant be less than 150");
		this.nodeInfoExpirationTaskInterval = value;
	}
}

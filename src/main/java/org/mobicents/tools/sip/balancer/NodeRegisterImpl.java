package org.mobicents.tools.sip.balancer;

import java.net.InetAddress;
import java.rmi.AlreadyBoundException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
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
	private long nodeInfoExpirationTaskInterval = 5000;
	private long nodeExpiration = 5100;
	
	private Registry registry;
	private Timer taskTimer = new Timer();
	private TimerTask nodeExpirationTask = null;

	private int pointer = -1;

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
		synchronized (nodes) {
			return new ArrayList<SIPNode>(this.nodes);	
		}
	}
	
	/**
	 * {@inheritDoc}
	 */
	public boolean startServer() {
		logger.info("Node registry starting...");
		try {
			nodes = new ArrayList<SIPNode>();
			gluedSessions = new ConcurrentHashMap<String, SIPNode>();

			register(serverAddress);
			
			this.nodeExpirationTask = new NodeExpirationTimerTask();
			this.taskTimer.scheduleAtFixedRate(this.nodeExpirationTask,
					this.nodeInfoExpirationTaskInterval,
					this.nodeInfoExpirationTaskInterval);
			logger.info("Node expiration task created");
			
			logger.info("Node registry started");
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
		logger.info("Stopping node registry...");
		boolean isDeregistered = deregister(serverAddress);
		boolean taskCancelled = nodeExpirationTask.cancel();
		logger.info("Node Expiration Task cancelled " + taskCancelled);
		synchronized (nodes) {
			nodes.clear();
		}
		nodes = null;
		gluedSessions.clear();
		gluedSessions = null;
		pointer = -1;
		logger.info("Node registry stopped.");
		return isDeregistered;
	}

	
	// ********* CLASS TO BE EXPOSED VIA RMI
	private class RegisterRMIStub extends UnicastRemoteObject implements NodeRegisterRMIStub {

		protected RegisterRMIStub() throws RemoteException {
			super();
		}
		
		public void handlePing(ArrayList<SIPNode> ping) throws RemoteException {
			//CALL METHOD IN REGISTRY
			handlePingInRegister(ping);
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
		gluedSessions.remove(callID);	
	}

	/**
	 * {@inheritDoc}
	 */
	public SIPNode getNextNode() throws IndexOutOfBoundsException {
		synchronized (nodes) {			
			int oldPtr = pointer++;
			if (pointer >= nodes.size())
				pointer = -1;
			SIPNode chosen = this.nodes.get(oldPtr);
			return chosen;	
		}
	}
	
	/**
	 * {@inheritDoc}
	 */
	public SIPNode stickSessionToNode(String callID) {

		SIPNode node = gluedSessions.get(callID);
		
		if(node == null) {
			SIPNode newStickyNode = null;
			for (int i = 0; i < 5 && node == null; i++) {
				try {
					newStickyNode = this.getNextNode();
				} catch (IndexOutOfBoundsException ioobe) {

				}	
			}
			if (newStickyNode  != null) {
				node = gluedSessions.putIfAbsent(callID, newStickyNode);
				if(node == null) {
					node = newStickyNode; 
				}
			}
		}		

		return node;
	}
	
	/**
	 * {@inheritDoc}
	 */
	public SIPNode getGluedNode(String callID) {

		return this.gluedSessions.get(callID);
	}

	class NodeExpirationTimerTask extends TimerTask {
		List<SIPNode> nodesToRemove;
		
		public NodeExpirationTimerTask() {
			nodesToRemove = new ArrayList<SIPNode>();
		}
		
		public void run() {
			logger.info("NodeExpirationTimerTask Running");
			synchronized (nodes) {
				for (int i = 0; i < nodes.size(); i++) {
					SIPNode node = nodes.get(i);
					logger.info("NodeExpirationTimerTask Run Sync["
									+ node + "]");
				
					if (node.getTimeStamp() + nodeExpiration < System
							.currentTimeMillis()) {
						nodesToRemove.add(node);
					}
					logger.info("NodeExpirationTimerTask Run NSync["
									+ node + "]");
				}	
				for (SIPNode node : nodesToRemove) {
					nodes.remove(node);
				}
			}
			nodesToRemove.clear();			
			logger.info("NodeExpirationTimerTask Done");
		}

	}
	
	/**
	 * {@inheritDoc}
	 */
	public void handlePingInRegister(ArrayList<SIPNode> ping) {

		for (SIPNode node : ping) {
			synchronized (nodes) {
				if (nodes.contains(node)) {
					nodes.get(nodes.indexOf(node)).updateTimerStamp();
				} else {
					nodes.add(node);
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

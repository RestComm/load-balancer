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

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import java.net.InetAddress;
import java.nio.charset.Charset;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.commons.validator.routines.InetAddressValidator;
import org.apache.log4j.Logger;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.mobicents.tools.heartbeat.api.IListener;
import org.mobicents.tools.heartbeat.api.IServerHeartbeatService;
import org.mobicents.tools.heartbeat.api.IServerListener;
import org.mobicents.tools.heartbeat.api.Node;
import org.mobicents.tools.heartbeat.api.Packet;
import org.mobicents.tools.heartbeat.api.Protocol;
import org.mobicents.tools.heartbeat.packets.HeartbeatResponsePacket;
import org.mobicents.tools.heartbeat.packets.ShutdownResponsePacket;
import org.mobicents.tools.heartbeat.packets.StartResponsePacket;
import org.mobicents.tools.heartbeat.packets.StopResponsePacket;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

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
public class NodeRegisterImpl  implements NodeRegister, IServerListener {
    private static Logger logger = Logger.getLogger(NodeRegisterImpl.class.getCanonicalName());

    public static final int POINTER_START = 0;
    private long nodeInfoExpirationTaskInterval = 5000;
    private long nodeExpiration = 5100;

    private Timer taskTimer = new Timer();
    private TimerTask nodeExpirationTask = null;
    private InetAddress serverAddress = null;

    private String latestVersion = Integer.MIN_VALUE + "";
    
    BalancerRunner balancerRunner;
    //private ServerController serverController;
    private IServerHeartbeatService heartbeatService;
    private Gson gson = new Gson();


    public NodeRegisterImpl(InetAddress serverAddress) {
        super();
        this.serverAddress = serverAddress;
    }


    /**
     * {@inheritDoc}
     */
    public boolean startRegistry(Integer ... heartbeatPorts) {
    	
        if(logger.isInfoEnabled()) {
            logger.info("Node registry starting...");
        }
        try {
        	try {
    			Class<?> clazz = Class.forName(balancerRunner.balancerContext.nodeCommunicationProtocolClassName);
    			heartbeatService = (IServerHeartbeatService) clazz.newInstance();
    		} catch (Exception e) {
    			throw new RuntimeException("Error loading the node communication protocol: " + balancerRunner.balancerContext.nodeCommunicationProtocolClassName, e);
    		}
            balancerRunner.balancerContext.aliveNodes = new CopyOnWriteArrayList<Node>();
            balancerRunner.balancerContext.jvmRouteToSipNode = new ConcurrentHashMap<String, Node>();
            heartbeatService.init(this,serverAddress,heartbeatPorts);
            heartbeatService.startServer();
            this.nodeExpirationTask = new NodeExpirationTimerTask();
            this.taskTimer.scheduleAtFixedRate(this.nodeExpirationTask, this.nodeInfoExpirationTaskInterval, this.nodeInfoExpirationTaskInterval);
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
        for(Entry<String, InvocationContext> ctxEntry : balancerRunner.contexts.entrySet())
        {
        	for(Entry<KeySip, Node> entry : ctxEntry.getValue().sipNodeMap(true).entrySet())
        	{
        		if(entry.getValue().getProperties().get(Protocol.HEARTBEAT_PORT)!=null)
        			heartbeatService.sendPacket(entry.getValue().getIp(),Integer.parseInt(entry.getValue().getProperties().get(Protocol.HEARTBEAT_PORT)));
        	}
         	for(Entry<KeySip, Node> entry : ctxEntry.getValue().sipNodeMap(false).entrySet())
         	{
         		if(entry.getValue().getProperties().get(Protocol.HEARTBEAT_PORT)!=null)
         			heartbeatService.sendPacket(entry.getValue().getIp(),Integer.parseInt(entry.getValue().getProperties().get(Protocol.HEARTBEAT_PORT)));
         	}
        }
        heartbeatService.stopServer();
        boolean isDeregistered = true;
        boolean taskCancelled = nodeExpirationTask.cancel();
        if(logger.isInfoEnabled()) {
            logger.info("Node Expiration Task cancelled " + taskCancelled);
        }
        
        if(balancerRunner.balancerContext.allNodesEver!=null)
        	balancerRunner.balancerContext.allNodesEver.clear();
        if(logger.isInfoEnabled()) {
            logger.info("Node registry stopped.");
        }
        return isDeregistered;
    }
    

    // ********* CLASS TO BE EXPOSED VIA RMI
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
        public void handlePing(ArrayList<Node> ping) throws RemoteException {
            handlePingInRegister(ping);
        }

        /*
         * (non-Javadoc)
         * @see org.mobicents.tools.sip.balancer.NodeRegisterRMIStub#forceRemoval(java.util.ArrayList)
         */
        public void forceRemoval(ArrayList<Node> ping)
                throws RemoteException {
            forceRemovalInRegister(ping);
        }

        public void switchover(String fromJvmRoute, String toJvmRoute) throws RemoteException {
            jvmRouteSwitchover(fromJvmRoute, toJvmRoute);

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
    public Node stickSessionToNode(String callID, Node sipNode) {

        if(logger.isDebugEnabled()) {
            logger.debug("sticking  CallId " + callID + " to node " + null);
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    public Node getGluedNode(String callID) {
        if(logger.isDebugEnabled()) {
            logger.debug("glueued node " + null + " for CallId " + callID);
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isNodePresent(String host, int port, String transport, String version)  {		
        if(getNode(host, port, transport, version) != null) {
            return true;
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public Node getNode(String host, int port, String transport, String version)  {		
        for (Node node : balancerRunner.balancerContext.aliveNodes) {
        	if(logger.isDebugEnabled()) {
                logger.debug("node to check against " + node);
            }
            // https://telestax.atlassian.net/browse/LB-9 Prevent Routing of Requests to Nodes that exposed null IP address
            if(node != null && node.getIp() != null && node.getIp().equals(host)) {
            	Integer nodePort = Integer.parseInt(node.getProperties().get(transport.toLowerCase() + "Port"));
                if(nodePort != null) {
                	if(nodePort == port) {
                        if(version == null) {
                            return node;
                        } else {
                            String nodeVersion = node.getProperties().get("version");
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
            for (Node node : balancerRunner.balancerContext.aliveNodes) {
                long expirationTime = node.getTimeStamp() + nodeExpiration;
                String nodeHostname = node.getHostName();
                if (expirationTime < System.currentTimeMillis() && !nodeHostname.contains("ExtraServerNode")) {
                    InvocationContext ctx = balancerRunner.getInvocationContext(node.getProperties().get("version"));
                    balancerRunner.balancerContext.aliveNodes.remove(node);
                    String instanceId = node.getProperties().get("Restcomm-Instance-Id");
                    if(instanceId!=null)
                    	ctx.httpNodeMap.remove(instanceId);
                    if(node.getProperties().get("smppPort")!=null)
                    {
                      	ctx.smppNodeMap.remove(new KeySmpp(node));
                    }
                    Boolean isIpV6=LbUtils.isValidInet6Address(node.getIp());        	                    
                    ctx.sipNodeMap(isIpV6).remove(new KeySip(node));
                    ctx.sessionNodeMap(isIpV6).remove(new KeySession(node.getProperties().get(Protocol.SESSION_ID)));
                    ctx.balancerAlgorithm.nodeRemoved(node);
                        logger.warn("NodeExpirationTimerTask Run NSync["
                                + node + "] removed. Last timestamp: " + node.getTimeStamp() + 
                                ", current: " + System.currentTimeMillis()
                                + " diff=" + ((double)System.currentTimeMillis()-node.getTimeStamp() ) +
                                "ms and tolerance=" + nodeExpiration + " ms");
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
    public synchronized void handlePingInRegister(ArrayList<Node> ping) {
        for (Node pingNode : ping) {        	
            if(pingNode.getIp() == null) {
                // https://telestax.atlassian.net/browse/LB-9 Prevent Routing of Requests to Nodes that exposed null IP address 
                logger.warn("[" + pingNode + "] not added as its IP is null, the node is sending bad information");			   
            } else {
            	Boolean isIpV6=LbUtils.isValidInet6Address(pingNode.getIp());
            	Boolean isIpV4=InetAddressValidator.getInstance().isValidInet4Address(pingNode.getIp());
            	if(!isIpV4 && !isIpV6)
            		logger.warn("[" + pingNode + "] not added as its IP is null, the node is sending bad information");
            	else
            	{
            		String version = pingNode.getProperties().get("version");
	                if(version == null) version = "0";
	                InvocationContext ctx = balancerRunner.getInvocationContext(version);
	                                
	                //if bad node changed sessioId it means that the node was restarted so we remove it from map of bad nodes
	                KeySip keySip = new KeySip(pingNode);	                	                
	                if(ctx.sipNodeMap(isIpV6).get(keySip)!=null&&ctx.sipNodeMap(isIpV6).get(keySip).isBad())
	                {
	                	if(ctx.sipNodeMap(isIpV6).get(keySip).getProperties().get("sessionId").equals(pingNode.getProperties().get("sessionId")))
	                		continue;
	                	else
	                		ctx.sipNodeMap(isIpV6).get(keySip).setBad(false);
	                }
	                pingNode.updateTimerStamp();
	                //logger.info("Pingnode updated " + pingNode);
	                if(pingNode.getProperties().get("jvmRoute") != null) {
	                    // Let it leak, we will have 10-100 nodes, not a big deal if it leaks.
	                    // We need info about inactive nodes to do the failover
	                    balancerRunner.balancerContext.jvmRouteToSipNode.put(
	                            pingNode.getProperties().get("jvmRoute"), pingNode);				
	                }

	                Node nodePresent = ctx.sipNodeMap(isIpV6).get(keySip);
	                
	                // adding done afterwards to avoid ConcurrentModificationException when adding the node while going through the iterator
	                if(nodePresent != null) 
	                {
	                    nodePresent.updateTimerStamp();
	                    if(logger.isTraceEnabled()) {
	                        logger.trace("Ping " + nodePresent.getTimeStamp());
	                    }

	                    if(pingNode.getProperties().get("GRACEFUL_SHUTDOWN")!=null&&
	                    		pingNode.getProperties().get("GRACEFUL_SHUTDOWN").equals("true"))
	                    {
	                    	logger.info("LB will exclude node " + nodePresent + " for new calls because of GRACEFUL_SHUTDOWN");
	                    	ctx.sipNodeMap(isIpV6).get(keySip).setGracefulShutdown(true);
	                    }
	                } 
	                else if(pingNode.getProperties().get("GRACEFUL_SHUTDOWN")!=null&&
	                		pingNode.getProperties().get("GRACEFUL_SHUTDOWN").equals("true"))
	                {
	                	if(logger.isDebugEnabled())
	                        logger.debug("Ping from node which LB exclude because of  GRACEFUL_SHUTDOWN : " + pingNode);
	                }
	                else
	                {
	                    Integer current = Integer.parseInt(version);
	                    Integer latest = Integer.parseInt(latestVersion);
	                    latestVersion = Math.max(current, latest) + "";
	                    balancerRunner.balancerContext.aliveNodes.add(pingNode);
	                    ctx.sipNodeMap(isIpV6).put(keySip, pingNode);
	                    String instanceId = pingNode.getProperties().get("Restcomm-Instance-Id");
	                    if(instanceId!=null)
	                    	ctx.httpNodeMap.put(new KeyHttp(instanceId), pingNode);
	                    Integer smppPort = null;
	                    if(pingNode.getProperties().get("smppPort")!=null)
	                    {
	                    	smppPort = Integer.parseInt(pingNode.getProperties().get("smppPort"));
	                    	ctx.smppNodeMap.put(new KeySmpp(pingNode), pingNode);
	                    }
	                    	
	                    
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
        }
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    /**
     * {@inheritDoc}
     */
    public void forceRemovalInRegister(ArrayList<Node> ping) {
        for (Node pingNode : ping) {
            InvocationContext ctx = balancerRunner.getInvocationContext(
                    pingNode.getProperties().get("version"));
            //ctx.nodes.remove(pingNode);
            String instanceId = pingNode.getProperties().get("Restcomm-Instance-Id");
            if(instanceId!=null)
            	ctx.httpNodeMap.remove(instanceId);
            Integer smppPort = null;
            if(pingNode.getProperties().get("smppPort") != null)
            	smppPort = Integer.parseInt(pingNode.getProperties().get("smppPort"));
            if(smppPort!=null)
            	ctx.smppNodeMap.remove(new KeySmpp(pingNode));
            
            Boolean isIpV6=LbUtils.isValidInet6Address(pingNode.getIp());        	
            ctx.sipNodeMap(isIpV6).remove(new KeySip(pingNode));
            boolean nodePresent = false;
            Iterator<Node> nodesIterator = balancerRunner.balancerContext.aliveNodes.iterator();
            while (nodesIterator.hasNext() && !nodePresent) {
                Node node = (Node) nodesIterator.next();
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

    public Node[] getAllNodes() {
        return balancerRunner.balancerContext.aliveNodes.toArray(new Node[]{});
    }

    public Node getNextNode() throws IndexOutOfBoundsException {
        // TODO Auto-generated method stub
        return null;
    }

    public void jvmRouteSwitchover(String fromJvmRoute, String toJvmRoute) {
        for(InvocationContext ctx : balancerRunner.contexts.values()) {
            ctx.balancerAlgorithm.jvmRouteSwitchover(fromJvmRoute, toJvmRoute);
        }
    }


	@Override
	public void responseReceived(JsonObject json) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public synchronized void startRequestReceived(MessageEvent e, JsonObject json) 
	{
		Node node = new Node(json);
        if(node.getIp() == null) 
        {
            logger.warn("[" + node + "] not added as it's IP is null, the node is sending bad information");			   
        } 
        else 
        {
         	Boolean isIpV6=LbUtils.isValidInet6Address(node.getIp());
           	Boolean isIpV4=InetAddressValidator.getInstance().isValidInet4Address(node.getIp());
           	if(!isIpV4 && !isIpV6)
           	{
           		logger.warn("[" + node + "] not added as it's IP is incorrect, the node is sending bad information");
           	}
           	else
           	{
           		String version = node.getProperties().get("version");
	            if(version == null) version = "0";
	            InvocationContext ctx = balancerRunner.getInvocationContext(version);
	            KeySip keySip = new KeySip(node);
	            KeySession keySession = new KeySession(node.getProperties().get(Protocol.SESSION_ID));
	            node.updateTimerStamp();
	            if(node.getProperties().get("jvmRoute") != null) 
	                balancerRunner.balancerContext.jvmRouteToSipNode.put(node.getProperties().get("jvmRoute"), node);				

	            //Node nodePresent = ctx.sipNodeMap(isIpV6).get(keySip);
	            Node nodePresent = ctx.sessionNodeMap(isIpV6).get(keySession);
	                
	            if(nodePresent != null&&!nodePresent.isBad()) 
	            {
	              	logger.warn("LB got start request from existed node " + nodePresent);

	            }else if(nodePresent != null&&nodePresent.isBad())
	            {
	            	logger.info("LB got start request from restarted node " + nodePresent);
	            	nodePresent.setBad(false);
	            	nodePresent.setFailCounter(0);
	            }
	            else
	            {
	            	logger.debug("LB got start request from node " + node);
	                Integer current = Integer.parseInt(version);
	                Integer latest = Integer.parseInt(latestVersion);
	                latestVersion = Math.max(current, latest) + "";
	                balancerRunner.balancerContext.aliveNodes.add(node);
	                ctx.sessionNodeMap(isIpV6).put(keySession, node);
	                ctx.sipNodeMap(isIpV6).put(keySip, node);
	                String instanceId = node.getProperties().get(Protocol.RESTCOMM_INSTANCE_ID);
	                if(instanceId!=null)
	                  	ctx.httpNodeMap.put(new KeyHttp(instanceId), node);
	                if(node.getProperties().get("smppPort")!=null)
	                  	ctx.smppNodeMap.put(new KeySmpp(node), node);
	                 ctx.balancerAlgorithm.nodeAdded(node);
	                 balancerRunner.balancerContext.allNodesEver.add(node);
	                 node.updateTimerStamp();
	                 if(logger.isInfoEnabled())
	                    logger.info("New node added to map of nodes [" + node + "] ");
	                }
            	}
            }
        if(e!=null)
        	writeResponse(e, HttpResponseStatus.OK, Protocol.START, Protocol.OK);
	}

	@Override
	public synchronized void heartbeatRequestReceived(MessageEvent e, JsonObject json) 
	{
		logger.trace("LB got heartbeat from Node : " + json );
		KeySession keySession = new KeySession(json.get(Protocol.SESSION_ID).toString());
		boolean was = false; 
		for(Entry<String, InvocationContext> ctxEntry : balancerRunner.contexts.entrySet())
		{
			InvocationContext ctx = ctxEntry.getValue();
			Node nodePresentIPv4 = ctx.sessionNodeMap(false).get(keySession);
			Node nodePresentIPv6 = null;
			if(nodePresentIPv4!=null)
			{
				nodePresentIPv4.updateTimerStamp();
				was = true;
			}
			else if((nodePresentIPv6 = ctx.sessionNodeMap(true).get(keySession))!=null)
			{
				nodePresentIPv6.updateTimerStamp();
				was = true;
			}
		}
		if(!was)
		{
			logger.error("LB got heartbeat ( " + json + " ) from node which not pesent in maps"); 
		}
		if(e!=null)
			writeResponse(e, HttpResponseStatus.OK, Protocol.HEARTBEAT, Protocol.OK);
	}

	@Override
	public synchronized void shutdownRequestReceived(MessageEvent e, JsonObject json) 
	{
		boolean was = false; 
		logger.info("LB got graceful shutdown from Node : " + json);
		KeySession keySession = new KeySession(json.get(Protocol.SESSION_ID).toString());
		for(Entry<String, InvocationContext> ctxEntry : balancerRunner.contexts.entrySet())
		{
			InvocationContext ctx = ctxEntry.getValue();
			Node nodePresentIPv4 = ctx.sessionNodeMap(false).get(keySession);
			Node nodePresentIPv6 = null;
			if(nodePresentIPv4!=null)
			{
				logger.info("LB will exclude node "+ nodePresentIPv4 +"for new calls because of shutdown request");
				nodePresentIPv4.setGracefulShutdown(true);
				was = true;
			}
			else if((nodePresentIPv6 = balancerRunner.getLatestInvocationContext().sessionNodeMap(true).get(keySession))!=null)
			{
				logger.info("LB will exclude node "+ nodePresentIPv6 +"for new calls because of shutdown request");
				nodePresentIPv6.setGracefulShutdown(true);
				was = true;
			}
		}
		if(!was)
		{
			logger.error("LB got shutdown request ( " + json + " ) from node which not pesent in maps");
		}
		if(e!=null)
			writeResponse(e, HttpResponseStatus.OK, Protocol.SHUTDOWN, Protocol.OK);
	}
	
	@Override
	public synchronized void stopRequestReceived(MessageEvent e, JsonObject json) 
	{
		boolean isIpV6 = false;
		boolean was = false;
		logger.info("LB got stop request from Node : " + json);
		KeySession keySession = new KeySession(json.get(Protocol.SESSION_ID).toString());
		for(Entry<String, InvocationContext> ctxEntry : balancerRunner.contexts.entrySet())
		{
			InvocationContext ctx = ctxEntry.getValue();
			Node nodePresent = null;
			Node nodePresentIpv4 = ctx.sessionNodeMap(true).get(keySession);
			Node nodePresentIpv6 = ctx.sessionNodeMap(false).get(keySession);
			if(nodePresentIpv4!=null)
			{
				isIpV6 = true;
				nodePresent = nodePresentIpv4;
			}
			else if(nodePresentIpv6!=null)
			{
				nodePresent = nodePresentIpv6;
			}
		
			if(nodePresent!=null)
			{
				was = true;
				Node removedNode = ctx.sessionNodeMap(isIpV6).remove(keySession);
				KeySip keySip = new KeySip(removedNode);
				ctx.sipNodeMap(isIpV6).remove(keySip);
				String instanceId = nodePresent.getProperties().get(Protocol.RESTCOMM_INSTANCE_ID);
				if(instanceId!=null)
					ctx.httpNodeMap.remove(instanceId);
				String smppPort = nodePresent.getProperties().get(Protocol.SMPP_PORT);
				if(smppPort!=null)
					ctx.smppNodeMap.remove(new KeySmpp(nodePresent));
				balancerRunner.balancerContext.aliveNodes.remove(nodePresent);
			
				ctx.balancerAlgorithm.nodeRemoved(nodePresent);
				if(logger.isInfoEnabled())
					logger.info(" LB got STOP request from node : " + nodePresent + ". So it will be rmoved : "  + balancerRunner.balancerContext.aliveNodes.size());
			}
		}
		if(!was)
		{
			logger.error("LB got shutdown request ( " + json + " ) from node which not pesent in maps : " + 
					balancerRunner.getLatestInvocationContext().sipNodeMap(isIpV6));
		}
		if(e!=null)
			writeResponse(e, HttpResponseStatus.OK, Protocol.STOP, Protocol.OK);
		
	}
	
	private synchronized void writeResponse(MessageEvent e, HttpResponseStatus status, String command, String responceString) 
    {
		Packet packet = null;
		switch(command)
		{
			case Protocol.HEARTBEAT:
				packet = new HeartbeatResponsePacket(responceString);
				break;
			case Protocol.START:
				packet = new StartResponsePacket(responceString);
				break;
			case Protocol.SHUTDOWN:
				packet = new ShutdownResponsePacket(responceString);
				break;
			case Protocol.STOP:
				packet = new StopResponsePacket(responceString);
				break;
		}
        ChannelBuffer buf = ChannelBuffers.copiedBuffer(gson.toJson(packet), Charset.forName("UTF-8"));
        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, status);
        response.setHeader(HttpHeaders.Names.CONTENT_TYPE, APPLICATION_JSON);
        response.setHeader(HttpHeaders.Names.CONTENT_LENGTH, buf.readableBytes());
        response.setContent(buf);
        ChannelFuture future = e.getChannel().write(response);
        future.addListener(ChannelFutureListener.CLOSE);
    }


	@Override
	public void switchoverRequestReceived(MessageEvent e, JsonObject json) {
		jvmRouteSwitchover(json.get("fromJvmRoute").toString(), json.get("toJvmRoute").toString());
		writeResponse(e, HttpResponseStatus.OK, Protocol.SWITCHOVER, Protocol.OK);
	}

  }
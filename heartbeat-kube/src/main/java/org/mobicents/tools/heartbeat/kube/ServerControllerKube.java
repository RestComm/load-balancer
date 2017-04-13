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
package org.mobicents.tools.heartbeat.kube;

import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Logger;
import org.mobicents.tools.heartbeat.api.HeartbeatRequestPacket;
import org.mobicents.tools.heartbeat.api.IListener;
import org.mobicents.tools.heartbeat.api.IServerHeartbeatService;
import org.mobicents.tools.heartbeat.api.IServerListener;
import org.mobicents.tools.heartbeat.api.Node;
import org.mobicents.tools.heartbeat.api.NodeShutdownRequestPacket;
import org.mobicents.tools.heartbeat.api.Protocol;
import org.mobicents.tools.heartbeat.api.StartRequestPacket;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */
public class ServerControllerKube implements IListener,IServerHeartbeatService<HeartbeatConfigKube> {

	private static Logger logger = Logger.getLogger(ServerControllerKube.class.getCanonicalName());

	private IServerListener listener;
	private Timer timer;
	private KubernetesClient kube;
	private Gson gson = new Gson();
	private JsonParser parser = new JsonParser();
	private ConcurrentHashMap<String, Node> activeNodes = new ConcurrentHashMap<>();
	private String lbIp;
	private long pullPeriod = 3000;
	private String nodeName = "sip-node";
	
	@Override
	public void startServer() 
	{
		timer = new Timer();
		timer.scheduleAtFixedRate(new TimerTask() {
			public void run() {
				PodList pods = kube.pods().list();
				List<Pod> items = pods.getItems();
				 for (Pod pod : items) 
				 {
			        	if(isValidPod(pod))
			        	{
			        		String currSessionId = getSessionId(pod);
			        		Node currNode = activeNodes.get(currSessionId);
			        		if(currNode==null)
			        		{
			        			for(ContainerStatus status : pod.getStatus().getContainerStatuses())
			        			{
			        				if(status.getName().startsWith(nodeName)&&status.getReady())
			        				{
			        					Node newNode = getNodeFromPod(pod);
			        					JsonObject jsonObject = parser.parse(gson.toJson(new StartRequestPacket(newNode))).getAsJsonObject();
			        					listener.startRequestReceived(null, jsonObject);
			        					activeNodes.put(newNode.getProperties().get(Protocol.SESSION_ID), newNode);
			        				}
			        			}
			        		}
			        		else if(!isGracefulShutdown(pod) || currNode.isGracefulShutdown())
			        		{
	        					JsonObject jsonObject = parser.parse(gson.toJson(new HeartbeatRequestPacket(currNode))).getAsJsonObject();
	        					listener.heartbeatRequestReceived(null, jsonObject);
			        		}
			        		else
			        		{
			        			if(!currNode.isGracefulShutdown())
			        			{
			        				currNode.setGracefulShutdown(true);
			        				JsonObject jsonObject = parser.parse(gson.toJson(new NodeShutdownRequestPacket(currNode))).getAsJsonObject();
			        				listener.shutdownRequestReceived(null, jsonObject);
			        			}
			        		}
			        	}
			     }
			}
		}, 2000, pullPeriod);

	}

	@Override
	public void stopServer() 
	{
		timer.cancel();
		activeNodes.clear();
		kube.close();
	}

	@Override
	public void init(IServerListener listener, InetAddress serverAddress, HeartbeatConfigKube config) 
	{		
		this.listener = listener;
		this.lbIp = serverAddress.getHostAddress();
		this.kube = new DefaultKubernetesClient();
		this.pullPeriod = config.getPullPeriod();
		this.nodeName = config.getNodeName();
	}

	@Override
	public void sendPacket(String ip, int parseInt)
	{
		
	}
	
	private Node getNodeFromPod(Pod pod)
	{
		Map<String,String> labels = pod.getMetadata().getLabels();
		PodStatus status = pod.getStatus();
		Node node =  new Node(labels.remove(Protocol.HOST_NAME),status.getPodIP());
		node.getProperties().putAll(labels);
		return node;
	}
	
	private String getSessionId(Pod pod)
	{
		return pod.getMetadata().getLabels().get(Protocol.SESSION_ID);
	}
	
	private boolean isValidPod(Pod pod)
	{
		return pod.getMetadata().getName().startsWith(nodeName) 
				&& pod.getStatus().getPhase().equals("Running")
				&& hasIpOfLB(pod.getMetadata().getLabels().get(Protocol.LB_LABEL));
	}
	
	private boolean isGracefulShutdown(Pod pod)
	{
		if(pod.getMetadata().getLabels().get(Protocol.GRACEFUL_SHUTDOWN)==null)
			return false;
		else
			return Boolean.parseBoolean(pod.getMetadata().getLabels().get(Protocol.GRACEFUL_SHUTDOWN));
	}
	
	private boolean hasIpOfLB(String lbsFromPodString)
	{
		if(lbsFromPodString==null)
			return true;

		String[] lbsFromPod = lbsFromPodString.split(",");
		for(String lbIpFromPod : lbsFromPod)
		{
			if(lbIpFromPod.equals(lbIp))
				return true;
		}
		return false;
		
	}

}
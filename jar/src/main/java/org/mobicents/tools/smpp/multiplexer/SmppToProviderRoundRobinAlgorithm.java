/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2015, Telestax Inc and individual contributors
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
package org.mobicents.tools.smpp.multiplexer;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Logger;
import org.mobicents.tools.heartbeat.api.Node;
import org.mobicents.tools.sip.balancer.KeySmpp;

import com.cloudhopper.smpp.pdu.Pdu;
/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */
public class SmppToProviderRoundRobinAlgorithm extends DefaultSmppAlgorithm
{
	private static final Logger logger = Logger.getLogger(SmppToProviderRoundRobinAlgorithm.class);
	
	protected Iterator<Entry<Long, MClientConnectionImpl>> connectionToProviderIterator = null;
	protected Iterator<Entry<KeySmpp, Node>> nodeIterator = null;

	@Override
	public void processSubmitToNode(ConcurrentHashMap<Long, MServerConnectionImpl> connectionsToNodes, Long serverSessionId, Pdu packet) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public synchronized void processSubmitToProvider(ConcurrentHashMap<Long, MClientConnectionImpl> connectionsToProviders, Long sessionId, Pdu packet) 
	{
		if(connectionToProviderIterator==null)
			connectionToProviderIterator = connectionsToProviders.entrySet().iterator();
		Entry<Long, MClientConnectionImpl> pair = null;
		while(connectionToProviderIterator.hasNext())
		{
			pair = connectionToProviderIterator.next();
			if(connectionsToProviders.containsKey(pair.getKey()))
				pair.getValue().sendSmppRequest(sessionId,packet);
			return;
		}
		connectionToProviderIterator = connectionsToProviders.entrySet().iterator();
		if(connectionToProviderIterator.hasNext())
		{
			pair = connectionToProviderIterator.next();
			pair.getValue().sendSmppRequest(sessionId,packet);
			return;
		}
		else
			throw new RuntimeException("LB does not have connected Providers, but trying send them request");
	}

	@Override
	public synchronized Node processBindToProvider()
	{

		if(invocationContext.smppNodeMap.size() == 0) return null;
		if(nodeIterator==null)
			nodeIterator = invocationContext.smppNodeMap.entrySet().iterator();
		Entry<KeySmpp, Node> pair = null;
		while(nodeIterator.hasNext())
		{
			pair = nodeIterator.next();
			if(invocationContext.smppNodeMap.containsKey(pair.getKey()))
				return pair.getValue();
		}
		nodeIterator = invocationContext.smppNodeMap.entrySet().iterator();
		if(nodeIterator.hasNext())
		{
			pair = nodeIterator.next();
			return pair.getValue();
		}
		else
			return null;
	}
	@Override
	public void init() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void stop() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void configurationChanged() {
		ConcurrentHashMap<KeySmpp, Node> newSmppNodeMap = new ConcurrentHashMap<KeySmpp, Node>();
		String [] s = balancerContext.lbConfig.getSmppConfiguration().getRemoteServers().split(",");
		String [] sTmp = new String[2];
		for(int i = 0; i < s.length; i++)
		{
			sTmp = s[i].split(":");
			Node currNode = new Node("SMPP server " + i, sTmp[0].trim());
			currNode.getProperties().put("smppPort", sTmp[1].trim());
			newSmppNodeMap.put(new KeySmpp(sTmp[0].trim(),Integer.parseInt(sTmp[1].trim())),currNode);
		}
		ConcurrentHashMap<KeySmpp, Node> removedSmppNodes = null; 
		ConcurrentHashMap<KeySmpp, Node> addedSmppNodes = null;
	    logger.info("Nodes removed : " + (removedSmppNodes = mapDifference(newSmppNodeMap, invocationContext.smppNodeMap)).values());
	    for(KeySmpp key:removedSmppNodes.keySet())
	    	invocationContext.smppNodeMap.remove(key);
	    logger.info("Nodes added : " + (addedSmppNodes = mapDifference(invocationContext.smppNodeMap, newSmppNodeMap)).values());
	    invocationContext.smppNodeMap.putAll(addedSmppNodes);
	    logger.info("Updated SMPP node map after changing config file : " + invocationContext.smppNodeMap.values());
	}
	 private ConcurrentHashMap<KeySmpp, Node> mapDifference(ConcurrentHashMap<KeySmpp, Node> left, ConcurrentHashMap<KeySmpp, Node> right) {
		 	ConcurrentHashMap<KeySmpp, Node> difference = new ConcurrentHashMap<>();
	        difference.putAll(left);
	        difference.putAll(right);
	        difference.entrySet().removeAll(left.entrySet());
	        return difference;
	    }
 
}

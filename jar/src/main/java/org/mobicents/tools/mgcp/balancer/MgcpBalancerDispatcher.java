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
package org.mobicents.tools.mgcp.balancer;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.Logger;
import org.mobicents.tools.heartbeat.api.Node;
import org.mobicents.tools.sip.balancer.BalancerRunner;
import org.mobicents.tools.sip.balancer.InvocationContext;
import org.mobicents.tools.heartbeat.api.Protocol;
import org.restcomm.media.client.mgcp.stack.JainMgcpStackProviderImpl;
import org.restcomm.media.client.mgcp.stack.JainMgcpStackImpl;

import jain.protocol.ip.mgcp.JainMgcpCommandEvent;
import jain.protocol.ip.mgcp.JainMgcpEvent;
import jain.protocol.ip.mgcp.JainMgcpResponseEvent;
import jain.protocol.ip.mgcp.message.AuditConnection;
import jain.protocol.ip.mgcp.message.AuditConnectionResponse;
import jain.protocol.ip.mgcp.message.AuditEndpoint;
import jain.protocol.ip.mgcp.message.AuditEndpointResponse;
import jain.protocol.ip.mgcp.message.Constants;
import jain.protocol.ip.mgcp.message.CreateConnection;
import jain.protocol.ip.mgcp.message.CreateConnectionResponse;
import jain.protocol.ip.mgcp.message.DeleteConnection;
import jain.protocol.ip.mgcp.message.DeleteConnectionResponse;
import jain.protocol.ip.mgcp.message.EndpointConfiguration;
import jain.protocol.ip.mgcp.message.EndpointConfigurationResponse;
import jain.protocol.ip.mgcp.message.ModifyConnection;
import jain.protocol.ip.mgcp.message.ModifyConnectionResponse;
import jain.protocol.ip.mgcp.message.NotificationRequest;
import jain.protocol.ip.mgcp.message.NotificationRequestResponse;
import jain.protocol.ip.mgcp.message.Notify;
import jain.protocol.ip.mgcp.message.NotifyResponse;
import jain.protocol.ip.mgcp.message.RestartInProgress;
import jain.protocol.ip.mgcp.message.RestartInProgressResponse;
import jain.protocol.ip.mgcp.message.parms.ConflictingParameterException;
import jain.protocol.ip.mgcp.message.parms.ConnectionIdentifier;
import jain.protocol.ip.mgcp.message.parms.EndpointIdentifier;
import jain.protocol.ip.mgcp.message.parms.NotifiedEntity;
import jain.protocol.ip.mgcp.message.parms.RequestIdentifier;
/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */
public class MgcpBalancerDispatcher {
	
	private static final Logger logger = Logger.getLogger(MgcpBalancerDispatcher.class);

	private JainMgcpStackProviderImpl externalProvider;
	private JainMgcpStackProviderImpl internalProvider;
	private BalancerRunner balancerRunner;
	private String lBHost;
	private NotifiedEntity lbNotifiedEntity;
	
	private ConcurrentHashMap<String, MgwHost> mgwHosts = new ConcurrentHashMap<>();
	
	private ConcurrentHashMap<String, Call> callMap = new ConcurrentHashMap<>();
	
	private ConcurrentHashMap<Connection, Connection> connectionMap = new ConcurrentHashMap<>();
	private ConcurrentHashMap<Connection, Connection> reverseConnectionMap = new ConcurrentHashMap<>();
	
	private ConcurrentHashMap <Endpoint, Endpoint> endpointMap = new ConcurrentHashMap<>();
	private ConcurrentHashMap <Endpoint, Endpoint> reverseEndpointMap = new ConcurrentHashMap<>();
	
	private ConcurrentHashMap <Transaction, Transaction> transactionMap = new ConcurrentHashMap <>();
	private ConcurrentHashMap <Transaction, Transaction> reverseTransactionMap = new ConcurrentHashMap <>();
	
	private ConcurrentHashMap <Request, Request> reverseRequestMap = new ConcurrentHashMap <>();
	
	private AtomicInteger connectionIdCounter = new AtomicInteger(1);
	private AtomicInteger endpointIdCounter = new AtomicInteger(1);
	private AtomicInteger transactionIdCounterInternal = new AtomicInteger(1);
	private AtomicInteger transactionIdCounterExternal = new AtomicInteger(1);
	private AtomicInteger requestIdCounter = new AtomicInteger(1);
	
	public MgcpBalancerDispatcher(BalancerRunner balancerRunner, String lBHost, JainMgcpStackProviderImpl externalProvider, JainMgcpStackProviderImpl internalProvider) 
	{
		this.externalProvider = externalProvider;
		this.internalProvider = internalProvider;
		this.lBHost = lBHost;
		this.balancerRunner = balancerRunner;
		this.lbNotifiedEntity = new NotifiedEntity("restcommlb",((JainMgcpStackImpl)internalProvider.getJainMgcpStack()).getAddress().getHostAddress(), internalProvider.getJainMgcpStack().getPort());
	}

	public void processExternalMgcpCommandEvent(JainMgcpCommandEvent event) 
	{
		if(logger.isInfoEnabled())
			logger.info("Process external request event : " + event);
		InvocationContext invocationContext = balancerRunner.getLatestInvocationContext();
		String host= null;
		EndpointIdentifier endpointIdExternal = null;
		EndpointIdentifier secondEndpointIdExternal = null;
		Transaction transactionExternal = null;
		Transaction transactionInternal = null;
		Request requestExternal = null;
		Request requestInternal = null;
		EndpointIdentifier endpointId = null;
		ConnectionIdentifier connectionIdExternal = null;
		Endpoint endpointInternal;
		Call call = null;
		switch (event.getObjectIdentifier()) 
		{
			case Constants.CMD_CREATE_CONNECTION:
				CreateConnection crcx = (CreateConnection) event;
				String notifiedHost = crcx.getNotifiedEntity().getDomainName()+":"+ crcx.getNotifiedEntity().getPortNumber();
				call = callMap.get(crcx.getCallIdentifier().toString());
				if(call==null&&crcx.getEndpointIdentifier().getLocalEndpointName().contains("$"))
				{
					//very first request
					Node node = invocationContext.balancerAlgorithm.processMgcpRequest(event, false);
					host = node.getIp()+":"+node.getProperties().get(Protocol.MGCP_PORT);
					//create call
					Call newCall = new Call(crcx.getCallIdentifier().toString(),notifiedHost);
					callMap.putIfAbsent(crcx.getCallIdentifier().toString(),newCall);
					//add call to host
					MgwHost currentHost = mgwHosts.get(host);
					if(currentHost!=null)
					{
						currentHost.getCalls().add(crcx.getCallIdentifier().toString());
					}
					else
					{
						MgwHost newMgwHost = new MgwHost(node.getIp(), Integer.parseInt(node.getProperties().get(Protocol.MGCP_PORT)));
						newMgwHost.getCalls().add(crcx.getCallIdentifier().toString());
						mgwHosts.put(host,newMgwHost);
					}
					//modify endpoints and transaction and send
					crcx.setEndpointIdentifier(new EndpointIdentifier(crcx.getEndpointIdentifier().getLocalEndpointName(), host));
					if(crcx.getSecondEndpointIdentifier()!=null)
					{
						secondEndpointIdExternal = crcx.getSecondEndpointIdentifier();
						try {
							crcx.setSecondEndpointIdentifier(new EndpointIdentifier(secondEndpointIdExternal.getLocalEndpointName(), host));
						} catch (ConflictingParameterException | IllegalArgumentException e) {
							e.printStackTrace();
						}
					}
					//modify transaction
					transactionExternal = new Transaction(crcx.getTransactionHandle(), crcx.getNotifiedEntity(), newCall);
					transactionInternal = new Transaction(transactionIdCounterInternal.getAndIncrement(), crcx.getNotifiedEntity(), null);
					transactionIdCounterInternal.compareAndSet(Integer.MAX_VALUE, 1);
					reverseTransactionMap.putIfAbsent(transactionInternal, transactionExternal);
					crcx.setTransactionHandle(transactionInternal.getTransactionId());
					crcx.setNotifiedEntity(lbNotifiedEntity);
					internalProvider.sendMgcpEvents(new JainMgcpEvent[] { crcx });
					break;
				}
				else if(crcx.getEndpointIdentifier()!=null&&!crcx.getEndpointIdentifier().getLocalEndpointName().contains("$"))
				{
					endpointIdExternal = crcx.getEndpointIdentifier();
					endpointInternal = endpointMap.get(new Endpoint(endpointIdExternal,crcx.getNotifiedEntity()));
					if(endpointInternal!=null)
					{
						//modify endpoint in request
						crcx.setEndpointIdentifier(endpointInternal.getEndpointId());
						host = endpointInternal.getEndpointId().getDomainName();
					}
					else
					{
						logger.error("MGCP LB got command with unknown endpoint identifier");
						break;
					}
				}
			try {
				if(crcx.getSecondEndpointIdentifier()!=null)
				{
					secondEndpointIdExternal = crcx.getSecondEndpointIdentifier();
					Endpoint secondEndpointExternal = endpointMap.get(new Endpoint(endpointIdExternal,crcx.getNotifiedEntity()));
					crcx.setSecondEndpointIdentifier(secondEndpointExternal.getEndpointId());
				}
				
			} catch (ConflictingParameterException | IllegalArgumentException e) {
				e.printStackTrace();
			}
			//store notified entity for endpoint and change it to LB
			//modify transaction
			transactionExternal = new Transaction(crcx.getTransactionHandle(), crcx.getNotifiedEntity(), call);
			transactionInternal = new Transaction(transactionIdCounterInternal.getAndIncrement(), crcx.getNotifiedEntity(), call);
			transactionIdCounterInternal.compareAndSet(Integer.MAX_VALUE, 1);
			reverseTransactionMap.putIfAbsent(transactionInternal, transactionExternal);
			crcx.setTransactionHandle(transactionInternal.getTransactionId());
			crcx.setNotifiedEntity(lbNotifiedEntity);
			internalProvider.sendMgcpEvents(new JainMgcpEvent[] { crcx });
				break;
			case Constants.CMD_MODIFY_CONNECTION:
				ModifyConnection mdcx = (ModifyConnection) event;
				endpointId = mdcx.getEndpointIdentifier();
				endpointInternal = endpointMap.get(new Endpoint(endpointId));

				if(endpointInternal!=null)
					mdcx.setEndpointIdentifier(endpointInternal.getEndpointId());
				else
					logger.error("MDCX has endpointId but LB does not have it in map");
				
				connectionIdExternal = mdcx.getConnectionIdentifier();
				if(connectionIdExternal!=null)
				{
					Connection connectionInternal = connectionMap.get(new Connection(connectionIdExternal.toString(), lBHost, null));
					if(connectionInternal!=null)
						mdcx.setConnectionIdentifier(new ConnectionIdentifier(connectionInternal.getConnectionId()));
					else
						logger.error("MDCX has connectionId but LB does not have it in map");
				}
				//modify transaction
				transactionExternal = new Transaction(mdcx.getTransactionHandle());
				transactionInternal = new Transaction(transactionIdCounterInternal.getAndIncrement());
				transactionIdCounterInternal.compareAndSet(Integer.MAX_VALUE, 1);
				reverseTransactionMap.putIfAbsent(transactionInternal, transactionExternal);
				mdcx.setTransactionHandle(transactionInternal.getTransactionId());
				mdcx.setNotifiedEntity(lbNotifiedEntity);
				internalProvider.sendMgcpEvents(new JainMgcpEvent[] { mdcx });
				break;
			case Constants.CMD_DELETE_CONNECTION:
				DeleteConnection dlcx = (DeleteConnection) event;
				endpointIdExternal = dlcx.getEndpointIdentifier();
				endpointInternal = endpointMap.get(new Endpoint(endpointIdExternal));
				if(endpointInternal!=null)
					dlcx.setEndpointIdentifier(endpointInternal.getEndpointId());
				else
					logger.error("DLCX has endpointId but LB does not have it in map");
				
				connectionIdExternal = dlcx.getConnectionIdentifier();
				if(connectionIdExternal!=null)
				{
					Connection connectionInternal = connectionMap.remove(new Connection(connectionIdExternal.toString(), lBHost, null));
					Connection connectionExternal =reverseConnectionMap.remove(connectionInternal);
					if(connectionInternal!=null)
						dlcx.setConnectionIdentifier(new ConnectionIdentifier(connectionInternal.getConnectionId()));
					else
						logger.error("DLCX has connectionId but LB does not have it in map");
					//remove connections from Call
					Connection removedExternalConnection = callMap.get(connectionExternal.getCall().getCallId()).getConnections().remove("ex" + connectionIdExternal);
					
					if(logger.isDebugEnabled()&&removedExternalConnection!=null)
						logger.debug("external connection : " +removedExternalConnection.getConnectionId() +" was removed from call : " + connectionExternal.getCall().getCallId());
					
					Connection removedInternalConnection = callMap.get(connectionInternal.getCall().getCallId()).getConnections().remove("in" + connectionInternal.getConnectionId());
					
					if(logger.isDebugEnabled()&&removedInternalConnection!=null)
						logger.debug("internal connection : " +removedInternalConnection.getConnectionId() +" was removed from call : " + connectionInternal.getCall().getCallId());
					//remove connections from internal endpoints
					
					Endpoint endpointInt = endpointMap.get(connectionExternal.getEndpoint());
					removedInternalConnection = endpointInt.getConnections().remove("in" + connectionInternal.getConnectionId());

					if(logger.isDebugEnabled()&&removedInternalConnection!=null)
						logger.debug("internal connection : " +removedInternalConnection.getConnectionId() +" was removed from endpoint : " + endpointInt.getEndpointId());
					//remove connections from external endpoints
					Endpoint endpointExt = reverseEndpointMap.get(connectionInternal.getEndpoint());
					removedExternalConnection = endpointExt.getConnections().remove("ex" + connectionExternal.getConnectionId());

					if(logger.isDebugEnabled()&&removedExternalConnection!=null)
						logger.debug("external connection : " +removedExternalConnection.getConnectionId() +" was removed from endpoint : " + endpointExt.getEndpointId());

					//check has endpoint and calls connections or we should remove it
					if(callMap.get(connectionExternal.getCall().getCallId()).getConnections().isEmpty())
					{
						//remove call from callMap
						Call removedCall = callMap.remove(connectionExternal.getCall().getCallId());
						//remove call from mgwHosts
						mgwHosts.get(removedCall.getHost()).getCalls().remove(removedCall.getCallId());
						if(logger.isDebugEnabled())
							logger.debug("Call [" +removedCall.getCallId() + "] was removed due to there is no more connections");
					}
					if(endpointInt.getConnections().isEmpty())
					{
						//remove endpoints external and internal
						Endpoint removedEndpointExternal = reverseEndpointMap.remove(endpointInt);
						Endpoint removedEndpointInternal = endpointMap.remove(endpointExt);
						if(logger.isDebugEnabled())
							logger.debug("Endpoints external [" +removedEndpointExternal.getEndpointId() + "] and internal [" + removedEndpointInternal +"] was removed due to there is no more connections");
					}
				}
				else if(dlcx.getCallIdentifier()!=null)
				{
					//remove endpoints
					Endpoint endpointInt = endpointMap.remove(new Endpoint(endpointIdExternal));
					Endpoint endpointExt = reverseEndpointMap.remove(endpointInt);
					if(logger.isDebugEnabled())
					{
						logger.debug("External endpoint was removed due to DLCX : " + endpointExt.getEndpointId());
						logger.debug("Internal endpoint was removed due to DLCX : " + endpointInt.getEndpointId());
					}
					//clean connections map
					Connection removedInternalConnection = null;
					for(Connection connection : endpointExt.getConnections().values())
					{
						removedInternalConnection = connectionMap.remove(new Connection(connection.getConnectionId(), lBHost, null));
						if(logger.isDebugEnabled())
							logger.debug("Internal connection was removed due to DLCX : " + removedInternalConnection);
						Connection removedExternalConnection = reverseConnectionMap.remove(removedInternalConnection);
						if(logger.isDebugEnabled())
							logger.debug("External connection was removed due to DLCX : " + removedExternalConnection);
						//remove connections from Call
						Connection removedExternalCon = callMap.get(dlcx.getCallIdentifier().toString()).getConnections().remove("ex" + removedExternalConnection.getConnectionId());
						
						if(logger.isDebugEnabled()&&removedExternalCon!=null)
							logger.debug("external connection : " +removedExternalCon.getConnectionId() +" was removed from call : " + dlcx.getCallIdentifier());
						
						Connection removedInternalCon = callMap.get(dlcx.getCallIdentifier().toString()).getConnections().remove("in" + removedInternalConnection.getConnectionId());
						
						if(logger.isDebugEnabled()&&removedInternalCon!=null)
							logger.debug("internal connection : " +removedInternalCon.getConnectionId() +" was removed from call : " + dlcx.getCallIdentifier());
					}
					if(callMap.get(dlcx.getCallIdentifier().toString()).getConnections().isEmpty())
					{
						Call removedCall = callMap.remove(dlcx.getCallIdentifier().toString());
						mgwHosts.get(removedCall.getHost()).getCalls().remove(removedCall.getCallId());
						if(logger.isDebugEnabled())
							logger.debug("Call ["+removedCall.getCallId()+"] was removed from map because it does not have connections");
					}
				}
				else
				{
					//remove endpoints
					Endpoint endpointInt = endpointMap.remove(new Endpoint(endpointIdExternal));
					Endpoint endpointExt = reverseEndpointMap.remove(endpointInt);
					if(logger.isDebugEnabled())
					{
						logger.debug("External endpoint was removed due to DLCX : " + endpointExt.getEndpointId());
						logger.debug("Internal endpoint was removed due to DLCX : " + endpointInt.getEndpointId());
					}
					//clean connections map
					Connection removedInternalConnection = null;
					for(Connection connection : endpointExt.getConnections().values())
					{
						removedInternalConnection = connectionMap.remove(new Connection(connection.getConnectionId(), lBHost, null));
						if(logger.isDebugEnabled())
							logger.debug("Internal connection was removed due to DLCX : " + removedInternalConnection);
						Connection removedExternalConnection = reverseConnectionMap.remove(removedInternalConnection);
						if(logger.isDebugEnabled())
							logger.debug("External connection was removed due to DLCX : " + removedExternalConnection);
						//remove connections from Call
						
						
						Connection removedExternalCon = callMap.get(removedExternalConnection.getCall().getCallId()).getConnections().remove("ex" + removedExternalConnection.getConnectionId());
						if(logger.isDebugEnabled()&&removedExternalCon!=null)
							logger.debug("external connection : " +removedExternalCon.getConnectionId() +" was removed from call : " + removedExternalConnection.getCall().getCallId());
						
						Connection removedInternalCon = callMap.get(removedInternalConnection.getCall().getCallId()).getConnections().remove("in" + removedInternalConnection.getConnectionId());
						if(logger.isDebugEnabled()&&removedInternalCon!=null)
							logger.debug("internal connection : " +removedInternalCon.getConnectionId() +" was removed from call : " + removedInternalConnection.getCall().getCallId());
					}
					if(callMap.get(removedInternalConnection.getCall().getCallId()).getConnections().isEmpty())
					{
						Call removedCall = callMap.remove(removedInternalConnection.getCall().getCallId());
						mgwHosts.get(removedCall.getHost()).getCalls().remove(removedCall.getCallId());
						if(logger.isDebugEnabled())
							logger.debug("Call ["+removedCall.getCallId()+"] was removed from map because it does not have connections");
					}
				}
				//modify transaction
				transactionExternal = new Transaction(dlcx.getTransactionHandle());
				transactionInternal = new Transaction(transactionIdCounterInternal.getAndIncrement());
				transactionIdCounterInternal.compareAndSet(Integer.MAX_VALUE, 1);
				reverseTransactionMap.putIfAbsent(transactionInternal, transactionExternal);
				dlcx.setTransactionHandle(transactionInternal.getTransactionId());
				internalProvider.sendMgcpEvents(new JainMgcpEvent[] { dlcx });
				break;
			case Constants.CMD_AUDIT_CONNECTION:
				AuditConnection aucx = (AuditConnection)event;
				endpointId = aucx.getEndpointIdentifier();
				endpointInternal = endpointMap.get(new Endpoint(endpointId));
				
				if(endpointInternal!=null)
					aucx.setEndpointIdentifier(endpointInternal.getEndpointId());
				else
					logger.error("AUCX has endpointId but LB does not have it in map");
				
				connectionIdExternal = aucx.getConnectionIdentifier();
				if(connectionIdExternal!=null)
				{
					Connection connectionInternal = connectionMap.get(new Connection(connectionIdExternal.toString(), lBHost, null));
					if(connectionInternal!=null)
						aucx.setConnectionIdentifier(new ConnectionIdentifier(connectionInternal.getConnectionId()));
					else
						logger.error("AUCX has connectionId but LB does not have it in map");
				}
				//modify transaction
				transactionExternal = new Transaction(aucx.getTransactionHandle());
				transactionInternal = new Transaction(transactionIdCounterInternal.getAndIncrement());
				transactionIdCounterInternal.compareAndSet(Integer.MAX_VALUE, 1);
				reverseTransactionMap.putIfAbsent(transactionInternal, transactionExternal);
				aucx.setTransactionHandle(transactionInternal.getTransactionId());
				internalProvider.sendMgcpEvents(new JainMgcpEvent[] { aucx });
				break;
			case Constants.CMD_AUDIT_ENDPOINT:
				AuditEndpoint auep = (AuditEndpoint)event;
				endpointId = auep.getEndpointIdentifier();
				endpointInternal = endpointMap.get(new Endpoint(endpointId));
				if(endpointInternal!=null)
					auep.setEndpointIdentifier(endpointInternal.getEndpointId());
				else
					logger.error("AUEP has endpointId but LB does not have it in map");
				//modify transaction
				transactionExternal = new Transaction(auep.getTransactionHandle());
				transactionInternal = new Transaction(transactionIdCounterInternal.getAndIncrement());
				transactionIdCounterInternal.compareAndSet(Integer.MAX_VALUE, 1);
				reverseTransactionMap.putIfAbsent(transactionInternal, transactionExternal);
				auep.setTransactionHandle(transactionInternal.getTransactionId());
				internalProvider.sendMgcpEvents(new JainMgcpEvent[] { auep });
				break;
			case Constants.CMD_NOTIFICATION_REQUEST:
				NotificationRequest rqnt = (NotificationRequest)event;
				endpointId = rqnt.getEndpointIdentifier();
				endpointInternal = endpointMap.get(new Endpoint(endpointId));
				if(endpointInternal!=null)
					rqnt.setEndpointIdentifier(endpointInternal.getEndpointId());
				else
					logger.error("RQNT has endpointId but LB does not have it in map");
				//modify request Id
				if(rqnt.getRequestIdentifier()!=null)
				{
					requestExternal = new Request(rqnt.getRequestIdentifier().toString(), rqnt.getNotifiedEntity().getDomainName()+":"+ rqnt.getNotifiedEntity().getPortNumber());
					requestInternal = new Request(""+requestIdCounter.getAndIncrement(), null);
					requestIdCounter.compareAndSet(Integer.MAX_VALUE, 1);
				}
				reverseRequestMap.put(requestInternal, requestExternal);
				rqnt.setRequestIdentifier(new RequestIdentifier(requestInternal.getRequestId()));
				//modify transaction
				transactionExternal = new Transaction(rqnt.getTransactionHandle());
				transactionInternal = new Transaction(transactionIdCounterInternal.getAndIncrement());
				transactionIdCounterInternal.compareAndSet(Integer.MAX_VALUE, 1);
				reverseTransactionMap.putIfAbsent(transactionInternal, transactionExternal);
				rqnt.setTransactionHandle(transactionInternal.getTransactionId());
				rqnt.setNotifiedEntity(lbNotifiedEntity);
				internalProvider.sendMgcpEvents(new JainMgcpEvent[] { rqnt });
				break;
			case Constants.CMD_ENDPOINT_CONFIGURATION:
				EndpointConfiguration epcf = (EndpointConfiguration)event;
				endpointId = epcf.getEndpointIdentifier();
				endpointInternal = endpointMap.get(new Endpoint(endpointId));
				if(endpointInternal!=null)
					epcf.setEndpointIdentifier(endpointInternal.getEndpointId());
				else
					logger.error("EPCF has endpointId but LB does not have it in map");
				//modify transaction
				transactionExternal = new Transaction(epcf.getTransactionHandle());
				transactionInternal = new Transaction(transactionIdCounterInternal.getAndIncrement());
				transactionIdCounterInternal.compareAndSet(Integer.MAX_VALUE, 1);
				reverseTransactionMap.putIfAbsent(transactionInternal, transactionExternal);
				epcf.setTransactionHandle(transactionInternal.getTransactionId());
				internalProvider.sendMgcpEvents(new JainMgcpEvent[] { epcf });
 				break;
			default:
				logger.warn("This COMMAND is unexpected from external side" + event);
				break;
		}
	}

	public void processExternalMgcpResponseEvent(JainMgcpResponseEvent event) 
	{
		Transaction transactionInternal = null;
		if(logger.isInfoEnabled())
			logger.info("Process external responce event : " + event);
		switch (event.getObjectIdentifier()) 
		{
		case Constants.RESP_RESTART_IN_PROGRESS:
			RestartInProgressResponse rsipResponse = (RestartInProgressResponse)event;
			transactionInternal = transactionMap.remove(new Transaction(rsipResponse.getTransactionHandle()));
			rsipResponse.setTransactionHandle(transactionInternal.getTransactionId());
			internalProvider.sendMgcpEvents(new JainMgcpEvent[] { rsipResponse });
			break;
		case Constants.RESP_DELETE_CONNECTION:
			DeleteConnectionResponse dlcxresponse = (DeleteConnectionResponse)event;
			transactionInternal = transactionMap.remove(new Transaction(dlcxresponse.getTransactionHandle()));
			if(transactionInternal!=null)
			{
				dlcxresponse.setTransactionHandle(transactionInternal.getTransactionId());
				internalProvider.sendMgcpEvents(new JainMgcpEvent[] { dlcxresponse });
			}
			else
				logger.info("MGCP LB got DLCX response with transactionId which not present in map. "
						+ "This can be due to LB send its own DLCX command, because of Node was removed");
				
			break;
		case Constants.RESP_NOTIFY:
			NotifyResponse ntfyResponse = (NotifyResponse)event;
			transactionInternal = transactionMap.remove(new Transaction(ntfyResponse.getTransactionHandle()));
			ntfyResponse.setTransactionHandle(transactionInternal.getTransactionId());
			internalProvider.sendMgcpEvents(new JainMgcpEvent[] { ntfyResponse });
			break;
		 default:
			logger.warn("This COMMAND is unexpected from internal side" + event);
			break;
		}
	}

	public void processInternalMgcpCommandEvent(JainMgcpCommandEvent event) 
	{
		Transaction transactionExternal = null;
		Transaction transactionInternal = null;
		InetAddress inetAddress = null;
		String address = null;
		int port = -1;
		if(logger.isInfoEnabled())
			logger.info("Process internal request event : " + event);
		switch (event.getObjectIdentifier()) 
		{
		case Constants.CMD_RESTART_IN_PROGRESS:
			RestartInProgress rsip = (RestartInProgress) event;
			if(rsip.getEndpointIdentifier()!=null)
			{
				EndpointIdentifier endpointIdentifierInternal = rsip.getEndpointIdentifier();
				Endpoint eidExternal = reverseEndpointMap.remove(new Endpoint(endpointIdentifierInternal, null));
				if(eidExternal!=null)
				{
					rsip.setEndpointIdentifier(eidExternal.getEndpointId());
					if(eidExternal.getNotifiedEntity()!=null)
					{
						address = eidExternal.getNotifiedEntity().getDomainName();
						port = eidExternal.getNotifiedEntity().getPortNumber();
					}
				}
				else
				{
					logger.error("RSIP command has endpointId but LB does not have it in reverseEndpointMap");
					break;
				}
				
				//remove endpoints
				Endpoint endpointInt = endpointMap.remove(eidExternal);
				if(logger.isDebugEnabled())
				{
					logger.debug("External endpoint was removed due to DLCX from MGW: " + eidExternal.getEndpointId());
					logger.debug("Internal endpoint was removed due to DLCX from MGW: " + endpointInt.getEndpointId());
				}
				//clean connections map
				Connection removedInternalConnection = null;
				for(Connection connection : eidExternal.getConnections().values())
				{
					removedInternalConnection = connectionMap.remove(new Connection(connection.getConnectionId(), lBHost, null));
					if(logger.isDebugEnabled())
						logger.debug("Internal connection was removed due to DLCX from MGW: " + removedInternalConnection);
					Connection removedExternalConnection = reverseConnectionMap.remove(removedInternalConnection);
					if(logger.isDebugEnabled())
						logger.debug("External connection was removed due to DLCX from MGW: " + removedExternalConnection);
					//remove connections from Call
					Connection removedExternalCon = callMap.get(removedExternalConnection.getCall().getCallId()).getConnections().remove("ex" + removedExternalConnection.getConnectionId());
					if(logger.isDebugEnabled()&&removedExternalCon!=null)
						logger.debug("external connection : " +removedExternalCon.getConnectionId() +" was removed from call : " + removedExternalConnection.getCall().getCallId());
					Connection removedInternalCon = callMap.get(removedInternalConnection.getCall().getCallId()).getConnections().remove("in" + removedInternalConnection.getConnectionId());
					if(logger.isDebugEnabled()&&removedInternalCon!=null)
						logger.debug("internal connection : " +removedInternalCon.getConnectionId() +" was removed from call : " + removedInternalConnection.getCall().getCallId());
				}
				if(callMap.get(removedInternalConnection.getCall().getCallId()).getConnections().isEmpty())
				{
					Call removedCall = callMap.remove(removedInternalConnection.getCall().getCallId());
					mgwHosts.get(removedCall.getHost()).getCalls().remove(removedCall.getCallId());
					if(logger.isDebugEnabled())
						logger.debug("Call ["+removedCall.getCallId()+"] was removed from map because it does not have connections");
				}
			}
			if(address==null)
			{
				logger.error("LB can't get address of CA for RSIP command from MGW!!!");
				break;
			} 
			else
			{
				try {
					inetAddress = InetAddress.getByName(address);
				} catch (UnknownHostException e) {
					logger.error("LB can't get address of CA because of error : " + e.getMessage());
					break;
				}
				
			}
			//modify transaction
			transactionInternal = new Transaction(rsip.getTransactionHandle());
			transactionExternal = new Transaction(transactionIdCounterExternal.getAndIncrement());
			transactionIdCounterExternal.compareAndSet(Integer.MAX_VALUE, 1);
			transactionMap.putIfAbsent(transactionExternal,transactionInternal);
			rsip.setTransactionHandle(transactionExternal.getTransactionId());
			externalProvider.sendMgcpEvents(new JainMgcpEvent[] { rsip }, inetAddress, port);
			break;
		case Constants.CMD_DELETE_CONNECTION:
			DeleteConnection dlcx = (DeleteConnection)event;
			//modify endpoint
			EndpointIdentifier endpointIdentifierInternal = null;
			if(dlcx.getEndpointIdentifier()!=null)
			{
				endpointIdentifierInternal = dlcx.getEndpointIdentifier();
				Endpoint eidExternal = reverseEndpointMap.get(new Endpoint(endpointIdentifierInternal, null));
				if(eidExternal!=null)
				{
					dlcx.setEndpointIdentifier(eidExternal.getEndpointId());
					if(eidExternal.getNotifiedEntity()!=null)
					{
						address = eidExternal.getNotifiedEntity().getDomainName();
						port = eidExternal.getNotifiedEntity().getPortNumber();
					}
				}
				else
				{
					logger.error("DLCX from MGW command has endpointId but LB does not have it in reverseEndpointMap");
					break;
				}
			}
			//modify connection ID
			ConnectionIdentifier connectionIdentifierInternal = dlcx.getConnectionIdentifier();
			if(connectionIdentifierInternal!=null)
			{
				Connection connectionExternal = reverseConnectionMap.get(new Connection(connectionIdentifierInternal.toString(), endpointIdentifierInternal.getDomainName(), null));
				dlcx.setConnectionIdentifier(new ConnectionIdentifier(connectionExternal.getConnectionId()));
				if(address==null&&connectionExternal.getNotifiedEntity()!=null)
				{
					address = connectionExternal.getNotifiedEntity().getDomainName();
					port = connectionExternal.getNotifiedEntity().getPortNumber();
				}
			}
			if(address==null)
			{
				logger.error("LB can't get address of CA for DLCX command from MGW!!!");
				break;
			}
			else
			{
				try {
					inetAddress = InetAddress.getByName(address);
				} catch (UnknownHostException e) {
					logger.error("LB can't get address of CA because of error : " + e.getMessage());
					break;
				}
			}
			//clean maps
			if(connectionIdentifierInternal!=null)
			{
				Connection connectionExternal = reverseConnectionMap.remove(new Connection(connectionIdentifierInternal.toString(), endpointIdentifierInternal.getDomainName() , null));
				Connection connectionInternal = connectionMap.remove(connectionExternal);
				//remove connections from Call
				Connection removedExternalConnection = callMap.get(connectionExternal.getCall().getCallId()).getConnections().remove("ex" + connectionExternal.getConnectionId());
				if(logger.isDebugEnabled()&&removedExternalConnection!=null)
					logger.debug("external connection : " +removedExternalConnection.getConnectionId() +" was removed from call : " + connectionExternal.getCall().getCallId());
				Connection removedInternalConnection = callMap.get(connectionInternal.getCall().getCallId()).getConnections().remove("in" + connectionInternal.getConnectionId());
				if(logger.isDebugEnabled()&&removedInternalConnection!=null)
					logger.debug("internal connection : " +removedInternalConnection.getConnectionId() +" was removed from call : " + connectionInternal.getCall().getCallId());
				//remove connections from internal endpoints
				Endpoint endpointInt = endpointMap.get(connectionExternal.getEndpoint());
				removedInternalConnection = endpointInt.getConnections().remove("in" + connectionInternal.getConnectionId());
				if(logger.isDebugEnabled()&&removedInternalConnection!=null)
					logger.debug("internal connection : " +removedInternalConnection.getConnectionId() +" was removed from endpoint : " + endpointInt.getEndpointId());
				//remove connections from external endpoints
				Endpoint endpointExt = reverseEndpointMap.get(connectionInternal.getEndpoint());
				removedExternalConnection = endpointExt.getConnections().remove("ex" + connectionExternal.getConnectionId());
				if(logger.isDebugEnabled()&&removedExternalConnection!=null)
					logger.debug("external connection : " +removedExternalConnection.getConnectionId() +" was removed from endpoint : " + endpointExt.getEndpointId());
				//check has endpoint and calls connections or we should remove it
				if(callMap.get(connectionExternal.getCall().getCallId()).getConnections().isEmpty())
				{
					//remove call from map
					Call removedCall = callMap.remove(connectionExternal.getCall().getCallId());
					mgwHosts.get(removedCall.getHost()).getCalls().remove(removedCall.getCallId());
					if(logger.isDebugEnabled())
						logger.debug("Call [" +removedCall.getCallId() + "] was removed due to there is no more connections");
				}
				if(endpointInt.getConnections().isEmpty())
				{
					//remove endpoints external and internal
					Endpoint removedEndpointExternal = reverseEndpointMap.remove(endpointInt);
					Endpoint removedEndpointInternal = endpointMap.remove(endpointExt);
					if(logger.isDebugEnabled())
						logger.debug("Endpoints external [" +removedEndpointExternal.getEndpointId() + "] and internal [" + removedEndpointInternal +"] was removed due to there is no more connections");
				}
			}
			else if (dlcx.getCallIdentifier()!=null)
			{
				//remove endpoints
				Endpoint endpointExt = reverseEndpointMap.remove(new Endpoint(endpointIdentifierInternal));
				Endpoint endpointInt = endpointMap.remove(endpointExt);
				if(logger.isDebugEnabled())
				{
					logger.debug("External endpoint was removed due to DLCX from MGW: " + endpointExt.getEndpointId());
					logger.debug("Internal endpoint was removed due to DLCX from MGW: " + endpointInt.getEndpointId());
				}
				//clean connections map
				Connection removedInternalConnection = null;
				for(Connection connection : endpointExt.getConnections().values())
				{
					removedInternalConnection = connectionMap.remove(new Connection(connection.getConnectionId(), lBHost, null));
					if(logger.isDebugEnabled())
						logger.debug("Internal connection was removed due to DLCX from MGW: " + removedInternalConnection);
					Connection removedExternalConnection = reverseConnectionMap.remove(removedInternalConnection);
					if(logger.isDebugEnabled())
						logger.debug("External connection was removed due to DLCX from MGW: " + removedExternalConnection);
					//remove connections from Call
					Connection removedExternalCon = callMap.get(dlcx.getCallIdentifier().toString()).getConnections().remove("ex" + removedExternalConnection.getConnectionId());
					if(logger.isDebugEnabled()&&removedExternalCon!=null)
						logger.debug("external connection : " +removedExternalCon.getConnectionId() +" was removed from call : " + dlcx.getCallIdentifier());
					Connection removedInternalCon = callMap.get(dlcx.getCallIdentifier().toString()).getConnections().remove("in" + removedInternalConnection.getConnectionId());
					if(logger.isDebugEnabled()&&removedInternalCon!=null)
						logger.debug("internal connection : " +removedInternalCon.getConnectionId() +" was removed from call : " + dlcx.getCallIdentifier());
				}
				if(callMap.get(dlcx.getCallIdentifier().toString()).getConnections().isEmpty())
				{
					Call removedCall = callMap.remove(dlcx.getCallIdentifier().toString());
					mgwHosts.get(removedCall.getHost()).getCalls().remove(removedCall.getCallId());
					if(logger.isDebugEnabled())
						logger.debug("Call ["+removedCall.getCallId()+"] was removed from map because it does not have connections");
				}
			}
			else
			{
				//remove endpoints
				Endpoint endpointExt = reverseEndpointMap.remove(new Endpoint(endpointIdentifierInternal));
				Endpoint endpointInt = endpointMap.remove(endpointExt);
				if(logger.isDebugEnabled())
				{
					logger.debug("External endpoint was removed due to DLCX from MGW: " + endpointExt.getEndpointId());
					logger.debug("Internal endpoint was removed due to DLCX from MGW: " + endpointInt.getEndpointId());
				}
				//clean connections map
				Connection removedInternalConnection = null;
				for(Connection connection : endpointExt.getConnections().values())
				{
					removedInternalConnection = connectionMap.remove(new Connection(connection.getConnectionId(), lBHost, null));
					if(logger.isDebugEnabled())
						logger.debug("Internal connection was removed due to DLCX from MGW: " + removedInternalConnection);
					Connection removedExternalConnection = reverseConnectionMap.remove(removedInternalConnection);
					if(logger.isDebugEnabled())
						logger.debug("External connection was removed due to DLCX from MGW: " + removedExternalConnection);
					//remove connections from Call
					
					
					Connection removedExternalCon = callMap.get(removedExternalConnection.getCall().getCallId()).getConnections().remove("ex" + removedExternalConnection.getConnectionId());
					if(logger.isDebugEnabled()&&removedExternalCon!=null)
						logger.debug("external connection : " +removedExternalCon.getConnectionId() +" was removed from call : " + removedExternalConnection.getCall().getCallId());
					Connection removedInternalCon = callMap.get(removedInternalConnection.getCall().getCallId()).getConnections().remove("in" + removedInternalConnection.getConnectionId());
					if(logger.isDebugEnabled()&&removedInternalCon!=null)
						logger.debug("internal connection : " +removedInternalCon.getConnectionId() +" was removed from call : " + removedInternalConnection.getCall().getCallId());
				}
				if(callMap.get(removedInternalConnection.getCall().getCallId()).getConnections().isEmpty())
				{
					Call removedCall = callMap.remove(removedInternalConnection.getCall().getCallId());
					mgwHosts.get(removedCall.getHost()).getCalls().remove(removedCall.getCallId());
					if(logger.isDebugEnabled())
						logger.debug("Call ["+removedCall.getCallId()+"] was removed from map because it does not have connections");
				}
			}
			//modify transaction
			transactionInternal = new Transaction(dlcx.getTransactionHandle());
			transactionExternal = new Transaction(transactionIdCounterExternal.getAndIncrement());
			transactionIdCounterExternal.compareAndSet(Integer.MAX_VALUE, 1);
			transactionMap.putIfAbsent(transactionExternal,transactionInternal);
			dlcx.setTransactionHandle(transactionExternal.getTransactionId());
			externalProvider.sendMgcpEvents(new JainMgcpEvent[] { dlcx }, inetAddress, port);
			break;
		case Constants.CMD_NOTIFY:
			Notify ntfy = (Notify) event;
			if(ntfy.getEndpointIdentifier()!=null)
			{
				EndpointIdentifier endpointIdentifierInt = ntfy.getEndpointIdentifier();
				Endpoint eidExternal = reverseEndpointMap.get(new Endpoint(endpointIdentifierInt, null));
				if(eidExternal!=null)
				{
					ntfy.setEndpointIdentifier(eidExternal.getEndpointId());
					ntfy.setNotifiedEntity(eidExternal.getNotifiedEntity());
				}
				else
				{
					logger.error("NTFY command has endpointId but LB does not have it in reverseEndpointMap");
					break;
				}
			}
			if(ntfy.getRequestIdentifier()!=null)
			{
				Request request = reverseRequestMap.remove(new Request(ntfy.getRequestIdentifier().toString()));
				if(request!=null)
				{
					ntfy.setRequestIdentifier(new RequestIdentifier(request.getRequestId()));
				}
				else
				{
					logger.error("NTFY command has request identifier but LB does not have it in reverseRequestMap");
					break;
				}
			}
			//modify transaction
			transactionInternal = new Transaction(ntfy.getTransactionHandle());
			transactionExternal = new Transaction(transactionIdCounterExternal.getAndIncrement());
			transactionIdCounterExternal.compareAndSet(Integer.MAX_VALUE, 1);
			transactionMap.putIfAbsent(transactionExternal,transactionInternal);
			ntfy.setTransactionHandle(transactionExternal.getTransactionId());
			externalProvider.sendMgcpEvents(new JainMgcpEvent[] { ntfy });
			break;
		 default:
			logger.warn("This COMMAND is unexpected from internal side" + event);
			break;
		}
	}

	public void processInternalMgcpResponseEvent(JainMgcpResponseEvent event) 
	{
		if(logger.isInfoEnabled())
			logger.info("Process internal responce event : " + event);
		Transaction transactionExternal = null;
		
		switch (event.getObjectIdentifier()) 
		{
		case Constants.RESP_CREATE_CONNECTION:
			CreateConnectionResponse crcxResponse = (CreateConnectionResponse)event;
			EndpointIdentifier endpointIdInternal = crcxResponse.getSpecificEndpointIdentifier();
			EndpointIdentifier secondEndpointIdInternal = crcxResponse.getSecondEndpointIdentifier();
			transactionExternal = reverseTransactionMap.remove(new Transaction(crcxResponse.getTransactionHandle()));
			Call call = callMap.get(transactionExternal.getCall().getCallId());
			call.setHost(crcxResponse.getSpecificEndpointIdentifier().getDomainName());
			Endpoint endpointExternal = null;
			Endpoint endpointInternal = null;
			Endpoint secondEndpointExternal = null;
			Endpoint secondEndpointInternal = null;
			Connection connectionExternal = null;
			Connection connectionInternal = null;
			Connection secondConnectionExternal = null;
			Connection secondConnectionInternal = null;
			
			//modify and store endpoints
			if(endpointIdInternal!=null)
			{
				endpointExternal = reverseEndpointMap.get(new Endpoint(endpointIdInternal, null));
				
				if(endpointExternal==null)
				{
					endpointInternal = new Endpoint(endpointIdInternal, null);
					String localEndpointNameExternal = endpointIdInternal.getLocalEndpointName() + endpointIdCounter.getAndIncrement();
					endpointIdCounter.compareAndSet(Integer.MAX_VALUE, 1);
					EndpointIdentifier endpointIdExternal = new EndpointIdentifier(localEndpointNameExternal, lBHost);
					endpointExternal = new Endpoint(endpointIdExternal, transactionExternal.getNotifiedEntity());
					endpointMap.put(endpointExternal, endpointInternal);
					reverseEndpointMap.put(endpointInternal, endpointExternal);
					crcxResponse.setSpecificEndpointIdentifier(new EndpointIdentifier(localEndpointNameExternal, lBHost));
				}
				else
				{
					endpointInternal = endpointMap.get(endpointExternal);
					crcxResponse.setSpecificEndpointIdentifier(endpointExternal.getEndpointId());
				}
			}
			if(secondEndpointIdInternal!=null)
			{
				String secondLocalEndpointName = secondEndpointIdInternal.getLocalEndpointName() + endpointIdCounter.getAndIncrement();
				endpointIdCounter.compareAndSet(Integer.MAX_VALUE, 1);
				EndpointIdentifier secondEndpointIdExternal = new EndpointIdentifier(secondLocalEndpointName, lBHost);
				secondEndpointExternal = new Endpoint(secondEndpointIdExternal, transactionExternal.getNotifiedEntity());
				secondEndpointInternal = new Endpoint(secondEndpointIdInternal, null);
				endpointMap.put(secondEndpointExternal, secondEndpointInternal);
				reverseEndpointMap.put(secondEndpointInternal, secondEndpointExternal);
				crcxResponse.setSecondEndpointIdentifier(new EndpointIdentifier(secondLocalEndpointName, lBHost));
			}
			//modify and store connections
			ConnectionIdentifier connectionIdInternal = crcxResponse.getConnectionIdentifier();
			if(connectionIdInternal!=null)
			{
				connectionInternal = new Connection(connectionIdInternal.toString(), endpointIdInternal.getDomainName(), transactionExternal.getNotifiedEntity());
				ConnectionIdentifier connectionIdExternal = new ConnectionIdentifier("" + connectionIdCounter.getAndIncrement());
				connectionIdCounter.compareAndSet(Integer.MAX_VALUE, 1);
				connectionExternal = new Connection(connectionIdExternal.toString(), lBHost, transactionExternal.getNotifiedEntity());
				connectionMap.put(connectionExternal, connectionInternal);
				reverseConnectionMap.put(connectionInternal, connectionExternal);
				crcxResponse.setConnectionIdentifier(connectionIdExternal);
				
				call.getConnections().put("in"+connectionInternal.getConnectionId(), connectionInternal);
				call.getConnections().put("ex"+connectionExternal.getConnectionId(), connectionExternal);
				
				connectionExternal.setEndpoint(endpointExternal);
				connectionExternal.setCall(call);
				connectionInternal.setEndpoint(endpointInternal);
				connectionInternal.setCall(call);
			}
			ConnectionIdentifier secondConnectionIdInternal = crcxResponse.getSecondConnectionIdentifier();
			if(secondConnectionIdInternal!=null)
			{
				secondConnectionInternal = new Connection(secondConnectionIdInternal.toString(), endpointIdInternal.getDomainName(), transactionExternal.getNotifiedEntity());
				ConnectionIdentifier secondConnectionIdExternal = new ConnectionIdentifier("" + connectionIdCounter.getAndIncrement());
				connectionIdCounter.compareAndSet(Integer.MAX_VALUE, 1);
				secondConnectionExternal = new Connection(secondConnectionIdExternal.toString(),  lBHost, transactionExternal.getNotifiedEntity());
				connectionMap.put(secondConnectionExternal, secondConnectionInternal);
				reverseConnectionMap.put(secondConnectionInternal, secondConnectionExternal);
				crcxResponse.setSecondConnectionIdentifier(secondConnectionIdExternal);
				
				call.getConnections().put("ex"+secondConnectionExternal.getConnectionId(), secondConnectionExternal);
				call.getConnections().put("in"+secondConnectionInternal.getConnectionId(), secondConnectionInternal);
				
				secondConnectionExternal.setEndpoint(secondEndpointExternal);
				secondConnectionExternal.setCall(call);
				secondConnectionInternal.setEndpoint(secondEndpointInternal);
				secondConnectionInternal.setCall(call);
			}
			//save connections to endpoints
			endpointExternal.getConnections().put("ex" + connectionExternal.getConnectionId(), connectionExternal);
			endpointInternal.getConnections().put("in" + connectionInternal.getConnectionId(), connectionInternal);
			if(secondEndpointIdInternal!=null)
			{
				secondEndpointExternal.getConnections().put("ex" + secondConnectionExternal.getConnectionId(), secondConnectionExternal);
				secondEndpointInternal.getConnections().put("in" + secondConnectionInternal.getConnectionId(), secondConnectionInternal);
			}
			
			//modify transactionId
			crcxResponse.setTransactionHandle(transactionExternal.getTransactionId());
			externalProvider.sendMgcpEvents(new JainMgcpEvent[] { crcxResponse });
			break;
		case Constants.RESP_MODIFY_CONNECTION:
			ModifyConnectionResponse mdcxResponse = (ModifyConnectionResponse)event;
			//modify transaction
			transactionExternal = reverseTransactionMap.remove(new Transaction(mdcxResponse.getTransactionHandle()));
			mdcxResponse.setTransactionHandle(transactionExternal.getTransactionId());
			externalProvider.sendMgcpEvents(new JainMgcpEvent[] { mdcxResponse });
			break;
		case Constants.RESP_DELETE_CONNECTION:
			DeleteConnectionResponse dlcxResponse = (DeleteConnectionResponse)event;
			transactionExternal = reverseTransactionMap.remove(new Transaction(dlcxResponse.getTransactionHandle()));
			dlcxResponse.setTransactionHandle(transactionExternal.getTransactionId());
			externalProvider.sendMgcpEvents(new JainMgcpEvent[] { dlcxResponse });
			break;
		case Constants.RESP_AUDIT_CONNECTION:
			AuditConnectionResponse aucxResponse = (AuditConnectionResponse)event;
			transactionExternal = reverseTransactionMap.remove(new Transaction(aucxResponse.getTransactionHandle()));
			aucxResponse.setTransactionHandle(transactionExternal.getTransactionId());
			externalProvider.sendMgcpEvents(new JainMgcpEvent[] { aucxResponse });
			break;
		case Constants.RESP_AUDIT_ENDPOINT:
			AuditEndpointResponse auepResponse = (AuditEndpointResponse)event;
			EndpointIdentifier[] endpointIdentifierListInternal = auepResponse.getEndpointIdentifierList();
			if(endpointIdentifierListInternal!=null&&endpointIdentifierListInternal.length>0)
			{
				EndpointIdentifier[] endpointIdentifierListExternal = new EndpointIdentifier[endpointIdentifierListInternal.length];
				for(int i = 0; i<endpointIdentifierListInternal.length; i++)
				{
					Endpoint eidExternal = endpointMap.get(new Endpoint(endpointIdentifierListInternal[i], null));
					if(eidExternal!=null)
						endpointIdentifierListExternal[i] = eidExternal.getEndpointId();
					else
						logger.error("AUEP response has endpointIds but LB does not have it in map");
					
				}
				auepResponse.setEndpointIdentifierList(endpointIdentifierListExternal);
			}
			
			ConnectionIdentifier[] connectionIdentifiersInternal = auepResponse.getConnectionIdentifiers();
			if(connectionIdentifiersInternal!=null&&connectionIdentifiersInternal.length>0)
			{
				ConnectionIdentifier[] connectionIdentifiersExternal = new ConnectionIdentifier[connectionIdentifiersInternal.length];
				for(int i = 0; i<connectionIdentifiersInternal.length; i++)
				{
					Connection conExternal = connectionMap.get(connectionIdentifiersInternal[i]);
					if(conExternal!=null)
						connectionIdentifiersExternal[i] = new ConnectionIdentifier(conExternal.getConnectionId());
					else
						logger.error("AUEP response has connectionIds but LB does not have it in map");
				}
				auepResponse.setConnectionIdentifiers(connectionIdentifiersExternal);
			}
			transactionExternal = reverseTransactionMap.remove(new Transaction(auepResponse.getTransactionHandle()));
			auepResponse.setTransactionHandle(transactionExternal.getTransactionId());
			externalProvider.sendMgcpEvents(new JainMgcpEvent[] { auepResponse });
			break;
		case Constants.RESP_NOTIFICATION_REQUEST:
			NotificationRequestResponse rqntResponse = (NotificationRequestResponse)event;
			transactionExternal = reverseTransactionMap.remove(new Transaction(rqntResponse.getTransactionHandle()));
			rqntResponse.setTransactionHandle(transactionExternal.getTransactionId());
			externalProvider.sendMgcpEvents(new JainMgcpEvent[] { rqntResponse });
			break;
		case Constants.RESP_ENDPOINT_CONFIGURATION:
			EndpointConfigurationResponse epcfResponse = (EndpointConfigurationResponse)event;
			transactionExternal = reverseTransactionMap.remove(new Transaction(epcfResponse.getTransactionHandle()));
			epcfResponse.setTransactionHandle(transactionExternal.getTransactionId());
			externalProvider.sendMgcpEvents(new JainMgcpEvent[] { epcfResponse });
			break;
		 default:
			logger.warn("This RESPONSE is unexpected from internal side" + event);
			break;
		}
	}

	public ConcurrentHashMap<String, Call> getCallMap() {
		return callMap;
	}

	public ConcurrentHashMap<Connection, Connection> getConnectionMap() {
		return connectionMap;
	}

	public ConcurrentHashMap<Connection, Connection> getReverseConnectionMap() {
		return reverseConnectionMap;
	}

	public ConcurrentHashMap<Endpoint, Endpoint> getEndpointMap() {
		return endpointMap;
	}

	public ConcurrentHashMap<Endpoint, Endpoint> getReverseEndpointMap() {
		return reverseEndpointMap;
	}

	public ConcurrentHashMap<Transaction, Transaction> getTransactionMap() {
		return transactionMap;
	}

	public ConcurrentHashMap<Transaction, Transaction> getReverseTransactionMap() {
		return reverseTransactionMap;
	}

	public ConcurrentHashMap<Request, Request> getReverseRequestMap() {
		return reverseRequestMap;
	}
	
	public ConcurrentHashMap<String, MgwHost> getMgwHosts() {
		return mgwHosts;
	}

	public void mgcpNodeRemoved(Node removedMgcpNode) {
		logger.warn("MGCP LB will remove calls of removed node : " + removedMgcpNode);
		String addressCA = null;
		int portCA = -1;
		InetAddress inetAddressCA = null;
		String address = removedMgcpNode.getIp();
		int port = Integer.parseInt(removedMgcpNode.getProperties().get(Protocol.MGCP_PORT));
		MgwHost removedHost = mgwHosts.get(address+":"+port);
		if(removedHost!=null)
		{
			CopyOnWriteArrayList<String> calls = removedHost.getCalls();
			for(String callId : calls)
			{
				//remove all connections and enpoints of this call
				Call removedCall = callMap.remove(callId);
				ConcurrentHashMap<String, Connection> connections = removedCall.getConnections();
				for(Entry<String,Connection> entry : connections.entrySet())
				{
					if(entry.getKey().startsWith("ex"))
					{
						Connection removedInternalConnection = connectionMap.remove(entry.getValue());
						Endpoint eidExternal = reverseEndpointMap.remove(removedInternalConnection.getEndpoint());
						if(eidExternal!=null)
						{
							//send DLCX to CA
							addressCA = eidExternal.getNotifiedEntity().getDomainName();
							portCA = eidExternal.getNotifiedEntity().getPortNumber();
							if(addressCA==null)
							{
								logger.error("LB can't get address of CA for DLCX command after node removing!!!");
							}
							else
							{
								try {
									inetAddressCA = InetAddress.getByName(addressCA);
								} catch (UnknownHostException e) {
									logger.error("LB can't get address of CA because of error : " + e.getMessage());
									break;
								}
							
								DeleteConnection deleteConnection = new DeleteConnection(this, eidExternal.getEndpointId());
								deleteConnection.setTransactionHandle(externalProvider.getUniqueTransactionHandler());
								externalProvider.sendMgcpEvents(new JainMgcpEvent[] { deleteConnection },inetAddressCA, portCA);
							}
						}
					}
					else
					{
						Connection removedExternalConnection = reverseConnectionMap.remove(entry.getValue());
						endpointMap.remove(removedExternalConnection.getEndpoint());
					}
				}
			}
		}
		else
			logger.error("Node was removed but we did not find it in map mgwHosts!!!");
	}

}

package org.mobicents.tools.smpp.multiplexer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.log4j.Logger;
import org.mobicents.tools.sip.balancer.ActiveStandbyAlgorithm;
import org.mobicents.tools.sip.balancer.BalancerRunner;
import org.mobicents.tools.sip.balancer.SIPNode;
import org.mobicents.tools.smpp.multiplexer.MClientConnectionImpl.ClientState;

import com.cloudhopper.smpp.SmppConstants;
import com.cloudhopper.smpp.pdu.BaseBind;
import com.cloudhopper.smpp.pdu.BaseBindResp;
import com.cloudhopper.smpp.pdu.Pdu;
import com.cloudhopper.smpp.pdu.Unbind;
import com.cloudhopper.smpp.tlv.Tlv;

public class UserSpace {
	private static final Logger logger = Logger.getLogger(UserSpace.class);
	public enum BINDSTATE 
    {    	
    	INITIAL, OPEN, BINDING, BOUND, REBINDING, UNBINDING, CLOSED    	
    }
	
	private String systemId;
	private String password;
	private String systemType;
	private ConcurrentLinkedQueue<MServerConnectionImpl> pendingCustomers;
	private Map<Long, MServerConnectionImpl> customers;
	private Map<Long, MClientConnectionImpl> connectionsToServers;
	private AtomicLong requestToServer = new AtomicLong(0);
	private AtomicLong requestToClient = new AtomicLong(0);
	private boolean isUseRrSendSmppRequestToClient;
	
	private SIPNode [] nodes;
	private Long serverSessionID = new Long(0);
	private ScheduledExecutorService monitorExecutor;
	private long reconnectPeriod;
	private BalancerRunner balancerRunner;
	private MBalancerDispatcher dispatcher;
	private AtomicReference<BINDSTATE> bindState=new AtomicReference<BINDSTATE>(BINDSTATE.INITIAL);
	private AtomicReference<MServerConnectionImpl> bindingCustomer=new AtomicReference<MServerConnectionImpl>();
	
	public UserSpace(String systemId, String password, SIPNode[] nodes, BalancerRunner balancerRunner,ScheduledExecutorService monitorExecutor, MBalancerDispatcher dispatcher)
	{
		this.systemId = systemId;
		this.password = password;
		this.balancerRunner = balancerRunner;
		this.customers = new ConcurrentHashMap<Long, MServerConnectionImpl>();
		this.pendingCustomers = new ConcurrentLinkedQueue<MServerConnectionImpl>();
		this.connectionsToServers = new ConcurrentHashMap<Long, MClientConnectionImpl>();
		this.nodes = nodes;
		this.monitorExecutor = monitorExecutor;
		this.dispatcher = dispatcher;
		this.reconnectPeriod = Long.parseLong(balancerRunner.balancerContext.properties.getProperty("reconnectPeriod", "500"));
		this.isUseRrSendSmppRequestToClient = Boolean.parseBoolean(balancerRunner.balancerContext.properties.getProperty("isUseRrSendSmppRequestToClient","false"));
	}
	
	public synchronized void bind(MServerConnectionImpl customer,Pdu bindPdu) {
		
			switch(bindState.get())
			{
				case INITIAL:
					bindState.set(BINDSTATE.OPEN);
					if(logger.isDebugEnabled())
						logger.debug("Initial bind, bindstate open");
					initBind(customer);
					break;
				case OPEN:
				case BINDING:
					if(logger.isDebugEnabled())
						logger.debug("Put customer with sessionId : "+ customer.getSessionId() +" to pendingCustomers");
					this.pendingCustomers.offer(customer);
					break;
				case BOUND:
				case CLOSED:
				case REBINDING:
				case UNBINDING:
					if(logger.isDebugEnabled())
						logger.debug("Validate customer");
					validateConnection(customer);						
					break;			
			}			
	}

	void bindSuccesfull(SIPNode node)
	{
		
		bindState.set(BINDSTATE.BOUND);
		
		MServerConnectionImpl customer=this.bindingCustomer.get();
		if(logger.isDebugEnabled())
			logger.debug("Init bind successful for customer with session ID : " + customer.getSessionId()	+ " to server " + node.getIp()+":"+node.getProperties().get("smppPort"));
		validateConnection(customer);		
		
		customer=this.pendingCustomers.poll();
		while(customer!=null)
		{
			validateConnection(customer);
			customer=this.pendingCustomers.poll();
		}
	}
	
	void bindFailed(Long serverSessionID, Pdu packet)
	{
		MClientConnectionImpl clientConnection = this.connectionsToServers.get(serverSessionID);
	
		if(logger.isDebugEnabled())
			logger.debug("Bind failed to server with serverSessionId : " + serverSessionID);
		//in case no new customer set state to INITIAL
		if(packet.getCommandStatus()!=SmppConstants.STATUS_INVPASWD)
		{
			//in case its not password try to reinint
				monitorExecutor.execute(new MBinderRunnable(clientConnection, systemId, password, systemType));
		}
		else
		{
			//otherwise remove from list
			this.connectionsToServers.remove(serverSessionID);
			//in case zero left send unbind to curr customer
			if(this.connectionsToServers.isEmpty())
			{
				bindingCustomer.get().sendBindResponse(packet);
				MServerConnectionImpl customer=this.pendingCustomers.poll();
				if(customer!=null)
					initBind(customer);
			}
		}		
	}
	
	private void validateConnection(MServerConnectionImpl customer)
	{
		if(logger.isDebugEnabled())
			logger.debug("Validate connection for customer with sessionId : " + customer.getSessionId());
		BaseBindResp bindResponse = (BaseBindResp)customer.getBindRequest().createResponse();
		bindResponse.setSystemId("loadbalancer");
		if (customer.getConfig().getInterfaceVersion() >= SmppConstants.VERSION_3_4 && ((BaseBind<?>) customer.getBindRequest()).getInterfaceVersion() >= SmppConstants.VERSION_3_4) 
		{
	        Tlv scInterfaceVersion = new Tlv(SmppConstants.TAG_SC_INTERFACE_VERSION, new byte[] { customer.getConfig().getInterfaceVersion() });
			bindResponse.addOptionalParameter(scInterfaceVersion);
		}

		if(!(customer.getConfig().getPassword().equals(this.password)))
		{
			if(logger.isDebugEnabled())
				logger.debug("LB sending fail bind response for customer with sessionId : " + customer.getSessionId());
			//SEND BIND FAILED TO CUSTOMER
	        bindResponse.setCommandStatus(SmppConstants.STATUS_INVPASWD);
	        customer.sendBindResponse(bindResponse);
		}
		else
		{
			if(logger.isDebugEnabled())
				logger.debug("LB sending successful bind response for customer with sessionId : " + customer.getSessionId());
			this.customers.put(customer.getSessionId(), customer);
			//SEND BIND SUCCESFULL TO CUSTOMER
	        bindResponse.setCommandStatus(SmppConstants.STATUS_OK);
	        customer.sendBindResponse(bindResponse);
		}
	}
	
	public void initBind(MServerConnectionImpl customer)
	{
		boolean isSslConnection = customer.getConfig().isUseSsl();
		this.systemType = customer.getConfig().getSystemType();
		if(logger.isDebugEnabled())
			logger.debug("Start initial bind for customer with sessionID : " + customer.getSessionId());
		bindingCustomer.set(customer);
		//INITIAL CONNECTION TO ALL SERVERS
		for(SIPNode node: nodes)
		{
			
	
			if(balancerRunner.balancerContext.terminateTLSTraffic)
				isSslConnection = false;
				
			connectionsToServers.put(serverSessionID, new MClientConnectionImpl(serverSessionID, this, monitorExecutor, balancerRunner, node, isSslConnection));
			monitorExecutor.execute(new MBinderRunnable(connectionsToServers.get(serverSessionID), systemId, password, systemType));
			serverSessionID++;
		}
	}
	
	public void unbind(Long sessionId, Unbind packet)
	{
		if(logger.isDebugEnabled())
			logger.debug("LB sending unbind response for customer with sessionId : " + sessionId + " and remove it. Current size of customers is : " + customers.size());
		
		//statistic
		balancerRunner.balancerContext.smppRequestsToServer.getAndIncrement();
		balancerRunner.incMessages();
		balancerRunner.balancerContext.smppRequestsProcessedById.get(packet.getCommandId()).incrementAndGet();
		
		customers.get(sessionId).sendUnbindResponse(packet.createResponse());
		customers.remove(sessionId);
		if(customers.isEmpty())
		{
			if(logger.isDebugEnabled())
				logger.debug("No more connected customers we send unbind to server with serverSessionID "+serverSessionID);
			
			for(Long serverSessionID :connectionsToServers.keySet())
				connectionsToServers.get(serverSessionID).sendUnbindRequest(packet);

			dispatcher.getUserSpaces().remove(systemId);
		}
	}

	public void sendRequestToServer(Long sessionId, Pdu packet)
	{
		//statistic
		balancerRunner.balancerContext.smppRequestsToServer.getAndIncrement();
		balancerRunner.incMessages();
		balancerRunner.balancerContext.smppRequestsProcessedById.get(packet.getCommandId()).incrementAndGet();
		balancerRunner.balancerContext.smppBytesToServer.addAndGet(packet.getCommandLength());
		
		if(logger.isDebugEnabled())
			logger.debug("LB sending message form customer with sessionId : " + sessionId + " to server ");
		
		if(balancerRunner.algorithClassName.endsWith("ActiveStandbyAlgorithm"))
		{
			if(connectionsToServers.get(0l)!=null&&connectionsToServers.get(0l).getClientState()==ClientState.BOUND)
				connectionsToServers.get(0l).sendSmppRequest(sessionId, packet);
			else
				connectionsToServers.get(1l).sendSmppRequest(sessionId, packet);
		}
		else
		{
		requestToServer.compareAndSet(Long.MAX_VALUE, 0);
		MClientConnectionImpl[] currItems=new MClientConnectionImpl[connectionsToServers.size()];
		currItems=connectionsToServers.values().toArray(currItems);
		while(currItems[(int)(requestToServer.get()%connectionsToServers.size())].getClientState() != ClientState.BOUND)
		{
			requestToServer.getAndIncrement();
		}
			currItems[(int)(requestToServer.getAndIncrement()%connectionsToServers.size())].sendSmppRequest(sessionId, packet);
		}
	}
	
	public void sendRequestToClient(Pdu packet, Long serverSessionId)
	{
		balancerRunner.balancerContext.smppRequestsToClient.getAndIncrement();
		balancerRunner.incMessages();
		balancerRunner.balancerContext.smppRequestsProcessedById.get(packet.getCommandId()).incrementAndGet();
		balancerRunner.balancerContext.smppBytesToClient.addAndGet(packet.getCommandLength());
		
		if(logger.isDebugEnabled())
			logger.debug("LB sending request from server with sessionId : " + serverSessionId + " to client. via RR algorithm? : " + isUseRrSendSmppRequestToClient);
		
		if(isUseRrSendSmppRequestToClient)
		{
			//sends using RR algorithm
			requestToClient.compareAndSet(Long.MAX_VALUE, 0);
			customers.get(requestToClient.getAndIncrement()%customers.size()).sendRequest(serverSessionId,packet);
		}
		else
		{
			//sends to all clients
			for(Long clientSessionID : customers.keySet())
				customers.get(clientSessionID).sendRequest(serverSessionId,packet);
		}
	}
	
	public void sendResponseToServer(Long sessionId, Pdu packet,Long serverSessionId)
	{
		if(logger.isDebugEnabled())
			logger.debug("LB sending response from customer with sessionId : " + sessionId + " to server ");
		
		//statistic
		balancerRunner.balancerContext.smppResponsesProcessedById.get(packet.getCommandId()).incrementAndGet();
		balancerRunner.balancerContext.smppBytesToServer.addAndGet(packet.getCommandLength());
		
		if(serverSessionId!=null)
			connectionsToServers.get(serverSessionId).sendSmppResponse(packet);
		
	}

	public void sendResponseToClient(CustomerPacket customerPacket, Pdu packet) 
	{
		//stistic
		balancerRunner.balancerContext.smppResponsesProcessedById.get(packet.getCommandId()).incrementAndGet();
		balancerRunner.balancerContext.smppBytesToClient.addAndGet(packet.getCommandLength());
		
		packet.setSequenceNumber(customerPacket.getSequence());
		if(logger.isDebugEnabled())
			logger.debug("LB sending response form server to client with sequence : " + packet.getSequenceNumber());
			if(packet.getCommandId()==SmppConstants.CMD_ID_ENQUIRE_LINK_RESP)
				customers.get(customerPacket.getSessionId()).serverSideOk();
			else
				customers.get(customerPacket.getSessionId()).sendResponse(packet);
	}
	
	public void enquireLinkReceivedFromServer()
	{
		for(Long key:customers.keySet())
			customers.get(key).serverSideOk();
	}
	
	public void unbindRequestedFromServer(Unbind packet, Long serverSessioId)
	{
		if(logger.isDebugEnabled())
			logger.debug("LB got unbind request form server with serverSessionId :" + serverSessioId+". LB removed it from list of nodes");
		
		connectionsToServers.remove(serverSessioId);
		
		if(connectionsToServers.isEmpty())
		{
			if(logger.isDebugEnabled())
				logger.debug("LB hasn't had nodes already : " + serverSessioId+". LB sent unbind to all clients and unbind response to server");
			
			for(Long key:customers.keySet())
				customers.get(key).sendUnbindRequest(packet);
		
			connectionsToServers.get(serverSessioId).sendUnbindResponse(packet.createResponse());
		}
	}
	
	public void connectionLost(Long serverSessionID)
	{
		//set all connected customers to rebinding state and trying to reconnect
		if(connectionsToServers.size() == 1)
			for(Long key:customers.keySet())
				customers.get(key).reconnectState(true);
		
		MClientConnectionImpl connection = connectionsToServers.get(serverSessionID);
		connectionsToServers.remove(serverSessionID);
		monitorExecutor.schedule(new MBinderRunnable(connection, systemId, password, systemType), reconnectPeriod, TimeUnit.MILLISECONDS);
	}
	
	public void reconnectSuccesful(Long serverSessionID, MClientConnectionImpl connection)
	{
		for(Long key:customers.keySet())
			customers.get(key).reconnectState(false);
		connectionsToServers.put(serverSessionID,connection);
	}
	
		
	public Map<Long, MServerConnectionImpl> getCustomers() {
		return customers;
	}
	public MBalancerDispatcher getDispatcher() {
		return dispatcher;
	}
}
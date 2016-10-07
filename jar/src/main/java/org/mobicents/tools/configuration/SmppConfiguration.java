package org.mobicents.tools.configuration;

public class SmppConfiguration {
	
	public static final String SMPP_HOST = null;
	public static final Integer SMPP_PORT = null;
	public static final Integer SMPP_SSL_PORT = null;
	public static final String REMOTE_SERVERS = null;
	public static final Integer MAX_CONNECTION_SIZE = 10;
	public static final Boolean NON_BLOCKING_SOCKETS_ENABLED = true;
	public static final Boolean DEFAULT_SESSION_COUNTERS_ENABLED = false;
	public static final Integer TIMEOUT_RESPONSE = 3000;
	public static final Integer TIMEOUT_CONNECTION = 1000;
	public static final Integer TIMEOUT_ENQUIRE = 10000;
	public static final Integer RECONNECT_PERIOD = 500;
	public static final Integer TIMEOUT_CONNECTION_CHECK_CLIENT_SIDE = 3000;
	public static final Integer TIMEOUT_CONNECTION_CHECK_SERVER_SIDE = 3000;
	public static final Boolean IS_USE_RR_SEND_SMPP_REQUEST_TO_CLIENT = false;
	
	private String smppHost;	
	private Integer smppPort;
	private Integer smppSslPort;
	private String remoteServers;
	private Integer maxConnectionSize;
	private Boolean nonBlockingSocketsEnabled;
	private Boolean defaultSessionCountersEnabled;
	private Integer timeoutResponse;
	private Integer timeoutConnection;
	private Integer timeoutEnquire;
	private Integer reconnectPeriod;
	private Integer timeoutConnectionCheckClientSide;
	private Integer timeoutConnectionCheckServerSide;
	private Boolean isUseRrSendSmppRequestToClient;
	
	 public SmppConfiguration() 
	    {
		 	this.smppHost = SMPP_HOST;
	        this.smppPort = SMPP_PORT;
	        this.smppSslPort = SMPP_SSL_PORT;
	        this.remoteServers = REMOTE_SERVERS;
	        this.maxConnectionSize = MAX_CONNECTION_SIZE;
	        this.nonBlockingSocketsEnabled = NON_BLOCKING_SOCKETS_ENABLED;
	        this.defaultSessionCountersEnabled = DEFAULT_SESSION_COUNTERS_ENABLED;
	        this.timeoutResponse = TIMEOUT_RESPONSE;
	        this.timeoutConnection = TIMEOUT_CONNECTION;
	        this.timeoutEnquire = TIMEOUT_ENQUIRE;
	        this.reconnectPeriod = RECONNECT_PERIOD;
	        this.timeoutConnectionCheckClientSide = TIMEOUT_CONNECTION_CHECK_CLIENT_SIDE;
	        this.timeoutConnectionCheckServerSide = TIMEOUT_CONNECTION_CHECK_SERVER_SIDE;
	        this.isUseRrSendSmppRequestToClient = IS_USE_RR_SEND_SMPP_REQUEST_TO_CLIENT;
	    }

	 
	public String getSmppHost() {
		return smppHost;
	}

	public void setSmppHost(String smppHost) {
		this.smppHost = smppHost;
	}

	public Integer getSmppPort() 
	{
		return smppPort;
	}

	public void setSmppPort(Integer smppPort) 
	{
		this.smppPort = smppPort;
	}

	public Integer getSmppSslPort() {
		return smppSslPort;
	}

	public void setSmppSslPort(Integer smppSslPort) 
	{
		this.smppSslPort = smppSslPort;
	}

	public Integer getMaxConnectionSize() 
	{
		return maxConnectionSize;
	}

	public void setMaxConnectionSize(Integer maxConnectionSize) 
	{
		this.maxConnectionSize = maxConnectionSize;
	}

	public Boolean isNonBlockingSocketsEnabled() 
	{
		return nonBlockingSocketsEnabled;
	}

	public void setNonBlockingSocketsEnabled(Boolean nonBlockingSocketsEnabled) 
	{
		this.nonBlockingSocketsEnabled = nonBlockingSocketsEnabled;
	}

	public Boolean isDefaultSessionCountersEnabled() 
	{
		return defaultSessionCountersEnabled;
	}

	public void setDefaultSessionCountersEnabled(Boolean defaultSessionCountersEnabled) 
	{
		this.defaultSessionCountersEnabled = defaultSessionCountersEnabled;
	}

	public Integer getTimeoutResponse() 
	{
		return timeoutResponse;
	}

	public void setTimeoutResponse(Integer timeoutResponse) 
	{
		this.timeoutResponse = timeoutResponse;
	}

	public Integer getTimeoutConnection() 
	{
		return timeoutConnection;
	}

	public void setTimeoutConnection(Integer timeoutConnection) 
	{
		this.timeoutConnection = timeoutConnection;
	}

	public Integer getTimeoutEnquire() 
	{
		return timeoutEnquire;
	}

	public void setTimeoutEnquire(Integer timeoutEnquire) 
	{
		this.timeoutEnquire = timeoutEnquire;
	}

	public Integer getReconnectPeriod() 
	{
		return reconnectPeriod;
	}

	public void setReconnectPeriod(Integer reconnectPeriod) 
	{
		this.reconnectPeriod = reconnectPeriod;
	}

	public Integer getTimeoutConnectionCheckClientSide() 
	{
		return timeoutConnectionCheckClientSide;
	}

	public void setTimeoutConnectionCheckClientSide(Integer timeoutConnectionCheckClientSide) 
	{
		this.timeoutConnectionCheckClientSide = timeoutConnectionCheckClientSide;
	}

	public Integer getTimeoutConnectionCheckServerSide() 
	{
		return timeoutConnectionCheckServerSide;
	}

	public void setTimeoutConnectionCheckServerSide(Integer timeoutConnectionCheckServerSide) 
	{
		this.timeoutConnectionCheckServerSide = timeoutConnectionCheckServerSide;
	}

	public String getRemoteServers() {
		return remoteServers;
	}

	public void setRemoteServers(String remoteServers) {
		this.remoteServers = remoteServers;
	}

	public Boolean getIsUseRrSendSmppRequestToClient() {
		return isUseRrSendSmppRequestToClient;
	}

	public void setIsUseRrSendSmppRequestToClient(Boolean isUseRrSendSmppRequestToClient) {
		this.isUseRrSendSmppRequestToClient = isUseRrSendSmppRequestToClient;
	}
}

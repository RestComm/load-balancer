package org.mobicents.tools.configuration;

import java.util.ArrayList;
import java.util.List;

import org.mobicents.tools.heartbeat.impl.ServerController;

public class CommonConfiguration {
    
    public static final String HOST = "127.0.0.1";
    public static final String IPV6_HOST = "::1";
    public static final Integer NODE_TIMEOUT = 8400;
    public static final Integer HARDBEAT_INTERVAL = 150;
    public static final Integer STATISTIC_PORT = 2006;
    public static final Integer SHUTDOWN_TIMEOUT = 10000;
    public static final String HEARTBEAT_PORT = "2610";
    public static final String CACHE_CONFIGURATION = null;
    public static final Boolean SECURITY_REQUIRED = false;
    public static final String LOGIN = null;
    public static final String PASSWORD = null;
    public static final String NODE_PROTOCOL_CLASS_NAME = ServerController.class.getCanonicalName();
    
    private String host;
    private String ipv6Host;
    private Integer nodeTimeout;
    private Integer heartbeatInterval;
    private Integer statisticPort;
    private Integer shutdownTimeout;
    private List <Integer> heartbeatPorts;
    private String cacheConfiguration;
    private Boolean securityRequired;
    private String login;
    private String password;
    private String nodeCommunicationProtocolClassName;

    public CommonConfiguration() 
    {
        this.host = HOST;
        this.ipv6Host = IPV6_HOST;
        this.nodeTimeout = NODE_TIMEOUT;
        this.heartbeatInterval = HARDBEAT_INTERVAL;
        this.statisticPort = STATISTIC_PORT;
        this.shutdownTimeout = SHUTDOWN_TIMEOUT;
        this.heartbeatPorts = new ArrayList<>();
        this.cacheConfiguration = CACHE_CONFIGURATION;
        this.securityRequired = SECURITY_REQUIRED;
        this.login = LOGIN;
        this.password = PASSWORD;
        this.nodeCommunicationProtocolClassName = NODE_PROTOCOL_CLASS_NAME;
    }

    public String getHost() 
    {
        return host;
    }

    public void setHost(String host) 
    {
        this.host = host;
    }
	public String getIpv6Host() 
	{
		return ipv6Host;
	}

	public void setIpv6Host(String ipv6Host) 
	{
		this.ipv6Host = ipv6Host;
	}

	public Integer getNodeTimeout() 
	{
		return nodeTimeout;
	}

	public void setNodeTimeout(Integer nodeTimeout) 
	{
		this.nodeTimeout = nodeTimeout;
	}

	public Integer getHeartbeatInterval() 
	{
		return heartbeatInterval;
	}

	public void setHeartbeatInterval(Integer heartbeatInterval) 
	{
		this.heartbeatInterval = heartbeatInterval;
	}

	public Integer getStatisticPort() 
	{
		return statisticPort;
	}

	public void setStatisticPort(Integer statisticPort) 
	{
		if (statisticPort < 1 || statisticPort > 65535) 
        {
            throw new IllegalArgumentException("statisticPort is out of range");
        }
		this.statisticPort = statisticPort;
	}

	public Integer getShutdownTimeout() {
		return shutdownTimeout;
	}

	public void setShutdownTimeout(Integer shutdownTimeout) {
		this.shutdownTimeout = shutdownTimeout;
	}

	public List<Integer> getHeartbeatPorts() {
		return heartbeatPorts;
	}

	public void setHeartbeatPorts(List<Integer> heartbeatPorts) {
		this.heartbeatPorts = heartbeatPorts;
	}

	public String getCacheConfiguration() {
		return cacheConfiguration;
	}

	public void setCacheConfiguration(String cacheConfiguration) {
		this.cacheConfiguration = cacheConfiguration;
	}

	public Boolean getSecurityRequired() {
		return securityRequired;
	}

	public void setSecurityRequired(Boolean securityRequired) {
		this.securityRequired = securityRequired;
	}

	public String getLogin() {
		return login;
	}

	public void setLogin(String login) {
		this.login = login;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getNodeCommunicationProtocolClassName() {
		return nodeCommunicationProtocolClassName;
	}

	public void setNodeCommunicationProtocolClassName(String nodeCommunicationProtocolClassName) {
		this.nodeCommunicationProtocolClassName = nodeCommunicationProtocolClassName;
	}
	
}

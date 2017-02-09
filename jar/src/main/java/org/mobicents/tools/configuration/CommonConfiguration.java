package org.mobicents.tools.configuration;

public class CommonConfiguration {
    
    public static final String HOST = "127.0.0.1";
    public static final String IPV6_HOST = "::1";
    public static final Integer RMI_REGISTRY_PORT = 2000;
    public static final Integer RMI_REMOTE_OBJECT_PORT = 2001;
    public static final Integer NODE_TIMEOUT = 8400;
    public static final Integer HARDBEAT_INTERVAL = 150;
    public static final Integer STATISTIC_PORT = 2006;
    public static final Integer JMX_HTML_ADAPTOR_PORT_PORT = 8000;
    public static final Integer SHUTDOWN_TIMEOUT = 10000;
    public static final Boolean SECURITY_REQUIRED = false;
    public static final String LOGIN = null;
    public static final String PASSWORD = null;
    
    private String host;
    private String ipv6Host;
    private Integer rmiRegistryPort;
    private Integer rmiRemoteObjectPort;
    private Integer nodeTimeout;
    private Integer heartbeatInterval;
    private Integer statisticPort;
    private Integer jmxHtmlAdapterPort;
    private Integer shutdownTimeout;
    private Boolean securityRequired;
    private String login;
    private String password;

    public CommonConfiguration() 
    {
        this.host = HOST;
        this.ipv6Host = IPV6_HOST;
        this.rmiRegistryPort = RMI_REGISTRY_PORT;
        this.rmiRemoteObjectPort = RMI_REMOTE_OBJECT_PORT;
        this.nodeTimeout = NODE_TIMEOUT;
        this.heartbeatInterval = HARDBEAT_INTERVAL;
        this.statisticPort = STATISTIC_PORT;
        this.jmxHtmlAdapterPort = JMX_HTML_ADAPTOR_PORT_PORT;
        this.shutdownTimeout = SHUTDOWN_TIMEOUT;
        this.securityRequired = SECURITY_REQUIRED;
        this.login = LOGIN;
        this.password = PASSWORD;
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
	public Integer getRmiRegistryPort() 
	{
		return rmiRegistryPort;
	}

	public void setRmiRegistryPort(Integer rmiRegistryPort) 
	{
		 if (rmiRegistryPort < 1 || rmiRegistryPort > 65535) 
	        {
	            throw new IllegalArgumentException("rmiRegistryPort is out of range");
	        }
		this.rmiRegistryPort = rmiRegistryPort;
	}

	public Integer getRmiRemoteObjectPort() 
	{
		return rmiRemoteObjectPort;
	}

	public void setRmiRemoteObjectPort(Integer rmiRemoteObjectPort) 
	{
		 if (rmiRemoteObjectPort < 1 || rmiRemoteObjectPort > 65535) 
	        {
	            throw new IllegalArgumentException("rmiRemoteObjectPort is out of range");
	        }
		this.rmiRemoteObjectPort = rmiRemoteObjectPort;
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

	public Integer getJmxHtmlAdapterPort() {
		return jmxHtmlAdapterPort;
	}

	public void setJmxHtmlAdapterPort(Integer jmxHtmlAdapterPort) {
		this.jmxHtmlAdapterPort = jmxHtmlAdapterPort;
	}

	public Integer getShutdownTimeout() {
		return shutdownTimeout;
	}

	public void setShutdownTimeout(Integer shutdownTimeout) {
		this.shutdownTimeout = shutdownTimeout;
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
	
}

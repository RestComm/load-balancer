package org.mobicents.tools.configuration;

public class CommonConfiguration {
    
    public static final String HOST = "127.0.0.1";
    public static final Integer RMI_REGISTRY_PORT = 2000;
    public static final Integer RMI_REMOTE_OBJECT_PORT = 2001;
    public static final Integer NODE_TIMEOUT = 8400;
    public static final Integer HARDBEAT_INTERVAL = 150;
    public static final Integer STATISTIC_PORT = 2006;
    public static final Integer JMX_HTML_ADAPTOR_PORT_PORT = 8000;
    
    private String host;
    private Integer rmiRegistryPort;
    private Integer rmiRemoteObjectPort;
    private Integer nodeTimeout;
    private Integer heartbeatInterval;
    private Integer statisticPort;
    private Integer jmxHtmlAdapterPort;

    public CommonConfiguration() 
    {
        this.host = HOST;
        this.rmiRegistryPort = RMI_REGISTRY_PORT;
        this.rmiRemoteObjectPort = RMI_REMOTE_OBJECT_PORT;
        this.nodeTimeout = NODE_TIMEOUT;
        this.heartbeatInterval = HARDBEAT_INTERVAL;
        this.statisticPort = STATISTIC_PORT;
        this.jmxHtmlAdapterPort = JMX_HTML_ADAPTOR_PORT_PORT;
    }

    public String getHost() 
    {
        return host;
    }

    public void setHost(String host) 
    {
        this.host = host;
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
	
}

package org.mobicents.tools.configuration;

public class HttpConfiguration 
{
	public static final Integer HTTP_PORT = 2080;
	public static final Integer HTTPS_PORT = null;
	public static final Integer MAX_CONTENT_LENT = 1048576;
	public static final String UNAVAILABLE_HOST = null;
	
	private Integer httpPort;
	private Integer httpsPort;
	private Integer maxContentLength;
	private String unavailableHost;
	
	 public HttpConfiguration() 
	    {
	        this.httpPort = HTTP_PORT;
	        this.httpsPort = HTTPS_PORT;
	        this.unavailableHost = UNAVAILABLE_HOST;
	        this.maxContentLength = MAX_CONTENT_LENT;
	    }

	public Integer getHttpPort() 
	{
		return httpPort;
	}

	public void setHttpPort(Integer httpPort) 
	{
		this.httpPort = httpPort;
	}

	public Integer getHttpsPort() 
	{
		return httpsPort;
	}

	public void setHttpsPort(Integer httpsPort) 
	{
 		this.httpsPort = httpsPort;
	}

	public String getUnavailableHost() 
	{
		return unavailableHost;
	}

	public void setUnavailableHost(String unavailableHost) 
	{
		this.unavailableHost = unavailableHost;
	}

	public Integer getMaxContentLength() 
	{
		return maxContentLength;
	}

	public void setMaxContentLength(Integer maxContentLength) 
	{
		this.maxContentLength = maxContentLength;
	}
	
}

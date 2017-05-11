package org.mobicents.tools.configuration;

import org.w3c.dom.Document;

public class HttpConfiguration 
{
	public static final Integer HTTP_PORT = 2080;
	public static final Integer HTTPS_PORT = null;
	public static final String UNAVAILABLE_HOST = null;
	public  static final String REQUEST_CHECK_PATTERN = null;
	
	private Integer httpPort;
	private Integer httpsPort;
	private String unavailableHost;
	private String requestCheckPattern;
	private Document urlrewriteRule;
	
	 public HttpConfiguration() 
	 {
		 this.httpPort = HTTP_PORT;
		 this.httpsPort = HTTPS_PORT;
	     this.unavailableHost = UNAVAILABLE_HOST;
	     this.requestCheckPattern = REQUEST_CHECK_PATTERN;   
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

	public Document getUrlrewriteRule() 
	{
		return urlrewriteRule;
	}

	public void setUrlrewriteRule(Document urlrewriteRule) 
	{
		this.urlrewriteRule = urlrewriteRule;
	}

	public String getRequestCheckPattern() 
	{
		return requestCheckPattern;
	}

	public void setRequestCheckPattern(String requestCheckPattern) 
	{
		this.requestCheckPattern = requestCheckPattern;
	}
	
}

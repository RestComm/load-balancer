package org.mobicents.tools.configuration;

public class MgcpConfiguration {
	
	public static final String MGCP_HOST = null;
	public static final String MGCP_INTERNAL_HOST = null;
	public static final String MGCP_EXTERNAL_HOST = null;
	public static final Integer MGCP_EXTERNAL_PORT = null;
	public static final Integer MGCP_INTERNAL_PORT = null;
	
	private String mgcpHost;	
	private String mgcpInternalHost;	
	private String mgcpExternalHost;	
	private Integer mgcpExternalPort;
	private Integer mgcpInternalPort;
	
	public MgcpConfiguration() 
	{
		this.mgcpHost = MGCP_HOST;
		this.mgcpInternalHost = MGCP_INTERNAL_HOST;
		this.mgcpExternalHost = MGCP_EXTERNAL_HOST;
	    this.mgcpExternalPort = MGCP_EXTERNAL_PORT;
	    this.mgcpInternalPort = MGCP_INTERNAL_PORT;
	}

	public String getMgcpHost() {
		return mgcpHost;
	}

	public void setMgcpHost(String mgcpHost) {
		this.mgcpHost = mgcpHost;
	}

	public String getMgcpInternalHost() {
		return mgcpInternalHost;
	}

	public void setMgcpInternalHost(String mgcpInternalHost) {
		this.mgcpInternalHost = mgcpInternalHost;
	}

	public String getMgcpExternalHost() {
		return mgcpExternalHost;
	}

	public void setMgcpExternalHost(String mgcpExternalHost) {
		this.mgcpExternalHost = mgcpExternalHost;
	}

	public Integer getMgcpExternalPort() {
		return mgcpExternalPort;
	}

	public void setMgcpExternalPort(Integer mgcpExternalPort) {
		this.mgcpExternalPort = mgcpExternalPort;
	}

	public Integer getMgcpInternalPort() {
		return mgcpInternalPort;
	}

	public void setMgcpInternalPort(Integer mgcpInternalPort) {
		this.mgcpInternalPort = mgcpInternalPort;
	}
}


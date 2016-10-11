package org.mobicents.tools.configuration;

public class ExternalLegConfiguration {

	public static final String HOST = null;
	public static final String IP_LOAD_BALANCER_ADRESS = null;
	
	public static final Integer UDP_PORT = 5060;
	public static final Integer TCP_PORT = 5060;
	public static final Integer TLS_PORT = null;
	public static final Integer WS_PORT = null;
	public static final Integer WSS_PORT = null;
	
	public static final Integer IP_LOAD_BALANCER_UDP_PORT = null;
	public static final Integer IP_LOAD_BALANCER_TCP_PORT = null;
	public static final Integer IP_LOAD_BALANCER_TLS_PORT = null;
	public static final Integer IP_LOAD_BALANCER_WS_PORT = null;
	public static final Integer IP_LOAD_BALANCER_WSS_PORT = null;
	
	private String host;
	private String ipLoadBalancerAddress;
	
	private Integer udpPort;
	private Integer tcpPort;
	private Integer tlsPort;
	private Integer wsPort;
	private Integer wssPort;
	
	private Integer ipLoadBalancerUdpPort;
	private Integer ipLoadBalancerTcpPort;
	private Integer ipLoadBalancerTlsPort;
	private Integer ipLoadBalancerWsPort;
	private Integer ipLoadBalancerWssPort;
		
	public ExternalLegConfiguration() 
	{
		this.host = HOST;
		this.ipLoadBalancerAddress = IP_LOAD_BALANCER_ADRESS;
		this.udpPort = UDP_PORT;
		this.tcpPort = TCP_PORT;
		this.tlsPort = TLS_PORT;
		this.wsPort = WS_PORT;
		this.wssPort = WSS_PORT;
		this.ipLoadBalancerUdpPort = IP_LOAD_BALANCER_UDP_PORT;
		this.ipLoadBalancerTcpPort = IP_LOAD_BALANCER_TCP_PORT;
		this.ipLoadBalancerTlsPort = IP_LOAD_BALANCER_TLS_PORT;
		this.ipLoadBalancerWsPort = IP_LOAD_BALANCER_WS_PORT;
		this.ipLoadBalancerWssPort = IP_LOAD_BALANCER_WSS_PORT;
	}

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public String getIpLoadBalancerAddress() {
		return ipLoadBalancerAddress;
	}

	public void setIpLoadBalancerAddress(String ipLoadBalancerAddress) {
		this.ipLoadBalancerAddress = ipLoadBalancerAddress;
	}

	public Integer getUdpPort() {
		return udpPort;
	}

	public void setUdpPort(Integer udpPort) {
		this.udpPort = udpPort;
	}

	public Integer getTcpPort() {
		return tcpPort;
	}

	public void setTcpPort(Integer tcpPort) {
		this.tcpPort = tcpPort;
	}

	public Integer getTlsPort() {
		return tlsPort;
	}

	public void setTlsPort(Integer tlsPort) {
		this.tlsPort = tlsPort;
	}

	public Integer getWsPort() {
		return wsPort;
	}

	public void setWsPort(Integer wsPort) {
		this.wsPort = wsPort;
	}

	public Integer getWssPort() {
		return wssPort;
	}

	public void setWssPort(Integer wssPort) {
		this.wssPort = wssPort;
	}

	public Integer getIpLoadBalancerUdpPort() {
		return ipLoadBalancerUdpPort;
	}

	public void setIpLoadBalancerUdpPort(Integer ipLoadBalancerUdpPort) {
		this.ipLoadBalancerUdpPort = ipLoadBalancerUdpPort;
	}

	public Integer getIpLoadBalancerTcpPort() {
		return ipLoadBalancerTcpPort;
	}

	public void setIpLoadBalancerTcpPort(Integer ipLoadBalancerTcpPort) {
		this.ipLoadBalancerTcpPort = ipLoadBalancerTcpPort;
	}

	public Integer getIpLoadBalancerTlsPort() {
		return ipLoadBalancerTlsPort;
	}

	public void setIpLoadBalancerTlsPort(Integer ipLoadBalancerTlsPort) {
		this.ipLoadBalancerTlsPort = ipLoadBalancerTlsPort;
	}

	public Integer getIpLoadBalancerWsPort() {
		return ipLoadBalancerWsPort;
	}

	public void setIpLoadBalancerWsPort(Integer ipLoadBalancerWsPort) {
		this.ipLoadBalancerWsPort = ipLoadBalancerWsPort;
	}

	public Integer getIpLoadBalancerWssPort() {
		return ipLoadBalancerWssPort;
	}

	public void setIpLoadBalancerWssPort(Integer ipLoadBalancerWssPort) {
		this.ipLoadBalancerWssPort = ipLoadBalancerWssPort;
	} 
    
}

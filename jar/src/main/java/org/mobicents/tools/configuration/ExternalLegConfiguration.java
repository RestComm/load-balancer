package org.mobicents.tools.configuration;

import java.util.ArrayList;

public class ExternalLegConfiguration {

	public static final String HOST = null;
	public static final String IPV6_HOST = null;
	public static final String IP_LOAD_BALANCER_ADRESS = null;
	public static final String IPV6_LOAD_BALANCER_ADRESS = null;
	
	public static final Integer UDP_PORT = 5060;
	public static final Integer TCP_PORT = 5060;
	public static final Integer TLS_PORT = null;
	public static final Integer WS_PORT = null;
	public static final Integer WSS_PORT = null;
	
	public static final Integer IPV6_UDP_PORT = null;
	public static final Integer IPV6_TCP_PORT = null;
	public static final Integer IPV6_TLS_PORT = null;
	public static final Integer IPV6_WS_PORT = null;
	public static final Integer IPV6_WSS_PORT = null;
	
	public static final Integer IP_LOAD_BALANCER_UDP_PORT = null;
	public static final Integer IP_LOAD_BALANCER_TCP_PORT = null;
	public static final Integer IP_LOAD_BALANCER_TLS_PORT = null;
	public static final Integer IP_LOAD_BALANCER_WS_PORT = null;
	public static final Integer IP_LOAD_BALANCER_WSS_PORT = null;
	
	public static final Integer IPV6_LOAD_BALANCER_UDP_PORT = null;
	public static final Integer IPV6_LOAD_BALANCER_TCP_PORT = null;
	public static final Integer IPV6_LOAD_BALANCER_TLS_PORT = null;
	public static final Integer IPV6_LOAD_BALANCER_WS_PORT = null;
	public static final Integer IPV6_LOAD_BALANCER_WSS_PORT = null;
	
	private String host;
	private String ipv6Host;
	private ArrayList<String> ipLoadBalancerAddress ;
	private ArrayList<String> ipv6LoadBalancerAddress;
	
	private Integer udpPort;
	private Integer tcpPort;
	private Integer tlsPort;
	private Integer wsPort;
	private Integer wssPort;
	
	private Integer ipv6UdpPort;
	private Integer ipv6TcpPort;
	private Integer ipv6TlsPort;
	private Integer ipv6WsPort;
	private Integer ipv6WssPort;
	
	private Integer ipLoadBalancerUdpPort;
	private Integer ipLoadBalancerTcpPort;
	private Integer ipLoadBalancerTlsPort;
	private Integer ipLoadBalancerWsPort;
	private Integer ipLoadBalancerWssPort;
	
	private Integer ipv6LoadBalancerUdpPort;
	private Integer ipv6LoadBalancerTcpPort;
	private Integer ipv6LoadBalancerTlsPort;
	private Integer ipv6LoadBalancerWsPort;
	private Integer ipv6LoadBalancerWssPort;
		
	public ExternalLegConfiguration() 
	{
		this.host = HOST;
		this.ipv6Host = IPV6_HOST;
//		this.ipLoadBalancerAddress = new ArrayList<String>();
//		this.ipv6LoadBalancerAddress = new ArrayList<String>();
		
		this.udpPort = UDP_PORT;
		this.tcpPort = TCP_PORT;
		this.tlsPort = TLS_PORT;
		this.wsPort = WS_PORT;
		this.wssPort = WSS_PORT;
		
		this.ipv6UdpPort = IPV6_UDP_PORT;
		this.ipv6TcpPort = IPV6_TCP_PORT;
		this.ipv6TlsPort = IPV6_TLS_PORT;
		this.ipv6WsPort = IPV6_WS_PORT;
		this.ipv6WssPort = IPV6_WSS_PORT;
		
		this.ipLoadBalancerUdpPort = IP_LOAD_BALANCER_UDP_PORT;
		this.ipLoadBalancerTcpPort = IP_LOAD_BALANCER_TCP_PORT;
		this.ipLoadBalancerTlsPort = IP_LOAD_BALANCER_TLS_PORT;
		this.ipLoadBalancerWsPort = IP_LOAD_BALANCER_WS_PORT;
		this.ipLoadBalancerWssPort = IP_LOAD_BALANCER_WSS_PORT;
		
		this.ipv6LoadBalancerUdpPort = IPV6_LOAD_BALANCER_UDP_PORT;
		this.ipv6LoadBalancerTcpPort = IPV6_LOAD_BALANCER_TCP_PORT;
		this.ipv6LoadBalancerTlsPort = IPV6_LOAD_BALANCER_TLS_PORT;
		this.ipv6LoadBalancerWsPort = IPV6_LOAD_BALANCER_WS_PORT;
		this.ipv6LoadBalancerWssPort = IPV6_LOAD_BALANCER_WSS_PORT;
	}

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public String getIpv6Host() {
		return ipv6Host;
	}

	public void setIpv6Host(String ipv6Host) {
		this.ipv6Host = ipv6Host;
	}

	public ArrayList<String> getIpLoadBalancerAddress() {
		return ipLoadBalancerAddress;
	}

	public void setIpLoadBalancerAddress(ArrayList<String> ipLoadBalancerAddress) {
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

	public ArrayList<String> getIpv6LoadBalancerAddress() {
		return ipv6LoadBalancerAddress;
	}

	public void setIpv6LoadBalancerAddress(ArrayList<String> ipv6LoadBalancerAddress) {
		this.ipv6LoadBalancerAddress = ipv6LoadBalancerAddress;
	}

	public Integer getIpv6UdpPort() {
		return ipv6UdpPort;
	}

	public void setIpv6UdpPort(Integer ipv6UdpPort) {
		this.ipv6UdpPort = ipv6UdpPort;
	}

	public Integer getIpv6TcpPort() {
		return ipv6TcpPort;
	}

	public void setIpv6TcpPort(Integer ipv6TcpPort) {
		this.ipv6TcpPort = ipv6TcpPort;
	}

	public Integer getIpv6TlsPort() {
		return ipv6TlsPort;
	}

	public void setIpv6TlsPort(Integer ipv6TlsPort) {
		this.ipv6TlsPort = ipv6TlsPort;
	}

	public Integer getIpv6WsPort() {
		return ipv6WsPort;
	}

	public void setIpv6WsPort(Integer ipv6WsPort) {
		this.ipv6WsPort = ipv6WsPort;
	}

	public Integer getIpv6WssPort() {
		return ipv6WssPort;
	}

	public void setIpv6WssPort(Integer ipv6WssPort) {
		this.ipv6WssPort = ipv6WssPort;
	}

	public Integer getIpv6LoadBalancerUdpPort() {
		return ipv6LoadBalancerUdpPort;
	}

	public void setIpv6LoadBalancerUdpPort(Integer ipv6LoadBalancerUdpPort) {
		this.ipv6LoadBalancerUdpPort = ipv6LoadBalancerUdpPort;
	}

	public Integer getIpv6LoadBalancerTcpPort() {
		return ipv6LoadBalancerTcpPort;
	}

	public void setIpv6LoadBalancerTcpPort(Integer ipv6LoadBalancerTcpPort) {
		this.ipv6LoadBalancerTcpPort = ipv6LoadBalancerTcpPort;
	}

	public Integer getIpv6LoadBalancerTlsPort() {
		return ipv6LoadBalancerTlsPort;
	}

	public void setIpv6LoadBalancerTlsPort(Integer ipv6LoadBalancerTlsPort) {
		this.ipv6LoadBalancerTlsPort = ipv6LoadBalancerTlsPort;
	}

	public Integer getIpv6LoadBalancerWsPort() {
		return ipv6LoadBalancerWsPort;
	}

	public void setIpv6LoadBalancerWsPort(Integer ipv6LoadBalancerWsPort) {
		this.ipv6LoadBalancerWsPort = ipv6LoadBalancerWsPort;
	}

	public Integer getIpv6LoadBalancerWssPort() {
		return ipv6LoadBalancerWssPort;
	}

	public void setIpv6LoadBalancerWssPort(Integer ipv6LoadBalancerWssPort) {
		this.ipv6LoadBalancerWssPort = ipv6LoadBalancerWssPort;
	}
	
	public int [] getPorts()
	{
		int [] externalPorts = new int[5];
		Integer [] currPorts = {udpPort, tcpPort, tlsPort, wsPort, wssPort};
		for(int i = 0; i < 5 ; i++)
			if(currPorts[i]!=null)
				externalPorts[i] = currPorts[i]; 
		
		return externalPorts;
	}
	public int [] getIpv6Ports()
	{
		int [] externalPorts = new int[5];
		Integer [] currPorts = {ipv6UdpPort, ipv6TcpPort, ipv6TlsPort, ipv6WsPort, ipv6WssPort};
		for(int i = 0; i < 5 ; i++)
			if(currPorts[i]!=null)
				externalPorts[i] = currPorts[i]; 
		
		return externalPorts;
	}
	public int [] getIPLoadBalancerPorts()
	{
		int [] externalPorts = new int[5];
		Integer [] currPorts = {ipLoadBalancerUdpPort, ipLoadBalancerTcpPort, ipLoadBalancerTlsPort, ipLoadBalancerWsPort, ipLoadBalancerWssPort};
		for(int i = 0; i < 5 ; i++)
			if(currPorts[i]!=null)
				externalPorts[i] = currPorts[i]; 
		
		return externalPorts;
	}
	public int [] getIpv6LoadBalancerPorts()
	{
		int [] externalPorts = new int[5];
		Integer [] currPorts = {ipv6LoadBalancerUdpPort, ipv6LoadBalancerTcpPort, ipv6LoadBalancerTlsPort, ipv6LoadBalancerWsPort, ipv6LoadBalancerWssPort};
		for(int i = 0; i < 5 ; i++)
			if(currPorts[i]!=null)
				externalPorts[i] = currPorts[i]; 
		
		return externalPorts;
	}
    
}

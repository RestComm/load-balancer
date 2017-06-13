package org.mobicents.tools.configuration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.log4j.Logger;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.mobicents.tools.heartbeat.api.HeartbeatConfig;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class XmlConfigurationLoader{
    
    private static final Logger log = Logger.getLogger(XmlConfigurationLoader.class);

    private  final Configurations configurations;


    public XmlConfigurationLoader() {
        this.configurations = new Configurations();
    }

    public  LoadBalancerConfiguration load(File filepath) {
        // Default configuration
    	LoadBalancerConfiguration configuration = new LoadBalancerConfiguration();

        // Read configuration from file
        XMLConfiguration xml;
        
        try 
        {
            xml = this.configurations.xml(filepath);
            
            setHeartbeatConfig(configuration, xml);
            // Overwrite default configurations
            configureCommon(xml.configurationAt("common"), configuration.getCommonConfiguration());
            configureHttp(xml, configuration.getHttpConfiguration());
            configureSmpp(xml.configurationAt("smpp"), configuration.getSmppConfiguration());
            configureSip(xml.configurationAt("sip"), configuration.getSipConfiguration());
            configureSsl(xml.configurationAt("ssl"), configuration.getSslConfiguration());
            configureSipStack(xml.configurationsAt("sipStack.property"), configuration.getSipStackConfiguration(), xml.configurationAt("ssl"));
        } catch (ConfigurationException | IllegalArgumentException e) 
        {
            log.error("Could not load configuration from " + filepath + ". Using default values. Message : " + e.getMessage());
        }
        return configuration;
    }
    
    private static void configureSipStack (List<HierarchicalConfiguration<ImmutableNode>> src, SipStackConfiguration dst, HierarchicalConfiguration<ImmutableNode> ssl)
    {
     	for(HierarchicalConfiguration<ImmutableNode> property : src) 
    	{
    		dst.getSipStackProperies().setProperty(property.getString("key"), property.getString("value")) ;
    	}
     	if(!ssl.getString("keyStore").equals(""))
     	{
     		dst.getSipStackProperies().setProperty("javax.net.ssl.keyStore",ssl.getString("keyStore"));
     		dst.getSipStackProperies().setProperty("javax.net.ssl.keyStorePassword",ssl.getString("keyStorePassword"));
     		dst.getSipStackProperies().setProperty("javax.net.ssl.trustStore", ssl.getString("trustStore"));
     		dst.getSipStackProperies().setProperty("javax.net.ssl.trustStorePassword", ssl.getString("trustStorePassword"));
         	if(ssl.getString("tlsClientProtocols").equals(""))
         		dst.getSipStackProperies().setProperty("gov.nist.javax.sip.TLS_CLIENT_PROTOCOLS",ssl.getString("tlsClientProtocols"));
         	if(ssl.getString("enabledCipherSuites").equals(""))
         		dst.getSipStackProperies().setProperty("gov.nist.javax.sip.ENABLED_CIPHER_SUITES",ssl.getString("enabledCipherSuites"));
     	}
    }

    private static void configureCommon(HierarchicalConfiguration<ImmutableNode> src, CommonConfiguration dst) 
    {
        dst.setHost(src.getString("host", CommonConfiguration.HOST));
        dst.setIpv6Host(src.getString("ipv6Host", CommonConfiguration.IPV6_HOST));
        dst.setNodeTimeout(src.getInteger("nodeTimeout", CommonConfiguration.NODE_TIMEOUT));
        dst.setHeartbeatInterval(src.getInteger("heartbeatInterval", CommonConfiguration.HARDBEAT_INTERVAL));
        dst.setStatisticPort(src.getInteger("apiPort", CommonConfiguration.STATISTIC_PORT));
        if(src.getString("shutdownTimeout") != null && !src.getString("shutdownTimeout").equals(""))
        	dst.setShutdownTimeout(src.getInteger("shutdownTimeout", CommonConfiguration.SHUTDOWN_TIMEOUT));
        dst.setCacheConfiguration(src.getString("cacheConfiguration",CommonConfiguration.CACHE_CONFIGURATION));
        dst.setSecurityRequired(src.getBoolean("securityRequired", CommonConfiguration.SECURITY_REQUIRED));
        dst.setLogin(src.getString("login", CommonConfiguration.LOGIN));
        dst.setPassword(src.getString("password", CommonConfiguration.PASSWORD));
        dst.setCacheConfigFile(src.getString("cacheConfigFile",CommonConfiguration.CACHE_CONFIG_FILE));
    }

    private static void configureSip(HierarchicalConfiguration<ImmutableNode> src, SipConfiguration dst) {
    	ExternalLegConfiguration ex = dst.getExternalLegConfiguration();
    	InternalLegConfiguration in = dst.getInternalLegConfiguration();
    	AlgorithmConfiguration alg = dst.getAlgorithmConfiguration();
    	// Basic SIP configuration
        dst.setPublicIp(src.getString("publicIp", SipConfiguration.PUBLIC_IP));
        dst.setPublicIpv6(src.getString("publicIpv6", SipConfiguration.PUBLIC_IPV6));
        dst.setExtraServerNodes(src.getString("extraServerNodes",SipConfiguration.EXTRA_SERVER_NODES));
        dst.setPerformanceTestingMode(src.getBoolean("performanceTestingMode",SipConfiguration.PERFORMANCE_TESTING_MODE));
        dst.setUseIpLoadBalancerAddressInViaHeaders(src.getBoolean("useIpLoadBalancerAddressInViaHeaders",SipConfiguration.USE_IP_LOAD_BALANCER_ADRESS_IN_VIA_HEADERS));
        dst.setSendTrying(src.getBoolean("isSendTrying",SipConfiguration.IS_SEND_TRYING));
        dst.setBlockedValues(src.getString("blockedValues",SipConfiguration.BLOCKED_VALUES));
        dst.setUsePrettyEncoding(src.getBoolean("usePrettyEncoding", SipConfiguration.USE_PRETTY_ENCODING));
        dst.setIsSend5xxResponse(src.getBoolean("isSend5xxResponse", SipConfiguration.IS_SEND_5XX_RESPONSE));
        dst.setIsSend5xxResponseSatusCode(src.getInteger("isSend5xxResponseSatusCode", SipConfiguration.IS_SEND_5XX_RESPONSE_STATUS_CODE));
        dst.setIsSend5xxResponseReasonHeader(src.getString("isSend5xxResponseReasonHeader", SipConfiguration.IS_SEND_5XX_RESPONSE_REASON_HEADER));
        String responsesStatusCodeNodeRemoval = src.getString("responsesStatusCodeNodeRemoval", SipConfiguration.RESPONSES_STATUS_CODE_NODE_REMOVAL);
        if(responsesStatusCodeNodeRemoval != null) {
        	List<Integer> responsesStatusCodeNodeRemovalList = new ArrayList<Integer>();
        	StringTokenizer tokens = new StringTokenizer(responsesStatusCodeNodeRemoval, ",");
        	while (tokens.hasMoreTokens()) {
				String token = tokens.nextToken();
				responsesStatusCodeNodeRemovalList.add(Integer.parseInt(token));
			}
        	dst.setResponseStatusCodeNodeRemoval(responsesStatusCodeNodeRemovalList);
        }
        dst.setIsUseWithNexmo(src.getBoolean("isUseWithNexmo",SipConfiguration.IS_USE_WITH_NEXMO));
        dst.setMatchingHostnameForRoute(src.getString("matchingHostnameForRoute", SipConfiguration.MATCHING_HOSTNAME_FOR_ROUTE));
        dst.setIsFilterSubdomain(src.getBoolean("isFilterSubdomain", SipConfiguration.IS_FILTER_SUBDOMAIN));
        if(src.getString("internalTransport")!=null&&!src.getString("internalTransport").equals(""))
        	dst.setInternalTransport(src.getString("internalTransport", SipConfiguration.INTERNAL_TRANSPORT));
        if(src.getString("trafficRampupCyclePeriod")!=null&&!src.getString("trafficRampupCyclePeriod").equals(""))
        	dst.setTrafficRampupCyclePeriod(src.getInteger("trafficRampupCyclePeriod", SipConfiguration.TRAFFIC_RAMPUP_CYCLE_PERIOD));
        if(src.getString("maxWeightIndex")!=null&&!src.getString("maxWeightIndex").equals(""))
        	dst.setMaxWeightIndex(src.getInteger("maxWeightIndex", SipConfiguration.MAX_WEIGHT_INDEX));
        if(src.getString("maxRequestNumberWithoutResponse") != null && !src.getString("maxRequestNumberWithoutResponse").equals(""))
        	dst.setMaxRequestNumberWithoutResponse(src.getInteger("maxRequestNumberWithoutResponse", SipConfiguration.MAX_REQUEST_NUMBER_WITHOUT_RESPONSE));
        if(src.getString("maxResponseTime") != null && !src.getString("maxResponseTime").equals(""))
        	dst.setMaxResponseTime(src.getLong("maxResponseTime", SipConfiguration.MAX_RESPONSE_TIME));

        //Algorithm configuration
        if(src.getString("algorithm.algorithmClass") != null && !src.getString("algorithm.algorithmClass").equals(""))
        	alg.setAlgorithmClass(src.getString("algorithm.algorithmClass",AlgorithmConfiguration.ALGORITHM_CLASS));
        if(src.getString("algorithm.sipHeaderAffinityKey") != null && !src.getString("algorithm.sipHeaderAffinityKey").equals(""))
        	alg.setSipHeaderAffinityKey(src.getString("algorithm.sipHeaderAffinityKey",AlgorithmConfiguration.SIP_HEADER_AFFINITY_KEY));
        if(src.getString("algorithm.sipHeaderAffinityExclusionPattern") != null && !src.getString("algorithm.sipHeaderAffinityExclusionPattern").equals(""))
        	alg.setSipHeaderAffinityKeyExclusionPattern(src.getString("algorithm.sipHeaderAffinityExclusionPattern"));
        if(src.getString("algorithm.sipHeaderAffinityFallbackKey") != null && !src.getString("algorithm.sipHeaderAffinityFallbackKey").equals(""))
        	alg.setSipHeaderAffinityFallbackKey(src.getString("algorithm.sipHeaderAffinityFallbackKey"));
        alg.setCallIdAffinityGroupFailover(src.getBoolean("algorithm.callIdAffinityGroupFailover",AlgorithmConfiguration.CALL_ID_AFFINITY_GROUP_FAILOVER));
        alg.setCallIdAffinityMaxTimeInCache(src.getInteger("algorithm.callIdAffinityMaxTimeInCache",AlgorithmConfiguration.CALL_ID_AFFINITY_MAX_TIME_IN_CACHE));
        alg.setHttpAffinityKey(src.getString("algorithm.httpAffinityKey",AlgorithmConfiguration.HTTP_AFFINITY_KEY));
        alg.setPersistentConsistentHashCacheConfiguration(src.getString("algorithm.persistentConsistentHashCacheConfiguration",AlgorithmConfiguration.PERSISTENT_CONSISTENT_HASH_CACHE_CONFIG));
        alg.setSubclusterMap(src.getString("subclusterMap",AlgorithmConfiguration.SUBCLUSTER_MAP));
        alg.setEarlyDialogWorstCase(src.getBoolean("earlyDialogWorstCase",AlgorithmConfiguration.EARLY_DIALOG_WORST_CASE));
        //external leg configuration
        ex.setHost(src.getString("external.host",ExternalLegConfiguration.HOST));
        String externalIpLoadBalancerAddresses = src.getString("external.ipLoadBalancerAddress", ExternalLegConfiguration.IP_LOAD_BALANCER_ADRESS);
        if(externalIpLoadBalancerAddresses != null&&!externalIpLoadBalancerAddresses.equals("")) {
        	ArrayList<String> externalIpLoadBalancerAddressesList = new ArrayList<String>();
        	StringTokenizer tokens = new StringTokenizer(externalIpLoadBalancerAddresses, ",");
        	while (tokens.hasMoreTokens()) {
				String token = tokens.nextToken();
				externalIpLoadBalancerAddressesList.add(token);
			}
        	ex.setIpLoadBalancerAddress(externalIpLoadBalancerAddressesList);
        }
        if(src.getString("external.udpPort") != null && !src.getString("external.udpPort").equals(""))
        	ex.setUdpPort(src.getInteger("external.udpPort", ExternalLegConfiguration.UDP_PORT));
        if(src.getString("external.tcpPort") != null && !src.getString("external.tcpPort").equals(""))
        	ex.setTcpPort(src.getInteger("external.tcpPort", ExternalLegConfiguration.TCP_PORT));
        if(src.getString("external.tlsPort") != null && !src.getString("external.tlsPort").equals(""))
        	ex.setTlsPort(src.getInteger("external.tlsPort", ExternalLegConfiguration.TLS_PORT));
        if(src.getString("external.wsPort") != null && !src.getString("external.wsPort").equals(""))
        	ex.setWsPort(src.getInteger("external.wsPort", ExternalLegConfiguration.WS_PORT));
        if(src.getString("external.wssPort") != null && !src.getString("external.wssPort").equals(""))
        	ex.setWssPort(src.getInteger("external.wssPort", ExternalLegConfiguration.WSS_PORT));
        if(src.getString("external.ipLoadBalancerUdpPort") != null && !src.getString("external.ipLoadBalancerUdpPort").equals(""))
        	ex.setIpLoadBalancerUdpPort(src.getInteger("external.ipLoadBalancerUdpPort",ExternalLegConfiguration.IP_LOAD_BALANCER_UDP_PORT));
        if(src.getString("external.ipLoadBalancerTcpPort") != null && !src.getString("external.ipLoadBalancerTcpPort").equals(""))
        	ex.setIpLoadBalancerTcpPort(src.getInteger("external.ipLoadBalancerTcpPort",ExternalLegConfiguration.IP_LOAD_BALANCER_TCP_PORT));
        if(src.getString("external.ipLoadBalancerTlsPort") != null && !src.getString("external.ipLoadBalancerTlsPort").equals(""))
        	ex.setIpLoadBalancerTlsPort(src.getInteger("external.ipLoadBalancerTlsPort",ExternalLegConfiguration.IP_LOAD_BALANCER_TLS_PORT));
        if(src.getString("external.ipLoadBalancerWsPort") != null && !src.getString("external.ipLoadBalancerWsPort").equals(""))
        	ex.setIpLoadBalancerWsPort(src.getInteger("external.ipLoadBalancerWsPort",ExternalLegConfiguration.IP_LOAD_BALANCER_WS_PORT));
        if(src.getString("external.ipLoadBalancerWssPort") != null && !src.getString("external.ipLoadBalancerWssPort").equals(""))
        	ex.setIpLoadBalancerWssPort(src.getInteger("external.ipLoadBalancerWssPort",ExternalLegConfiguration.IP_LOAD_BALANCER_WSS_PORT));
        //external ipv6
        ex.setIpv6Host(src.getString("external.ipv6Host",ExternalLegConfiguration.IPV6_HOST));
        String externalIpv6LoadBalancerAddresses = src.getString("external.ipv6LoadBalancerAddress", ExternalLegConfiguration.IPV6_LOAD_BALANCER_ADRESS);
        if(externalIpv6LoadBalancerAddresses != null&&!externalIpv6LoadBalancerAddresses.equals("")) {
        	ArrayList<String> externalIpv6LoadBalancerAddressesList = new ArrayList<String>();
        	StringTokenizer tokens = new StringTokenizer(externalIpv6LoadBalancerAddresses, ",");
        	while (tokens.hasMoreTokens()) {
				String token = tokens.nextToken();
				externalIpv6LoadBalancerAddressesList.add(token);
			}
        	ex.setIpv6LoadBalancerAddress(externalIpv6LoadBalancerAddressesList);
        }
        if(src.getString("external.ipv6UdpPort") != null && !src.getString("external.ipv6UdpPort").equals(""))
        	ex.setIpv6UdpPort(src.getInteger("external.ipv6UdpPort", ExternalLegConfiguration.IPV6_UDP_PORT));
        if(src.getString("external.ipv6TcpPort") != null && !src.getString("external.ipv6TcpPort").equals(""))
        	ex.setIpv6TcpPort(src.getInteger("external.ipv6TcpPort", ExternalLegConfiguration.IPV6_TCP_PORT));
        if(src.getString("external.ipv6TlsPort") != null && !src.getString("external.ipv6TlsPort").equals(""))
        	ex.setIpv6TlsPort(src.getInteger("external.ipv6TlsPort", ExternalLegConfiguration.IPV6_TLS_PORT));
        if(src.getString("external.ipv6WsPort") != null && !src.getString("external.ipv6WsPort").equals(""))
        	ex.setIpv6WsPort(src.getInteger("external.ipv6WsPort", ExternalLegConfiguration.IPV6_WS_PORT));
        if(src.getString("external.ipv6WssPort") != null && !src.getString("external.ipv6WssPort").equals(""))
        	ex.setIpv6WssPort(src.getInteger("external.ipv6WssPort", ExternalLegConfiguration.IPV6_WSS_PORT));
        if(src.getString("external.ipv6LoadBalancerUdpPort") != null && !src.getString("external.ipv6LoadBalancerUdpPort").equals(""))
        	ex.setIpv6LoadBalancerUdpPort(src.getInteger("external.ipv6LoadBalancerUdpPort",ExternalLegConfiguration.IPV6_LOAD_BALANCER_UDP_PORT));
        if(src.getString("external.ipv6LoadBalancerTcpPort") != null && !src.getString("external.ipv6LoadBalancerTcpPort").equals(""))
        	ex.setIpv6LoadBalancerTcpPort(src.getInteger("external.ipv6LoadBalancerTcpPort",ExternalLegConfiguration.IPV6_LOAD_BALANCER_TCP_PORT));
        if(src.getString("external.ipv6LoadBalancerTlsPort") != null && !src.getString("external.ipv6LoadBalancerTlsPort").equals(""))
        	ex.setIpv6LoadBalancerTlsPort(src.getInteger("external.ipv6LoadBalancerTlsPort",ExternalLegConfiguration.IPV6_LOAD_BALANCER_TLS_PORT));
        if(src.getString("external.ipv6LoadBalancerWsPort") != null && !src.getString("external.ipv6LoadBalancerWsPort").equals(""))
        	ex.setIpv6LoadBalancerWsPort(src.getInteger("external.ipv6LoadBalancerWsPort",ExternalLegConfiguration.IPV6_LOAD_BALANCER_WS_PORT));
        if(src.getString("external.ipv6LoadBalancerWssPort") != null && !src.getString("external.ipv6LoadBalancerWssPort").equals(""))
        	ex.setIpv6LoadBalancerWssPort(src.getInteger("external.ipv6LoadBalancerWssPort",ExternalLegConfiguration.IPV6_LOAD_BALANCER_WSS_PORT));
        
        //internal leg configuration
        in.setHost(src.getString("internal.host",InternalLegConfiguration.HOST));
        String internalIpLoadBalancerAddresses = src.getString("internal.ipLoadBalancerAddress", InternalLegConfiguration.IP_LOAD_BALANCER_ADRESS);
        if(internalIpLoadBalancerAddresses != null&&!internalIpLoadBalancerAddresses.equals("")) {
        	ArrayList<String> internalIpLoadBalancerAddressesList = new ArrayList<String>();
        	StringTokenizer tokens = new StringTokenizer(internalIpLoadBalancerAddresses, ",");
        	while (tokens.hasMoreTokens()) {
				String token = tokens.nextToken();
				internalIpLoadBalancerAddressesList.add(token);
			}
        	in.setIpLoadBalancerAddress(internalIpLoadBalancerAddressesList);
        }
        if(src.getString("internal.udpPort") != null && !src.getString("internal.udpPort").equals(""))
        	in.setUdpPort(src.getInteger("internal.udpPort", InternalLegConfiguration.UDP_PORT));
        if(src.getString("internal.tcpPort") != null && !src.getString("internal.tcpPort").equals(""))
        	in.setTcpPort(src.getInteger("internal.tcpPort", InternalLegConfiguration.TCP_PORT));
        if(src.getString("internal.tlsPort") != null && !src.getString("internal.tlsPort").equals(""))
        	in.setTlsPort(src.getInteger("internal.tlsPort", InternalLegConfiguration.TLS_PORT));
        if(src.getString("internal.wsPort") != null && !src.getString("internal.wsPort").equals(""))
        	in.setWsPort(src.getInteger("internal.wsPort", InternalLegConfiguration.WS_PORT));
        if(src.getString("internal.wssPort") != null && !src.getString("internal.wssPort").equals(""))
        	in.setWssPort(src.getInteger("internal.wssPort", InternalLegConfiguration.WSS_PORT));
        if(src.getString("internal.ipLoadBalancerUdpPort") != null && !src.getString("internal.ipLoadBalancerUdpPort").equals(""))
        	in.setIpLoadBalancerUdpPort(src.getInteger("internal.ipLoadBalancerUdpPort",InternalLegConfiguration.IP_LOAD_BALANCER_UDP_PORT));
        if(src.getString("internal.ipLoadBalancerTcpPort") != null && !src.getString("internal.ipLoadBalancerTcpPort").equals(""))
        	in.setIpLoadBalancerTcpPort(src.getInteger("internal.ipLoadBalancerTcpPort",InternalLegConfiguration.IP_LOAD_BALANCER_TCP_PORT));
        if(src.getString("internal.ipLoadBalancerTlsPort") != null && !src.getString("internal.ipLoadBalancerTlsPort").equals(""))
        	in.setIpLoadBalancerTlsPort(src.getInteger("internal.ipLoadBalancerTlsPort",InternalLegConfiguration.IP_LOAD_BALANCER_TLS_PORT));
        if(src.getString("internal.ipLoadBalancerWsPort") != null && !src.getString("internal.ipLoadBalancerWsPort").equals(""))
        	in.setIpLoadBalancerWsPort(src.getInteger("internal.ipLoadBalancerWsPort",InternalLegConfiguration.IP_LOAD_BALANCER_WS_PORT));
        if(src.getString("internal.ipLoadBalancerWssPort") != null && !src.getString("internal.ipLoadBalancerWssPort").equals(""))
        	in.setIpLoadBalancerWssPort(src.getInteger("internal.ipLoadBalancerWssPort",InternalLegConfiguration.IP_LOAD_BALANCER_WSS_PORT));
        //ipv6
        in.setIpv6Host(src.getString("internal.ipv6Host",InternalLegConfiguration.IPV6_HOST));
        String internalIpv6LoadBalancerAddresses = src.getString("internal.ipv6LoadBalancerAddress", InternalLegConfiguration.IPV6_LOAD_BALANCER_ADRESS);
        if(internalIpv6LoadBalancerAddresses != null&&!internalIpv6LoadBalancerAddresses.equals("")) {
        	ArrayList<String> internalIpv6LoadBalancerAddressesList = new ArrayList<String>();
        	StringTokenizer tokens = new StringTokenizer(internalIpv6LoadBalancerAddresses, ",");
        	while (tokens.hasMoreTokens()) {
				String token = tokens.nextToken();
				internalIpv6LoadBalancerAddressesList.add(token);
			}
        	in.setIpv6LoadBalancerAddress(internalIpv6LoadBalancerAddressesList);
        };
        if(src.getString("internal.ipv6UdpPort") != null && !src.getString("internal.ipv6UdpPort").equals(""))
        	in.setIpv6UdpPort(src.getInteger("internal.ipv6UdpPort", InternalLegConfiguration.IPV6_UDP_PORT));
        if(src.getString("internal.ipv6TcpPort") != null && !src.getString("internal.ipv6TcpPort").equals(""))
        	in.setIpv6TcpPort(src.getInteger("internal.ipv6TcpPort", InternalLegConfiguration.IPV6_TCP_PORT));
        if(src.getString("internal.ipv6TlsPort") != null && !src.getString("internal.ipv6TlsPort").equals(""))
        	in.setIpv6TlsPort(src.getInteger("internal.ipv6TlsPort", InternalLegConfiguration.IPV6_TLS_PORT));
        if(src.getString("internal.ipv6WsPort") != null && !src.getString("internal.ipv6WsPort").equals(""))
        	in.setIpv6WsPort(src.getInteger("internal.ipv6WsPort", InternalLegConfiguration.IPV6_WS_PORT));
        if(src.getString("internal.ipv6WssPort") != null && !src.getString("internal.ipv6WssPort").equals(""))
        	in.setIpv6WssPort(src.getInteger("internal.ipv6WssPort", InternalLegConfiguration.IPV6_WSS_PORT));
        if(src.getString("internal.ipv6LoadBalancerUdpPort") != null && !src.getString("internal.ipv6LoadBalancerUdpPort").equals(""))
        	in.setIpv6LoadBalancerUdpPort(src.getInteger("internal.ipv6LoadBalancerUdpPort",InternalLegConfiguration.IPV6_LOAD_BALANCER_UDP_PORT));
        if(src.getString("internal.ipv6LoadBalancerTcpPort") != null && !src.getString("internal.ipv6LoadBalancerTcpPort").equals(""))
        	in.setIpv6LoadBalancerTcpPort(src.getInteger("internal.ipv6LoadBalancerTcpPort",InternalLegConfiguration.IPV6_LOAD_BALANCER_TCP_PORT));
        if(src.getString("internal.ipv6LoadBalancerTlsPort") != null && !src.getString("internal.ipv6LoadBalancerTlsPort").equals(""))
        	in.setIpv6LoadBalancerTlsPort(src.getInteger("internal.ipv6LoadBalancerTlsPort",InternalLegConfiguration.IPV6_LOAD_BALANCER_TLS_PORT));
        if(src.getString("internal.ipv6LoadBalancerWsPort") != null && !src.getString("internal.ipv6LoadBalancerWsPort").equals(""))
        	in.setIpv6LoadBalancerWsPort(src.getInteger("internal.ipv6LoadBalancerWsPort",InternalLegConfiguration.IPV6_LOAD_BALANCER_WS_PORT));
        if(src.getString("internal.ipv6LoadBalancerWssPort") != null && !src.getString("internal.ipv6LoadBalancerWssPort").equals(""))
        	in.setIpv6LoadBalancerWssPort(src.getInteger("internal.ipv6LoadBalancerWssPort",InternalLegConfiguration.IPV6_LOAD_BALANCER_WSS_PORT));
    }

    private static void configureHttp(XMLConfiguration xml, HttpConfiguration dst) 
    {
    	HierarchicalConfiguration<ImmutableNode> src = xml.configurationAt("http");
        // Basic HTTP configuration
    	if(!src.getString("httpPort").equals(""))
    		dst.setHttpPort(src.getInteger("httpPort", HttpConfiguration.HTTP_PORT));
        if(!src.getString("httpsPort").equals(""))
        	dst.setHttpsPort(src.getInteger("httpsPort", HttpConfiguration.HTTPS_PORT));
        if(!src.getString("httpPort").equals("")&&!src.getString("httpsPort").equals(""))
        {
        	dst.setUnavailableHost(src.getString("unavailableHost", HttpConfiguration.UNAVAILABLE_HOST));
        	dst.setRequestCheckPattern(src.getString("requestCheckPattern", HttpConfiguration.REQUEST_CHECK_PATTERN));
        }
        setFilterConfig(xml, dst);
     }

    private static void configureSmpp(HierarchicalConfiguration<ImmutableNode> src, SmppConfiguration dst) 
    {
    	// Basic SMPP configuration
    	if (src.getString("smppHost") != null && !src.getString("smppHost").equals(""))
    		dst.setSmppHost(src.getString("smppHost", SmppConfiguration.SMPP_HOST));
    	// Basic SMPP configuration
    	if (src.getString("smppInternalHost") != null && !src.getString("smppInternalHost").equals(""))
    		dst.setSmppInternalHost(src.getString("smppInternalHost", SmppConfiguration.SMPP_INTERNAL_HOST));
    	// Basic SMPP configuration
    	if (src.getString("smppExternalHost") != null && !src.getString("smppExternalHost").equals(""))
    		dst.setSmppExternalHost(src.getString("smppExternalHost", SmppConfiguration.SMPP_EXTERNAL_HOST));
    	
    	if(src.getString("smppPort") != null && !src.getString("smppPort").equals(""))
    		dst.setSmppPort(src.getInteger("smppPort", SmppConfiguration.SMPP_PORT));
    	if(src.getString("smppSslPort") != null && !src.getString("smppSslPort").equals(""))
    		dst.setSmppSslPort(src.getInteger("smppSslPort", SmppConfiguration.SMPP_SSL_PORT));
    	if(!src.getString("smppPort").equals("")||!src.getString("smppSslPort").equals(""))
    	{
    		dst.setMaxConnectionSize(src.getInteger("maxConnectionSize",SmppConfiguration.MAX_CONNECTION_SIZE));
    		dst.setNonBlockingSocketsEnabled(src.getBoolean("nonBlockingSocketsEnabled",SmppConfiguration.NON_BLOCKING_SOCKETS_ENABLED));
    		dst.setDefaultSessionCountersEnabled(src.getBoolean("defaultSessionCountersEnabled",SmppConfiguration.DEFAULT_SESSION_COUNTERS_ENABLED));
    		dst.setTimeoutResponse(src.getInteger("timeoutResponse",SmppConfiguration.TIMEOUT_RESPONSE));
    		dst.setTimeoutConnection(src.getInteger("timeoutConnection",SmppConfiguration.TIMEOUT_CONNECTION));
    		dst.setTimeoutEnquire(src.getInteger("timeoutEnquire",SmppConfiguration.TIMEOUT_ENQUIRE));
    		dst.setReconnectPeriod(src.getInteger("reconnectPeriod",SmppConfiguration.RECONNECT_PERIOD));
    		dst.setTimeoutConnectionCheckClientSide(src.getInteger("timeoutConnectionCheckClientSide",SmppConfiguration.TIMEOUT_CONNECTION_CHECK_CLIENT_SIDE));
    		dst.setTimeoutConnectionCheckServerSide(src.getInteger("timeoutConnectionCheckServerSide",SmppConfiguration.TIMEOUT_CONNECTION_CHECK_SERVER_SIDE));
    		dst.setRemoteServers(src.getString("remoteServers",SmppConfiguration.REMOTE_SERVERS));
    		dst.setMuxMode(src.getBoolean("muxMode", SmppConfiguration.MUX_MODE));
    		if(src.getString("toNodeAlgorithmClass") != null && !src.getString("toNodeAlgorithmClass").equals(""))
    			dst.setSmppToNodeAlgorithmClass(src.getString("toNodeAlgorithmClass",SmppConfiguration.SMPP_TO_NODE_ALGORITHM_CLASS));
    	    if(src.getString("toProviderAlgorithmClass") != null && !src.getString("toProviderAlgorithmClass").equals(""))
    	    	dst.setSmppToProviderAlgorithmClass(src.getString("toProviderAlgorithmClass",SmppConfiguration.SMPP_TO_PROVIDER_ALGORITHM_CLASS));
    	}
    }
    private static void configureSsl(HierarchicalConfiguration<ImmutableNode> src, SslConfiguration dst) 
    {
    	if(!src.getString("terminateTLSTraffic").equals(""))
    		dst.setTerminateTLSTraffic(src.getBoolean("terminateTLSTraffic", SslConfiguration.TERMINATE_TLS_TRAFFIC));
        if(!src.getString("keyStore").equals(""))
        {
        	dst.setKeyStore(src.getString("keyStore",SslConfiguration.KEY_STORE));
           	dst.setKeyStorePassword(src.getString("keyStorePassword",SslConfiguration.KEY_STORE_PASSWORD));
          	dst.setTrustStore(src.getString("trustStore",SslConfiguration.TRUST_STORE));
           	dst.setTrustStorePassword(src.getString("trustStorePassword",SslConfiguration.TRUST_STORE_PASSWORD));
            if(!src.getString("tlsClientProtocols").equals(""))
               	dst.setTlsClientProtocols(src.getString("tlsClientProtocols",SslConfiguration.TLS_CLIENT_PROTOCOLS));
            if(!src.getString("enabledCipherSuites").equals(""))
                dst.setEnabledCipherSuites(src.getString("enabledCipherSuites",SslConfiguration.ENABLED_CIPHER_SUITES));
        }

        
    }
    
    private static void setFilterConfig(XMLConfiguration xmlConfiguration, HttpConfiguration httpConfiguration)
    {
    	Document doc = xmlConfiguration.getDocument();
        NodeList nodes = doc.getElementsByTagName("urlrewrite");
        Node node = null;
        for(int i = 0; i < nodes.getLength(); i++)
        {
        	if(nodes.item(i).getNodeName().equals("urlrewrite"))
         		node = nodes.item(i);
        }
        if(node!=null)
        {
        	DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        	factory.setNamespaceAware(true);
        	DocumentBuilder builder = null;
        	try {
        		builder = factory.newDocumentBuilder();
        	} catch (ParserConfigurationException e) {
        		e.printStackTrace();
        	}
        	Document urlRewriteRuleDocument = builder.newDocument();
        	Node importedNode = urlRewriteRuleDocument.importNode(node, true);
        	urlRewriteRuleDocument.appendChild(importedNode);
        	httpConfiguration.setUrlrewriteRule(urlRewriteRuleDocument);
        }
    }
    private void setHeartbeatConfig(LoadBalancerConfiguration lbConfiguration, XMLConfiguration xmlConfiguration)
    {
    	String configClassString = xmlConfiguration.getString("heartbeat[@configclass]");
    	if(configClassString!=null)
    	{
    		lbConfiguration.setHeartbeatConfigurationClass(xmlConfiguration.getString("heartbeat[@configclass]"));
    		Document doc = xmlConfiguration.getDocument();
    		NodeList nodes = doc.getElementsByTagName("heartbeatConfig");
    		Node node = null;
    		int lentgth = nodes.getLength();
    		for(int i = 0; i < lentgth; i++)
    		{
    			if(nodes.item(i).getNodeName().equals("heartbeatConfig"))
    				node = nodes.item(i);
    		}
    		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    		factory.setNamespaceAware(true);
    		DocumentBuilder builder = null;
    		try {
    			builder = factory.newDocumentBuilder();
    		} catch (ParserConfigurationException e) {
    			e.printStackTrace();
    		}
    		Document heartbeatConfigDocument = builder.newDocument();
    		Node importedNode = heartbeatConfigDocument.importNode(node, true);
    		heartbeatConfigDocument.appendChild(importedNode);

    		JAXBContext jc = null;
    		Unmarshaller u = null;
    		try {
    			jc = JAXBContext.newInstance(Class.forName(lbConfiguration.getHeartbeatConfigurationClass()));
    			u = jc.createUnmarshaller();
    			lbConfiguration.setHeartbeatConfiguration((HeartbeatConfig) u.unmarshal(heartbeatConfigDocument));
    		} catch (ClassNotFoundException | JAXBException e) {
    			e.printStackTrace();
    		}
    	}
    }
}

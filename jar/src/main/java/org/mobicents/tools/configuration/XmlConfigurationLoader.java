package org.mobicents.tools.configuration;

import java.io.File;
import java.util.List;

import org.apache.log4j.Logger;
import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.commons.configuration2.ex.ConfigurationException;

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

            // Overwrite default configurations
            configureCommon(xml.configurationAt("common"), configuration.getCommonConfiguration());
            configureHttp(xml.configurationAt("http"), configuration.getHttpConfiguration());
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
        dst.setRmiRegistryPort(src.getInteger("rmiRegistryPort",CommonConfiguration.RMI_REGISTRY_PORT));
        dst.setRmiRemoteObjectPort(src.getInteger("rmiRemoteObjectPort",CommonConfiguration.RMI_REMOTE_OBJECT_PORT));
        dst.setNodeTimeout(src.getInteger("nodeTimeout", CommonConfiguration.NODE_TIMEOUT));
        dst.setHeartbeatInterval(src.getInteger("heartbeatInterval", CommonConfiguration.HARDBEAT_INTERVAL));
        dst.setStatisticPort(src.getInteger("statisticPort", CommonConfiguration.STATISTIC_PORT));
    }

    private static void configureSip(HierarchicalConfiguration<ImmutableNode> src, SipConfiguration dst) {
    	ExternalLegConfiguration ex = dst.getExternalLegConfiguration();
    	InternalLegConfiguration in = dst.getInternalLegConfiguration();
    	AlgorithmConfiguration alg = dst.getAlgorithmConfiguration();
    	// Basic SIP configuration
    	src.getString("publicIp", SipConfiguration.PUBLIC_IP);
        dst.setPublicIp(src.getString("publicIp", SipConfiguration.PUBLIC_IP));
        dst.setExtraServerNodes(src.getString("extraServerNodes",SipConfiguration.EXTRA_SERVER_NODES));
        dst.setPerformanceTestingMode(src.getBoolean("performanceTestingMode",SipConfiguration.PERFORMANCE_TESTING_MODE));
        dst.setUseIpLoadBalancerAddressInViaHeaders(src.getBoolean("useIpLoadBalancerAddressInViaHeaders",SipConfiguration.USE_IP_LOAD_BALANCER_ADRESS_IN_VIA_HEADERS));
        dst.setSendTrying(src.getBoolean("isSendTrying",SipConfiguration.IS_SEND_TRYING));
        dst.setBlockedValues(src.getString("blockedValues",SipConfiguration.BLOCKED_VALUES));
        dst.setUsePrettyEncoding(src.getBoolean("usePrettyEncoding", SipConfiguration.USE_PRETTY_ENCODING));
        dst.setIsSend5xxResponse(src.getBoolean("isSend5xxResponse", SipConfiguration.IS_SEND_5XX_RESPONSE));
        dst.setIsSend5xxResponseSatusCode(src.getInteger("isSend5xxResponseSatusCode", SipConfiguration.IS_SEND_5XX_RESPONSE_STATUS_CODE));
        dst.setIsSend5xxResponseReasonHeader(src.getString("isSend5xxResponseReasonHeader", SipConfiguration.IS_SEND_5XX_RESPONSE_REASON_HEADER));
        dst.setResponseStatusCodeNodeRemoval(src.getInteger("responseStatusCodeNodeRemoval", SipConfiguration.RESPONSE_STATUS_CODE_NODE_REMOVAL));
        dst.setResponseReasonNodeRemoval(src.getString("responseReasonNodeRemoval", SipConfiguration.RESPONSE_REASON_NODE_REMOVAL));
        dst.setIsUseWithNexmo(src.getBoolean("isUseWithNexmo",SipConfiguration.IS_USE_WITH_NEXMO));
        dst.setMatchingHostnameForRoute(src.getString("matchingHostnameForRoute", SipConfiguration.MATCHING_HOSTNAME_FOR_ROUTE));
        dst.setIsFilterSubdomain(src.getBoolean("isFilterSubdomain", SipConfiguration.IS_FILTER_SUBDOMAIN));
        //Algorithm configuration
        if(!src.getString("algorithm.algorithmClass").equals(""))
        	alg.setAlgorithmClass(src.getString("algorithm.algorithmClass",AlgorithmConfiguration.ALGORITHM_CLASS));
        alg.setSipHeaderAffinityKey(src.getString("algorithm.sipHeaderAffinityKey",AlgorithmConfiguration.SIP_HEADER_AFFINITY_KEY));
        alg.setCallIdAffinityGroupFailover(src.getBoolean("algorithm.callIdAffinityGroupFailover",AlgorithmConfiguration.CALL_ID_AFFINITY_GROUP_FAILOVER));
        alg.setCallIdAffinityMaxTimeInCache(src.getInteger("algorithm.callIdAffinityMaxTimeInCache",AlgorithmConfiguration.CALL_ID_AFFINITY_MAX_TIME_IN_CACHE));
        alg.setHttpAffinityKey(src.getString("algorithm.httpAffinityKey",AlgorithmConfiguration.HTTP_AFFINITY_KEY));
        alg.setPersistentConsistentHashCacheConfiguration(src.getString("algorithm.persistentConsistentHashCacheConfiguration",AlgorithmConfiguration.PERSISTENT_CONSISTENT_HASH_CACHE_CONFIG));
        alg.setSubclusterMap(src.getString("subclusterMap",AlgorithmConfiguration.SUBCLUSTER_MAP));
        alg.setEarlyDialogWorstCase(src.getBoolean("earlyDialogWorstCase",AlgorithmConfiguration.EARLY_DIALOG_WORST_CASE));
        //external leg configuration
        ex.setHost(src.getString("external.host",CommonConfiguration.HOST));
        if(!src.getString("external.ipLoadBalancerAddress").equals(""))
        	ex.setIpLoadBalancerAddress(src.getString("external.ipLoadBalancerAddress", ExternalLegConfiguration.IP_LOAD_BALANCER_ADRESS));
        if(!src.getString("external.udpPort").equals(""))
        	ex.setUdpPort(src.getInteger("external.udpPort", ExternalLegConfiguration.UDP_PORT));
        if(!src.getString("external.tcpPort").equals(""))
        	ex.setTcpPort(src.getInteger("external.tcpPort", ExternalLegConfiguration.TCP_PORT));
        if(!src.getString("external.tlsPort").equals(""))
        	ex.setTlsPort(src.getInteger("external.tlsPort", ExternalLegConfiguration.TLS_PORT));
        if(!src.getString("external.wsPort").equals(""))
        	ex.setWsPort(src.getInteger("external.wsPort", ExternalLegConfiguration.WS_PORT));
        if(!src.getString("external.wssPort").equals(""))
        	ex.setWssPort(src.getInteger("external.wssPort", ExternalLegConfiguration.WSS_PORT));
        if(!src.getString("external.ipLoadBalancerUdpPort").equals(""))
        	ex.setIpLoadBalancerUdpPort(src.getInteger("external.ipLoadBalancerUdpPort",ExternalLegConfiguration.IP_LOAD_BALANCER_UDP_PORT));
        if(!src.getString("external.ipLoadBalancerTcpPort").equals(""))
        	ex.setIpLoadBalancerTcpPort(src.getInteger("external.ipLoadBalancerTcpPort",ExternalLegConfiguration.IP_LOAD_BALANCER_TCP_PORT));
        if(!src.getString("external.ipLoadBalancerTlsPort").equals(""))
        	ex.setIpLoadBalancerTlsPort(src.getInteger("external.ipLoadBalancerTlsPort",ExternalLegConfiguration.IP_LOAD_BALANCER_TLS_PORT));
        if(!src.getString("external.ipLoadBalancerWsPort").equals(""))
        	ex.setIpLoadBalancerWsPort(src.getInteger("external.ipLoadBalancerWsPort",ExternalLegConfiguration.IP_LOAD_BALANCER_WS_PORT));
        if(!src.getString("external.ipLoadBalancerWssPort").equals(""))
        	ex.setIpLoadBalancerWssPort(src.getInteger("external.ipLoadBalancerWssPort",ExternalLegConfiguration.IP_LOAD_BALANCER_WSS_PORT));
        //internal leg configuration
        in.setHost(src.getString("internal.host",CommonConfiguration.HOST));
        if(!src.getString("internal.ipLoadBalancerAddress").equals(""))
        	in.setIpLoadBalancerAddress(src.getString("internal.ipLoadBalancerAddress", InternalLegConfiguration.IP_LOAD_BALANCER_ADRESS));
        if(!src.getString("internal.udpPort").equals(""))
        	in.setUdpPort(src.getInteger("internal.udpPort", InternalLegConfiguration.UDP_PORT));
        if(!src.getString("internal.tcpPort").equals(""))
        	in.setTcpPort(src.getInteger("internal.tcpPort", InternalLegConfiguration.TCP_PORT));
        if(!src.getString("internal.tlsPort").equals(""))
        	in.setTlsPort(src.getInteger("internal.tlsPort", InternalLegConfiguration.TLS_PORT));
        if(!src.getString("internal.wsPort").equals(""))
        	in.setWsPort(src.getInteger("internal.wsPort", InternalLegConfiguration.WS_PORT));
        if(!src.getString("internal.wssPort").equals(""))
        	in.setWssPort(src.getInteger("internal.wssPort", InternalLegConfiguration.WSS_PORT));
        if(!src.getString("internal.ipLoadBalancerUdpPort").equals(""))
        	in.setIpLoadBalancerUdpPort(src.getInteger("internal.ipLoadBalancerUdpPort",InternalLegConfiguration.IP_LOAD_BALANCER_UDP_PORT));
        if(!src.getString("internal.ipLoadBalancerTcpPort").equals(""))
        	in.setIpLoadBalancerTcpPort(src.getInteger("internal.ipLoadBalancerTcpPort",InternalLegConfiguration.IP_LOAD_BALANCER_TCP_PORT));
        if(!src.getString("internal.ipLoadBalancerTlsPort").equals(""))
        	in.setIpLoadBalancerTlsPort(src.getInteger("internal.ipLoadBalancerTlsPort",InternalLegConfiguration.IP_LOAD_BALANCER_TLS_PORT));
        if(!src.getString("internal.ipLoadBalancerWsPort").equals(""))
        	in.setIpLoadBalancerWsPort(src.getInteger("internal.ipLoadBalancerWsPort",InternalLegConfiguration.IP_LOAD_BALANCER_WS_PORT));
        if(!src.getString("internal.ipLoadBalancerWssPort").equals(""))
        	in.setIpLoadBalancerWssPort(src.getInteger("internal.ipLoadBalancerWssPort",InternalLegConfiguration.IP_LOAD_BALANCER_WSS_PORT));
    }

    private static void configureHttp(HierarchicalConfiguration<ImmutableNode> src, HttpConfiguration dst) 
    {
        // Basic HTTP configuration
    	if(!src.getString("httpPort").equals(""))
    		dst.setHttpPort(src.getInteger("httpPort", HttpConfiguration.HTTP_PORT));
        if(!src.getString("httpsPort").equals(""))
        	dst.setHttpsPort(src.getInteger("httpsPort", HttpConfiguration.HTTPS_PORT));
        if(!src.getString("httpPort").equals("")&&!src.getString("httpsPort").equals(""))
        {
        	dst.setMaxContentLength(src.getInteger("maxContentLength",HttpConfiguration.MAX_CONTENT_LENT));
        	dst.setUnavailableHost(src.getString("unavailableHost", HttpConfiguration.UNAVAILABLE_HOST));
        }
     }

    private static void configureSmpp(HierarchicalConfiguration<ImmutableNode> src, SmppConfiguration dst) 
    {
    	// Basic SMPP configuration
    	dst.setSmppHost(src.getString("smppHost", SmppConfiguration.SMPP_HOST));
    	if(!src.getString("smppPort").equals(""))
    		dst.setSmppPort(src.getInteger("smppPort", SmppConfiguration.SMPP_PORT));
    	if(!src.getString("smppSslPort").equals(""))
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
    		dst.setIsUseRrSendSmppRequestToClient(src.getBoolean("isUseRrSendSmppRequestToClient",SmppConfiguration.IS_USE_RR_SEND_SMPP_REQUEST_TO_CLIENT));
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
}

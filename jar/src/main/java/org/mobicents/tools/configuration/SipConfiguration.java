package org.mobicents.tools.configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class SipConfiguration {
	
	private final AlgorithmConfiguration algorithmConfiguration;
	private final ExternalLegConfiguration externalLegConfiguration;
    private final InternalLegConfiguration internalLegConfiguration;
    
    public static final Boolean IS_SEND_TRYING = true;
	public static final String PUBLIC_IP = null;
	public static final String PUBLIC_IPV6 = null;
	public static final Boolean USE_IP_LOAD_BALANCER_ADRESS_IN_VIA_HEADERS = false;
	public static final Boolean PERFORMANCE_TESTING_MODE = false;
	public static final String EXTRA_SERVER_NODES = null;
	public static final String BLOCKED_VALUES = "sipvicious,sipcli,friendly-scanner";
	public static final Boolean USE_PRETTY_ENCODING = false; 
	public static final Boolean IS_SEND_5XX_RESPONSE = false;
	public static final String IS_SEND_5XX_RESPONSE_REASON_HEADER = null;
	public static final Integer IS_SEND_5XX_RESPONSE_STATUS_CODE = 503;
	public static final String RESPONSES_STATUS_CODE_NODE_REMOVAL = "503";
	public static final Boolean IS_USE_WITH_NEXMO = false;
	public static final String MATCHING_HOSTNAME_FOR_ROUTE = null;
	public static final Boolean IS_FILTER_SUBDOMAIN = false;
	public static final String INTERNAL_TRANSPORT = null;

	private Boolean isSendTrying;
	private String publicIp;
	private String publicIpv6;
	private Boolean useIpLoadBalancerAddressInViaHeaders;
	private Boolean performanceTestingMode;
	private String extraServerNodes;
	private String blockedValues;
	private Boolean usePrettyEncoding;
	private Boolean isSend5xxResponse;
	private String isSend5xxResponseReasonHeader;
	private Integer isSend5xxResponseSatusCode;
	private List<Integer> responsesStatusCodeNodeRemoval;
	private Boolean isUseWithNexmo;
	private String matchingHostnameForRoute;
	private Boolean isFilterSubdomain;
	private String internalTransport;
	
	public SipConfiguration() 
    {
		this.algorithmConfiguration = new AlgorithmConfiguration();
		this.externalLegConfiguration = new ExternalLegConfiguration();
		this.internalLegConfiguration = new InternalLegConfiguration();
		
		this.isSendTrying = IS_SEND_TRYING;
        this.publicIp = PUBLIC_IP;
        this.publicIpv6 = PUBLIC_IPV6;
        this.useIpLoadBalancerAddressInViaHeaders = USE_IP_LOAD_BALANCER_ADRESS_IN_VIA_HEADERS;
        this.performanceTestingMode = PERFORMANCE_TESTING_MODE;
        this.extraServerNodes = EXTRA_SERVER_NODES;
        this.blockedValues = BLOCKED_VALUES;
        this.usePrettyEncoding = USE_PRETTY_ENCODING;
        this.isSend5xxResponse = IS_SEND_5XX_RESPONSE;
        this.isSend5xxResponseReasonHeader = IS_SEND_5XX_RESPONSE_REASON_HEADER;
        this.isSend5xxResponseSatusCode = IS_SEND_5XX_RESPONSE_STATUS_CODE;
        this.responsesStatusCodeNodeRemoval = new ArrayList<Integer>();
        this.isUseWithNexmo = IS_USE_WITH_NEXMO;
        this.matchingHostnameForRoute = MATCHING_HOSTNAME_FOR_ROUTE;
        this.isFilterSubdomain = IS_FILTER_SUBDOMAIN;
        this.internalTransport = INTERNAL_TRANSPORT;
    }

	public AlgorithmConfiguration getAlgorithmConfiguration() {
		return algorithmConfiguration;
	}

	public ExternalLegConfiguration getExternalLegConfiguration() 
	{
		return externalLegConfiguration;
	}

	public InternalLegConfiguration getInternalLegConfiguration() 
	{
		return internalLegConfiguration;
	}

	public String getPublicIp() 
	{
		return publicIp;
	}

	public void setPublicIp(String publicIp) 
	{
		this.publicIp = publicIp;
	}

	public String getPublicIpv6() {
		return publicIpv6;
	}

	public void setPublicIpv6(String publicIpv6) {
		this.publicIpv6 = publicIpv6;
	}

	public String getExtraServerNodes() 
	{
		return extraServerNodes;
	}

	public void setExtraServerNodes(String extraServerNodes) 
	{
		this.extraServerNodes = extraServerNodes;
	}

	public Boolean isSendTrying() 
	{
		return isSendTrying;
	}

	public void setSendTrying(Boolean isSendTrying) 
	{
		this.isSendTrying = isSendTrying;
	}

	public Boolean isUseIpLoadBalancerAddressInViaHeaders() 
	{
		return useIpLoadBalancerAddressInViaHeaders;
	}

	public void setUseIpLoadBalancerAddressInViaHeaders(Boolean useIpLoadBalancerAddressInViaHeaders) 
	{
		this.useIpLoadBalancerAddressInViaHeaders = useIpLoadBalancerAddressInViaHeaders;
	}

	public Boolean isPerformanceTestingMode() 
	{
		return performanceTestingMode;
	}

	public void setPerformanceTestingMode(Boolean performanceTestingMode) 
	{
		this.performanceTestingMode = performanceTestingMode;
	}

	public String getBlockedValues() {
		return blockedValues;
	}

	public void setBlockedValues(String blockedValues) {
		this.blockedValues = blockedValues;
	}

	public Boolean isUsePrettyEncoding() {
		return usePrettyEncoding;
	}

	public void setUsePrettyEncoding(Boolean usePrettyEncoding) {
		this.usePrettyEncoding = usePrettyEncoding;
	}

	public Boolean getIsSend5xxResponse() {
		return isSend5xxResponse;
	}

	public void setIsSend5xxResponse(Boolean isSend5xxResponse) {
		this.isSend5xxResponse = isSend5xxResponse;
	}

	public String getIsSend5xxResponseReasonHeader() {
		return isSend5xxResponseReasonHeader;
	}

	public void setIsSend5xxResponseReasonHeader(String isSend5xxResponseReasonHeader) {
		this.isSend5xxResponseReasonHeader = isSend5xxResponseReasonHeader;
	}

	public Integer getIsSend5xxResponseSatusCode() {
		return isSend5xxResponseSatusCode;
	}

	public void setIsSend5xxResponseSatusCode(Integer isSend5xxResponseSatusCode) {
		this.isSend5xxResponseSatusCode = isSend5xxResponseSatusCode;
	}

	public List<Integer> getResponsesStatusCodeNodeRemoval() {
		return responsesStatusCodeNodeRemoval;
	}

	public void setResponseStatusCodeNodeRemoval(List<Integer> responsesStatusCodeNodeRemoval) {
		this.responsesStatusCodeNodeRemoval = responsesStatusCodeNodeRemoval;
	}

	public Boolean getIsUseWithNexmo() {
		return isUseWithNexmo;
	}

	public void setIsUseWithNexmo(Boolean isUseWithNexmo) {
		this.isUseWithNexmo = isUseWithNexmo;
	}

	public String getMatchingHostnameForRoute() {
		return matchingHostnameForRoute;
	}

	public void setMatchingHostnameForRoute(String matchingHostnameForRoute) {
		this.matchingHostnameForRoute = matchingHostnameForRoute;
	}

	public Boolean getIsFilterSubdomain() {
		return isFilterSubdomain;
	}

	public void setIsFilterSubdomain(Boolean isFilterSubdomain) {
		this.isFilterSubdomain = isFilterSubdomain;
	}

	public String getInternalTransport() {
		return internalTransport;
	}

	public void setInternalTransport(String internalTransport) {
		this.internalTransport = internalTransport;
	}
	
}

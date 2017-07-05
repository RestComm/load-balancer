package org.mobicents.tools.configuration;

import java.util.ArrayList;
import java.util.List;

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
	public static final Integer MAX_NUMBER_RESPONSES_WITH_ERROR = 5;
	public static final Long MAX_ERROR_TIME = 300000l;
	public static final Boolean IS_USE_WITH_NEXMO = false;
	public static final String MATCHING_HOSTNAME_FOR_ROUTE = null;
	public static final Boolean IS_FILTER_SUBDOMAIN = false;
	public static final String INTERNAL_TRANSPORT = null;
	public static final Integer TRAFFIC_RAMPUP_CYCLE_PERIOD = null;
	public static final Integer MAX_WEIGHT_INDEX = null;
	public static final Integer MAX_REQUEST_NUMBER_WITHOUT_RESPONSE = null;
	public static final Long MAX_RESPONSE_TIME = null;

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
	private Integer maxNumberResponsesWithError;
	private Long maxErrorTime;
	private Boolean isUseWithNexmo;
	private String matchingHostnameForRoute;
	private Boolean isFilterSubdomain;
	private String internalTransport;
	private Integer trafficRampupCyclePeriod;
	private Integer maxWeightIndex;
	private Integer maxRequestNumberWithoutResponse;
	private Long maxResponseTime;
	
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
        this.maxNumberResponsesWithError = MAX_NUMBER_RESPONSES_WITH_ERROR;
        this.maxErrorTime = MAX_ERROR_TIME;
        this.isUseWithNexmo = IS_USE_WITH_NEXMO;
        this.matchingHostnameForRoute = MATCHING_HOSTNAME_FOR_ROUTE;
        this.isFilterSubdomain = IS_FILTER_SUBDOMAIN;
        this.internalTransport = INTERNAL_TRANSPORT;
        this.trafficRampupCyclePeriod = TRAFFIC_RAMPUP_CYCLE_PERIOD;
        this.maxWeightIndex = MAX_WEIGHT_INDEX;
        this.maxRequestNumberWithoutResponse = MAX_REQUEST_NUMBER_WITHOUT_RESPONSE;
        this.maxResponseTime = MAX_RESPONSE_TIME;
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

	public Integer getMaxNumberResponsesWithError() {
		return maxNumberResponsesWithError;
	}

	public void setMaxNumberResponsesWithError(Integer maxNumberResponsesWithError) {
		this.maxNumberResponsesWithError = maxNumberResponsesWithError;
	}

	public Long getMaxErrorTime() {
		return maxErrorTime;
	}

	public void setMaxErrorTime(Long maxErrorTime) {
		this.maxErrorTime = maxErrorTime;
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

	public Integer getTrafficRampupCyclePeriod() {
		return trafficRampupCyclePeriod;
	}

	public void setTrafficRampupCyclePeriod(Integer trafficRampupCyclePeriod) {
		this.trafficRampupCyclePeriod = trafficRampupCyclePeriod;
	}

	public Integer getMaxWeightIndex() {
		return maxWeightIndex;
	}

	public void setTrafficPercentageIncrease(Integer trafficPercentageIncrease) {
		//convert percentage to weight based system
		this.maxWeightIndex = 100/trafficPercentageIncrease;
	}

	public Integer getMaxRequestNumberWithoutResponse() {
		return maxRequestNumberWithoutResponse;
	}

	public void setMaxRequestNumberWithoutResponse(Integer maxRequestNumberWithoutResponse) {
		this.maxRequestNumberWithoutResponse = maxRequestNumberWithoutResponse;
	}

	public Long getMaxResponseTime() {
		return maxResponseTime;
	}

	public void setMaxResponseTime(Long maxResponseTime) {
		this.maxResponseTime = maxResponseTime;
	}
	
}

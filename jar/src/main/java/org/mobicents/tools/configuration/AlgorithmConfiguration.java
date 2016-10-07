package org.mobicents.tools.configuration;

import org.mobicents.tools.sip.balancer.CallIDAffinityBalancerAlgorithm;

public class AlgorithmConfiguration {
	
	public static final String ALGORITHM_CLASS = CallIDAffinityBalancerAlgorithm.class.getCanonicalName();
	public static final Integer CALL_ID_AFFINITY_MAX_TIME_IN_CACHE = 500;
	public static final Boolean CALL_ID_AFFINITY_GROUP_FAILOVER = false;
	public static final String SIP_HEADER_AFFINITY_KEY = "Call-ID";
	public static final String HTTP_AFFINITY_KEY = "appsession";
	public static final String PERSISTENT_CONSISTENT_HASH_CACHE_CONFIG = null;
	public static final String SUBCLUSTER_MAP = null;
	public static final Boolean EARLY_DIALOG_WORST_CASE = false;
	
	private String algorithmClass;
	private Integer callIdAffinityMaxTimeInCache;
	private Boolean	callIdAffinityGroupFailover;
	private String sipHeaderAffinityKey;
	private String httpAffinityKey;
	private String persistentConsistentHashCacheConfiguration;
	private String subclusterMap;
	private Boolean earlyDialogWorstCase;
	
	public AlgorithmConfiguration()
	{
		this.algorithmClass = ALGORITHM_CLASS;
		this.callIdAffinityMaxTimeInCache = CALL_ID_AFFINITY_MAX_TIME_IN_CACHE;
		this.callIdAffinityGroupFailover = CALL_ID_AFFINITY_GROUP_FAILOVER;
		this.sipHeaderAffinityKey = SIP_HEADER_AFFINITY_KEY;
		this.httpAffinityKey = HTTP_AFFINITY_KEY;
		this.persistentConsistentHashCacheConfiguration = PERSISTENT_CONSISTENT_HASH_CACHE_CONFIG;
		this.subclusterMap = SUBCLUSTER_MAP;
		this.earlyDialogWorstCase = EARLY_DIALOG_WORST_CASE;
	}

	public void setCallIdAffinityGroupFailover(Boolean callIdAffinityGroupFailover) {
		this.callIdAffinityGroupFailover = callIdAffinityGroupFailover;
	}

	public String getAlgorithmClass() 
	{
		return algorithmClass;
	}

	public void setAlgorithmClass(String algorithmClass) 
	{
		this.algorithmClass = algorithmClass;
	}

	public Integer getCallIdAffinityMaxTimeInCache() 
	{
		return callIdAffinityMaxTimeInCache;
	}

	public void setCallIdAffinityMaxTimeInCache(Integer callIdAffinityMaxTimeInCache) 
	{
		this.callIdAffinityMaxTimeInCache = callIdAffinityMaxTimeInCache;
	}

	public Boolean isCallIdAffinityGroupFailover() 
	{
		return callIdAffinityGroupFailover;
	}

	public String getSipHeaderAffinityKey() 
	{
		return sipHeaderAffinityKey;
	}

	public void setSipHeaderAffinityKey(String sipHeaderAffinityKey) 
	{
		this.sipHeaderAffinityKey = sipHeaderAffinityKey;
	}

	public String getHttpAffinityKey() 
	{
		return httpAffinityKey;
	}

	public void setHttpAffinityKey(String httpAffinityKey) 
	{
		this.httpAffinityKey = httpAffinityKey;
	}

	public String getPersistentConsistentHashCacheConfiguration() 
	{
		return persistentConsistentHashCacheConfiguration;
	}

	public void setPersistentConsistentHashCacheConfiguration(String persistentConsistentHashCacheConfiguration) 
	{
		this.persistentConsistentHashCacheConfiguration = persistentConsistentHashCacheConfiguration;
	}

	public String getSubclusterMap() 
	{
		return subclusterMap;
	}

	public void setSubclusterMap(String subclusterMap) 
	{
		this.subclusterMap = subclusterMap;
	}

	public Boolean isEarlyDialogWorstCase() {
		return earlyDialogWorstCase;
	}

	public void setEarlyDialogWorstCase(Boolean earlyDialogWorstCase) {
		this.earlyDialogWorstCase = earlyDialogWorstCase;
	}
	
}

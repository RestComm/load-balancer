package org.mobicents.tools.sip.balancer;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.apache.log4j.Logger;

public class StatisticObject 
{
	private static Logger logger = Logger.getLogger(NodesInfoObject.class);
	private String releaseName;
	private String releaseVersion;
	private Integer activeNodes;
	private Integer JvmCpuUsage;
	private Long JvmHeapSize;
	private Integer NumberOfActiveHttpConnections; 
	private Integer NumberOfActiveSipConnections;
	private Integer NumberOfActiveSmppConnections;
	private Long NumberOfBytesTransferred;
	private Long NumberOfHttpBytesToClient;
	private Long NumberOfHttpBytesToServer;
	private Long NumberOfHttpRequests;
	private Long NumberOfRequestsProcessed;
	private Long NumberOfResponsesProcessed;
	private Long NumberOfSmppBytesToClient;
	private Long NumberOfSmppBytesToServer;
	private Long NumberOfSmppRequestsToClient;
	private Long NumberOfSmppRequestsToServer;
	
	public StatisticObject(BalancerRunner balancerRunner){
		
		Properties releaseProperties = new Properties();
		try {
			InputStream in = BalancerRunner.class.getResourceAsStream("release.properties");
			if(in != null) {
				releaseProperties.load(in);
				in.close();
				this.releaseVersion = releaseProperties.getProperty("release.version");
				this.releaseName = releaseProperties.getProperty("release.name");
			}
		} catch (IOException e) 
		{
			logger.warn("Unable to extract the version of Restcomm Load Balancer currently running", e);
		}
		this.JvmCpuUsage = (int)(balancerRunner.getJvmCpuUsage()*100.0);
		this.JvmHeapSize = balancerRunner.getJvmHeapSize();
		this.NumberOfActiveHttpConnections = balancerRunner.getNumberOfActiveHttpConnections();
		this.NumberOfActiveSipConnections = balancerRunner.getNumberOfActiveSipConnections();
		this.NumberOfActiveSmppConnections = balancerRunner.getNumberOfActiveSmppConnections();
		this.NumberOfBytesTransferred = balancerRunner.getNumberOfBytesTransferred();
		this.NumberOfHttpBytesToClient = balancerRunner.getNumberOfHttpBytesToClient();
		this.NumberOfHttpBytesToServer = balancerRunner.getNumberOfHttpBytesToServer();
		this.NumberOfHttpRequests = balancerRunner.getNumberOfHttpRequests();
		this.NumberOfRequestsProcessed = balancerRunner.getNumberOfRequestsProcessed();
		this.NumberOfResponsesProcessed = balancerRunner.getNumberOfResponsesProcessed();
		this.NumberOfSmppBytesToClient = balancerRunner.getNumberOfSmppBytesToClient();
		this.NumberOfSmppBytesToServer = balancerRunner.getNumberOfSmppBytesToServer();
		this.NumberOfSmppRequestsToClient = balancerRunner.getNumberOfSmppRequestsToClient();
		this.NumberOfSmppRequestsToServer = balancerRunner.getNumberOfSmppRequestsToServer();
		this.activeNodes = balancerRunner.getLatestInvocationContext().sipNodeMap(false).size() + 
				balancerRunner.getLatestInvocationContext().sipNodeMap(true).size();
	}

	public Integer getJvmCpuUsage() {
		return JvmCpuUsage;
	}

	public void setJvmCpuUsage(Integer jvmCpuUsage) {
		JvmCpuUsage = jvmCpuUsage;
	}

	public Long getJvmHeapSize() {
		return JvmHeapSize;
	}

	public void setJvmHeapSize(Long jvmHeapSize) {
		JvmHeapSize = jvmHeapSize;
	}

	public Integer getNumberOfActiveHttpConnections() {
		return NumberOfActiveHttpConnections;
	}

	public void setNumberOfActiveHttpConnections(
			Integer numberOfActiveHttpConnections) {
		NumberOfActiveHttpConnections = numberOfActiveHttpConnections;
	}

	public Integer getNumberOfActiveSipConnections() {
		return NumberOfActiveSipConnections;
	}

	public void setNumberOfActiveSipConnections(Integer numberOfActiveSipConnections) {
		NumberOfActiveSipConnections = numberOfActiveSipConnections;
	}

	public Integer getNumberOfActiveSmppConnections() {
		return NumberOfActiveSmppConnections;
	}

	public void setNumberOfActiveSmppConnections(
			Integer numberOfActiveSmppConnections) {
		NumberOfActiveSmppConnections = numberOfActiveSmppConnections;
	}

	public Long getNumberOfBytesTransferred() {
		return NumberOfBytesTransferred;
	}

	public void setNumberOfBytesTransferred(Long numberOfBytesTransferred) {
		NumberOfBytesTransferred = numberOfBytesTransferred;
	}

	public Long getNumberOfHttpBytesToClient() {
		return NumberOfHttpBytesToClient;
	}

	public void setNumberOfHttpBytesToClient(Long numberOfHttpBytesToClient) {
		NumberOfHttpBytesToClient = numberOfHttpBytesToClient;
	}

	public Long getNumberOfHttpBytesToServer() {
		return NumberOfHttpBytesToServer;
	}

	public void setNumberOfHttpBytesToServer(Long numberOfHttpBytesToServer) {
		NumberOfHttpBytesToServer = numberOfHttpBytesToServer;
	}

	public Long getNumberOfHttpRequests() {
		return NumberOfHttpRequests;
	}

	public void setNumberOfHttpRequests(Long numberOfHttpRequests) {
		NumberOfHttpRequests = numberOfHttpRequests;
	}

	public Long getNumberOfRequestsProcessed() {
		return NumberOfRequestsProcessed;
	}

	public void setNumberOfRequestsProcessed(Long numberOfRequestsProcessed) {
		NumberOfRequestsProcessed = numberOfRequestsProcessed;
	}

	public Long getNumberOfResponsesProcessed() {
		return NumberOfResponsesProcessed;
	}

	public void setNumberOfResponsesProcessed(Long numberOfResponsesProcessed) {
		NumberOfResponsesProcessed = numberOfResponsesProcessed;
	}

	public Long getNumberOfSmppBytesToClient() {
		return NumberOfSmppBytesToClient;
	}

	public void setNumberOfSmppBytesToClient(Long numberOfSmppBytesToClient) {
		NumberOfSmppBytesToClient = numberOfSmppBytesToClient;
	}

	public Long getNumberOfSmppBytesToServer() {
		return NumberOfSmppBytesToServer;
	}

	public void setNumberOfSmppBytesToServer(Long numberOfSmppBytesToServer) {
		NumberOfSmppBytesToServer = numberOfSmppBytesToServer;
	}

	public Long getNumberOfSmppRequestsToClient() {
		return NumberOfSmppRequestsToClient;
	}

	public void setNumberOfSmppRequestsToClient(Long numberOfSmppRequestsToClient) {
		NumberOfSmppRequestsToClient = numberOfSmppRequestsToClient;
	}

	public Long getNumberOfSmppRequestsToServer() {
		return NumberOfSmppRequestsToServer;
	}

	public void setNumberOfSmppRequestsToServer(Long numberOfSmppRequestsToServer) {
		NumberOfSmppRequestsToServer = numberOfSmppRequestsToServer;
	}

	public String getReleaseName() {
		return releaseName;
	}

	public void setReleaseName(String releaseName) {
		this.releaseName = releaseName;
	}

	public String getReleaseVersion() {
		return releaseVersion;
	}

	public void setReleaseVersion(String releaseVersion) {
		this.releaseVersion = releaseVersion;
	}

	public Integer getActiveNodes() {
		return activeNodes;
	}

	public void setActiveNodes(Integer activeNodes) {
		this.activeNodes = activeNodes;
	}
	
}

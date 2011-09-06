package org.mobicents.tools.sip.balancer;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

public class InvocationContext {
	Logger logger = Logger.getLogger(InvocationContext.class.getCanonicalName());
	public DefaultBalancerAlgorithm balancerAlgorithm;
	public InvocationContext(String version, BalancerContext balancerContext) {
		this.version = version;
		try {
			Class clazz = Class.forName(balancerContext.algorithmClassName);
			balancerAlgorithm = (DefaultBalancerAlgorithm) clazz.newInstance();
			balancerAlgorithm.balancerContext = balancerContext;
			balancerAlgorithm.setProperties(balancerContext.properties);
			balancerAlgorithm.setInvocationContext(this);
			logger.info("Balancer algorithm " + balancerContext.algorithmClassName + " loaded succesfully");
			balancerAlgorithm.init();
		} catch (Exception e) {
			throw new RuntimeException("Error loading the algorithm class: " + balancerContext.algorithmClassName, e);
		}
	}
	public CopyOnWriteArrayList<SIPNode> nodes = new CopyOnWriteArrayList<SIPNode>();
	public String version;
	private ConcurrentHashMap<String, Object> attribs = new ConcurrentHashMap<String, Object>();
	public Object getAttribute(String name) {
		return attribs.get(name);
	}
	public void setAttribute(String name, Object val) {
		attribs.put(name, val);
	}
	public void removeAttribute(String name) {
		attribs.remove(name);
	}
}

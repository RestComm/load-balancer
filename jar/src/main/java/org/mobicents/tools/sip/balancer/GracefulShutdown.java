package org.mobicents.tools.sip.balancer;

import org.apache.log4j.Logger;
import org.mobicents.tools.http.balancer.HttpChannelAssociations;

public class GracefulShutdown extends Thread {
	private static Logger logger = Logger.getLogger(GracefulShutdown.class
			.getCanonicalName());
	
	BalancerRunner balancerRunner;
	public GracefulShutdown(BalancerRunner balancerRunner)
	{
		this.balancerRunner = balancerRunner;
	}
	@Override
	public void run() {
		logger.warn("GracefulShutdown was called, stopping the Load Balancer into " + balancerRunner.balancerContext.shutdownTimeout + " ms.");
    	HttpChannelAssociations.serverApiChannel.unbind();
    	HttpChannelAssociations.serverApiChannel.close();
    	HttpChannelAssociations.serverApiChannel.getCloseFuture().awaitUninterruptibly();
    	HttpChannelAssociations.serverApiBootstrap.shutdown();
    	try 
    	{
			sleep(balancerRunner.balancerContext.shutdownTimeout);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}								
		balancerRunner.stop();
    	System.exit(0);
	}
	

}

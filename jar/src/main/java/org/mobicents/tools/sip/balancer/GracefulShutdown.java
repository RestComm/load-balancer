package org.mobicents.tools.sip.balancer;

import org.mobicents.tools.http.balancer.HttpChannelAssociations;

public class GracefulShutdown extends Thread {

	BalancerRunner balancerRunner;
	public GracefulShutdown(BalancerRunner balancerRunner)
	{
		this.balancerRunner = balancerRunner;
	}
	@Override
	public void run() {
    	HttpChannelAssociations.serverStatisticChannel.unbind();
    	HttpChannelAssociations.serverStatisticChannel.close();
    	HttpChannelAssociations.serverStatisticChannel.getCloseFuture().awaitUninterruptibly();
    	HttpChannelAssociations.serverStatisticBootstrap.shutdown();
    	try 
    	{
			sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}								
		balancerRunner.stop();
    	System.exit(0);

		
	}
	

}

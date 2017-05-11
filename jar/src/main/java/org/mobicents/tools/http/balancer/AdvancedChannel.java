package org.mobicents.tools.http.balancer;

import org.jboss.netty.channel.Channel;

public class AdvancedChannel {
	
	private Channel channel;
	private boolean isCheckNeed = false;
	public AdvancedChannel (Channel channel)
	{
		this(channel, false);
	}
	
	public AdvancedChannel (Channel channel, boolean isCheckNeed)
	{
		this.channel = channel;
		this.isCheckNeed = isCheckNeed;
	}

	public Channel getChannel() {
		return channel;
	}

	public void setChannel(Channel channel) {
		this.channel = channel;
	}

	public boolean isCheckNeed() {
		return isCheckNeed;
	}

	public void setCheckNeed(boolean isCheckNeed) {
		this.isCheckNeed = isCheckNeed;
	}
	
	@Override
    public int hashCode() {
		return channel.hashCode();
	}
	
	@Override
	public boolean equals(Object obj) {
	    if (obj == null) 
	    	return false;
	    
	    if (obj instanceof AdvancedChannel&&((AdvancedChannel)obj).getChannel().equals(channel))
	    	return true;

	    return false;
	}
}

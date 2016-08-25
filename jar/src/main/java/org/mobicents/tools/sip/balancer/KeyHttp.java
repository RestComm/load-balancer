package org.mobicents.tools.sip.balancer;

public class KeyHttp {
	
	private Integer instanceId;
	
	public KeyHttp(Integer instanceId)
	{
		this.instanceId = instanceId;
	}
	
	
	public Integer getInstanceId() {
		return instanceId;
	}


	@Override
    public int hashCode()
    { 
		return instanceId.hashCode();
    }
	
	
	@Override
	public boolean equals(Object obj) 
	{
		if ((obj instanceof KeyHttp) && (this.instanceId.equals(((KeyHttp) obj).getInstanceId()))) 
			return true;
		else 
			return false;
	}

}

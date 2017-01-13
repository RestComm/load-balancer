package org.mobicents.tools.sip.balancer;

public class KeyHttp {
	
	private String instanceId;
	
	public KeyHttp(String instanceId)
	{
		this.instanceId = instanceId;
	}
	
	
	public String getInstanceId() {
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

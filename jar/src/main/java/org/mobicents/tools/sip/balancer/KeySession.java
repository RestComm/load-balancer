package org.mobicents.tools.sip.balancer;

public class KeySession {

	private String sessionId;
	
	public KeySession (String sessionId)
	{
		this.sessionId = sessionId;
	}
	
	public String getSessionId() {
		return sessionId;
	}
	
	@Override
    public int hashCode()
    { 
		return sessionId.hashCode();			
    }
	
	@Override
	public boolean equals(Object obj) 
	{
		if (obj instanceof KeySession) 
		{
			if(sessionId.equals(((KeySession) obj).getSessionId()))
			{
				return true;
			}
			else
			{
				return false;
			}
		} 
		else 
		{
			return false;
		}
	}
	
	public String toString() 
	{
		return "Session ID key: " + sessionId;
	}
	
}

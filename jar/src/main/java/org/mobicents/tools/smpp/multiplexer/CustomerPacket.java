package org.mobicents.tools.smpp.multiplexer;

public class CustomerPacket {
	
	private Long sessionId;
	private Integer sequence;
	
	public CustomerPacket(Long sessionId, Integer sequence)
	{
		this.sessionId = sessionId;
		this.sequence = sequence;
	}

	public Long getSessionId() {
		return sessionId;
	}

	public Integer getSequence() {
		return sequence;
	}

}

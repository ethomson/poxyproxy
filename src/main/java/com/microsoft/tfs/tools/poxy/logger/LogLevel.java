package com.microsoft.tfs.tools.poxy.logger;

public enum LogLevel
{
	NONE(0),
	FATAL(1),
	ERROR(2),
	WARNING(3),
	INFO(4),
	DEBUG(5),
	TRACE(6);
	
	private int value;
	
	private LogLevel(int value)
	{
		this.value = value;
	}
	
	int getValue()
	{
		return value;
	}
}

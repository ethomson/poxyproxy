package com.edwardthomson.poxyproxy.logger;

public abstract class Logger
{
	protected static volatile LogLevel level;

	public static void setLevel(LogLevel level)
	{
		Logger.level = level;
	}

	@SuppressWarnings("rawtypes")
	public static Logger getLogger(Class c)
	{
		return getLogger(c != null ? c.getName() : "(unknown)");
	}

	public static Logger getLogger(String name)
	{
		return new ConsoleLogger(name);
	}

	public abstract boolean isEnabled(LogLevel level);
	public abstract void write(LogLevel level, String message);
	public abstract void write(LogLevel level, String message, Throwable t);
}

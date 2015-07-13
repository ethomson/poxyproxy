package com.microsoft.tfs.tools.poxy.logger;

import java.text.SimpleDateFormat;
import java.util.Calendar;

public class ConsoleLogger extends Logger
{
	private final String name;
	private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	private static final Calendar calendar = Calendar.getInstance();

	ConsoleLogger(String name)
	{
		this.name = name != null ? name : "(unknown)";
	}
	
	public boolean isEnabled(LogLevel level)
	{
		if (level == null)
		{
			return false;
		}
		
		return (level.getValue() <= Logger.level.getValue());
	}
	
	public void write(LogLevel level, String message)
	{
		write(level, message, null);
	}
	
	public void write(LogLevel level, String message, Exception e)
	{
		if (isEnabled(level))
		{
			if (message == null && e == null)
			{
				return;
			}

			System.out.print("[");
			System.out.print(dateFormat.format(calendar.getTime()));
			System.out.print("] ");
			System.out.print(name);
			System.out.print(": ");

			if (message != null)
			{
				System.out.print(message);
			}
			if (message != null && e != null)
			{
				System.out.print(": ");
				System.out.print(e.getMessage());
			}
			System.out.print("\n");

			if (e != null)
			{
				e.printStackTrace();				
			}
		}
	}
}

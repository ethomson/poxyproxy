/*
 * Poxy: a simple HTTP proxy for testing.
 *
 * Copyright (c) Microsoft Corporation. All rights reserved.
 */

package com.microsoft.tfs.tools.poxy;

import java.util.List;

import com.microsoft.tfs.tools.poxy.logger.LogLevel;

public abstract class HeaderUtils
{
	/*
	 * These are never forwarded to the remote request
	 */
	public static final HeaderFilter NEVER_TRANSMIT_FILTER = new HeaderFilter(new String[]
			{
					"trailer", "upgrade"
			});

	/*
	 * Not sent when the connection is direct (no forward proxy) because this
	 * program wants control over its back end socket lifecycle.
	 */
	public static final HeaderFilter DISALLOW_FOR_DIRECT_REQUESTS = new HeaderFilter(new String[]
			{
					"proxy-connection", "proxy-authorization", "proxy-authenticate", "connection", "keep-alive"
			});

	public static boolean isChunked(List<Header> headers)
	{
		for (Header h : headers)
		{
			// HTTP 1.1 Section 4.4: any Transfer-Encoding other than "identity"
			// means chunked determines the length

			if (h.matchesName(Constants.TRANSFER_ENCODING_HEADER)
					&& !h.getValue().equalsIgnoreCase(Constants.TRANSFER_ENCODING_IDENTITY))
			{
				return true;
			}
		}

		return false;
	}

	public static boolean isConnectionClose(List<Header> headers)
	{
		for (Header h : headers)
		{
			if (h.matchesName(Constants.CONNECTION_HEADER) && h.getValue().equalsIgnoreCase(Constants.CONNECTION_CLOSE))
			{
				return true;
			}
		}

		return false;
	}

	public static boolean isConnectionKeepAlive(List<Header> headers)
	{
		for (Header h : headers)
		{
			if (h.matchesName(Constants.CONNECTION_HEADER) && h.getValue().equalsIgnoreCase(Constants.CONNECTION_KEEP_ALIVE))
			{
				return true;
			}
		}

		return false;
	}

	public static boolean isProxyConnectionClose(List<Header> headers)
	{
		for (Header h : headers)
		{
			if (h.matchesName(Constants.PROXY_CONNECTION_HEADER)
					&& h.getValue().equalsIgnoreCase(Constants.CONNECTION_CLOSE))
			{
				return true;
			}
		}

		return false;
	}

	public static long getContentLength(List<Header> headers)
	{
		for (Header h : headers)
		{
			if (h.matchesName(Constants.CONTENT_LENGTH_HEADER))
			{
				try
				{
					return Long.parseLong(h.getValue());
				}
				catch (NumberFormatException e)
				{
					Header.logger.write(LogLevel.WARNING, "Couldn't parse content length header: " + h);
				}
			}
		}

		return -1;
	}
}

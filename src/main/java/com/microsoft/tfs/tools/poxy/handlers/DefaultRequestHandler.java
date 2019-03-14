/*
 * Poxy: a simple HTTP proxy for testing.
 *
 * Copyright (c) Microsoft Corporation. All rights reserved.
 */

package com.microsoft.tfs.tools.poxy.handlers;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import com.microsoft.tfs.tools.poxy.Connection;
import com.microsoft.tfs.tools.poxy.Constants;
import com.microsoft.tfs.tools.poxy.HTTPException;
import com.microsoft.tfs.tools.poxy.Header;
import com.microsoft.tfs.tools.poxy.HeaderUtils;
import com.microsoft.tfs.tools.poxy.IOUtils;
import com.microsoft.tfs.tools.poxy.PersistentServerSocketMapKey;
import com.microsoft.tfs.tools.poxy.Request;
import com.microsoft.tfs.tools.poxy.Response;
import com.microsoft.tfs.tools.poxy.Status;
import com.microsoft.tfs.tools.poxy.UTF8Utils;
import com.microsoft.tfs.tools.poxy.Utils;
import com.microsoft.tfs.tools.poxy.logger.LogLevel;
import com.microsoft.tfs.tools.poxy.logger.Logger;

/**
 * Handles GET, POST, and HEAD requests.
 */
public class DefaultRequestHandler
extends RequestHandler
{
	private static final Logger logger = Logger.getLogger(DefaultRequestHandler.class);

	public DefaultRequestHandler(Connection connection)
	{
		super(connection);
	}

	@Override
	public boolean handle(Request request, Response response)
			throws IOException
	{
		// Parse the target URI

		final URI targetURI = parseURI(request, response);

		if (targetURI == null)
		{
			return false;
		}

		// Connect to forward proxy or directly

		final boolean useProxy =
				connection.getOptions().getForwardProxyURI() != null
				&& !connection.getOptions().hostMatchesForwardProxyBypassHosts(targetURI.getHost());

		final String host;
		final int port;
		if (useProxy)
		{
			host = connection.getOptions().getForwardProxyURI().getHost();
			port = connection.getOptions().getForwardProxyURI().getPort();
		}
		else
		{
			host = targetURI.getHost();
			port = targetURI.getPort();
		}

		final InetSocketAddress serverAddress = new InetSocketAddress(host, port > 0 ? port : 80);

		/*
		 * If this socket fails for this request, it will be forgotten (removed
		 * from the map of persistent sockets if it was in it). If the request
		 * finishes without error, it will be added to the map (if it was not
		 * already there).
		 */
		Socket proxyToServerSocket = null;

		final OutputStream serverOutput;
		final InputStream serverInput;
		try
		{
			proxyToServerSocket = connectOrGetExistingSocket(serverAddress);

			serverOutput = proxyToServerSocket.getOutputStream();
			serverInput = proxyToServerSocket.getInputStream();

			transferRequest(request, serverOutput, useProxy);
		}
		catch (SocketTimeoutException e)
		{
			// We can still send an error message because no response data
			// has been forwarded
			response.writeError(Status.GATEWAY_TIMEOUT, "Timed out connecting to " + request.getURI());
			forgetSocket(proxyToServerSocket);

			// We can return true to process more requests because we read the
			// entire request
			return true;
		}
		catch (HTTPException e)
		{
			// Conversation error talking to forward proxy
			response.writeError(Status.BAD_GATEWAY, e.getMessage());
			forgetSocket(proxyToServerSocket);

			// We can return true to process more requests because we read the
			// entire request
			return true;
		}
		catch (IOException e)
		{
			response.writeError(Status.BAD_GATEWAY, e);
			forgetSocket(proxyToServerSocket);

			// We can return true to process more requests because we read the
			// entire request
			return true;
		}

		/*
		 * At this point the request has been sent but nothing has been read
		 * from the socket.
		 */

		try
		{
			transferResponse(request, response, serverInput, serverAddress);
		}
		catch (SocketTimeoutException e)
		{
			/*
			 * This error is likely from reading from the proxy-to-server
			 * socket. We can't safely send error information to the client now.
			 */
			forgetSocket(proxyToServerSocket);

			// Prevent more requets from this client because we don't know what
			// we may have written to the client
			return false;
		}
		catch (IOException e)
		{
			/*
			 * This error is likely from writing to the response (maybe socket
			 * closed?).
			 */
			forgetSocket(proxyToServerSocket);

			// Prevent more requets from this client because we don't know what
			// we may have written to the client
			return false;
		}

		/*
		 * Request was successful and complete. Remember this socket for further
		 * requests if it's not already in the mamp.
		 */
		rememberSocket(proxyToServerSocket);

		return true;
	}

	private URI parseURI(Request request, Response response)
			throws IOException
	{
		final URI targetURI;

		try
		{
			// Use the multi-arg constructor so it accepts chars like pipes
			targetURI = new URI(null, null, request.getURI(), null);
		}
		catch (URISyntaxException e)
		{
			response.writeError(Status.BAD_REQUEST, e);
			return null;
		}

		if (targetURI.getHost() == null || targetURI.getHost().length() == 0)
		{
			response.writeError(Status.BAD_REQUEST, "This is a proxy server and it requires absolute resource URIs.");
			return null;
		}

		if (targetURI.getScheme() == null || !targetURI.getScheme().equalsIgnoreCase("http"))
		{
			response.writeError(Status.BAD_REQUEST, "Only HTTP supported for "
					+ request.getMethod()
					+ " requests.  Use CONNECT for HTTPS.");
			return null;
		}

		return targetURI;
	}

	private void forgetSocket(final Socket socket)
	{
		if (socket == null)
		{
			return;
		}

		connection.removePersistentProxyToServerSocket(getSocketMapKey(socket));
	}

	private void rememberSocket(Socket serverSocket)
	{
		connection.putPersistentProxyToServerSocket(getSocketMapKey(serverSocket), serverSocket);
	}

	private Socket connectOrGetExistingSocket(InetSocketAddress serverAddress)
			throws IOException
	{
		final Socket socket = connection.getPersistentProxyToServerSocket(getSocketMapKey(serverAddress));
		if (socket == null)
		{
			logger.write(LogLevel.DEBUG, "No previous proxy-to-server socket, connecting to " + serverAddress);
			return connect(serverAddress);
		}
		else
		{
			logger.write(LogLevel.DEBUG, "Found existing proxy-to-server socket " + socket);
		}

		return socket;
	}

	private PersistentServerSocketMapKey getSocketMapKey(InetSocketAddress serverAddress)
	{
		return new PersistentServerSocketMapKey(connection.getClientToProxySocket(), serverAddress);
	}

	private PersistentServerSocketMapKey getSocketMapKey(Socket serverSocket)
	{
		return new PersistentServerSocketMapKey(connection.getClientToProxySocket(), new InetSocketAddress(
				serverSocket.getInetAddress(),
				serverSocket.getPort()));
	}

	private void transferRequest(Request request, OutputStream serverOutput, boolean useProxy)
			throws IOException
	{
		serverOutput.write(UTF8Utils.encode(request.toString() + "\r\n"));

		List<Header> headers = request.getHeaders();

		headers = HeaderUtils.NEVER_TRANSMIT_FILTER.filter(headers);

		if (!useProxy)
		{
			headers = HeaderUtils.DISALLOW_FOR_DIRECT_REQUESTS.filter(headers);
		}

		for (Header h : headers)
		{
			serverOutput.write(UTF8Utils.encode(h.toString() + "\r\n"));
		}

		/*
		 * Normally we would add in some headers identifying this request as
		 * proxied, but that seems to cause IIS/ISA servers to refuse to keep
		 * sockets alive, which breaks NTLM. So we're just a sneaky proxy!
		 */

		serverOutput.write(UTF8Utils.encode("\r\n"));

		long length = 0;
		if (HeaderUtils.isChunked(headers))
		{
			logger.write(LogLevel.DEBUG, "Transferring chunked request content bytes");
			IOUtils.copyChunkedStream(request.getInputStream(), serverOutput);
		}
		else if ((length = HeaderUtils.getContentLength(headers)) > 0)
		{
			logger.write(LogLevel.DEBUG, "Transferring " + length + " request content bytes");
			IOUtils.copyStream(request.getInputStream(), serverOutput, length);
		}
		else
		{
			logger.write(LogLevel.DEBUG, "Request has no content");
		}

		serverOutput.flush();
	}

	private void transferResponse(Request request, Response response, InputStream serverInput, InetSocketAddress address)
			throws IOException
	{
		final String statusLine = IOUtils.readLine(serverInput);

		if (statusLine == null)
		{
			response.writeError(Status.BAD_GATEWAY, "Connection closed by " + address);
			return;
		}

		logger.write(LogLevel.DEBUG, "Forward proxy responds: " + statusLine);

		final String[] parts = statusLine.split(" ", 3);
		// Need at least 2; message is optional
		if (parts.length < 2)
		{
			response.writeError(Status.BAD_GATEWAY, "Couldn't parse HTTP status '" + statusLine + "' from " + address);
			return;
		}

		final String version = parts[0];
		final int statusCode = Integer.parseInt(parts[1]);
		final String message = parts[2];

		// The user may have enabled a sleep here
		final int delay = connection.getOptions().getResponseDelayMilliseconds();
		if (delay > 0)
		{
			try
			{
				Thread.sleep(delay);
			}
			catch (InterruptedException e)
			{
				// Just continue with the response
			}
		}

		/*
		 * After this point we can't call response.writeError() because it would
		 * mix with the status, headers, and content we're writing.
		 */

		response.writeStatus(statusCode, message, version);

		List<Header> headers = IOUtils.readHeaders(serverInput);
		headers = HeaderUtils.NEVER_TRANSMIT_FILTER.filter(headers);
		headers.add(new Header("Via", "1.0 " + Utils.getHostname()));

		// Just saves them for later inspection; doesn't write anything
		response.setHeaders(headers);

		response.writeHeaders(headers);

		response.endHeaders();

		/*
		 * Copy the response body if the method isn't HEAD.
		 */
		if (!request.getMethod().equalsIgnoreCase(Constants.HEAD_METHOD))
		{
			long length = 0;
			if (HeaderUtils.isChunked(headers))
			{
				logger.write(LogLevel.DEBUG, "Transferring chunked response content bytes");
				IOUtils.copyChunkedStream(serverInput, response.getStream());
			}
			else if ((length = HeaderUtils.getContentLength(headers)) > 0)
			{
				logger.write(LogLevel.DEBUG, "Transferring " + length + " response content bytes");
				IOUtils.copyStream(serverInput, response.getStream(), length);
			}
			else if (HeaderUtils.isConnectionClose(headers) || HeaderUtils.isProxyConnectionClose(headers))
			{
				logger.write(LogLevel.DEBUG, "Transferring response bytes until end of stream because of Connection: close or Proxy-Connection: close");
				IOUtils.copyStream(serverInput, response.getStream(), -1);
			}
			else
			{
				logger.write(LogLevel.DEBUG, "Response has no content");
			}
		}

		response.flush();
	}
}

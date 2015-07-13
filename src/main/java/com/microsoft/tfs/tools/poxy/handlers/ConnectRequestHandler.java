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
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.log4j.Logger;

import com.microsoft.tfs.tools.poxy.Connection;
import com.microsoft.tfs.tools.poxy.HTTPException;
import com.microsoft.tfs.tools.poxy.Header;
import com.microsoft.tfs.tools.poxy.HeaderUtils;
import com.microsoft.tfs.tools.poxy.IOUtils;
import com.microsoft.tfs.tools.poxy.Request;
import com.microsoft.tfs.tools.poxy.Response;
import com.microsoft.tfs.tools.poxy.Status;
import com.microsoft.tfs.tools.poxy.UTF8Utils;

public class ConnectRequestHandler
    extends RequestHandler
{
    private static final Logger logger = Logger.getLogger(ConnectRequestHandler.class);
    private static final AtomicLong threadCounter = new AtomicLong(0);

    public ConnectRequestHandler(Connection connection)
    {
        super(connection);
    }

    @Override
    public boolean handle(Request request, Response response)
        throws HTTPException,
            IOException
    {
        List<Header> headers = new ArrayList<Header>();

        final Socket clientToProxySocket;
        final Socket proxyToServerSocket;
        try
        {
            clientToProxySocket = connection.getClientToProxySocket();
            proxyToServerSocket = connect(request, response, headers);
        }
        catch (SocketTimeoutException e)
        {
            // We can improve the message for this one
            response.writeError(Status.GATEWAY_TIMEOUT, "Timed out connecting to " + request.getURI());
            return false;
        }
        catch (HTTPException e)
        {
            // Conversation error talking to forward proxy
            response.writeError(Status.BAD_GATEWAY, e.getMessage());
            return false;
        }
        catch (IOException e)
        {
            response.writeError(Status.BAD_GATEWAY, e);
            return false;
        }

        response.writeStatus(Status.OK);

        headers = HeaderUtils.NEVER_TRANSMIT_FILTER.filter(headers);
        response.writeHeaders(headers);
        response.endHeaders();
        response.flush();

        final long threadID = threadCounter.incrementAndGet();

        // Start two IO threads using the executor service

        Future<?> clientToProxyFuture =
            connection.getExecutorService().submit(
                new IORunner("ClientToProxy-" + threadID, clientToProxySocket, proxyToServerSocket));

        Future<?> proxyToServerFuture =
            connection.getExecutorService().submit(
                new IORunner("ProxyToServer-" + threadID, proxyToServerSocket, clientToProxySocket));

        getFutureResult(clientToProxyFuture);
        getFutureResult(proxyToServerFuture);

        return true;
    }

    /**
     * Gets the future result and logs any errors.
     */
    private Object getFutureResult(final Future<?> future)
    {
        try
        {
            return future.get();
        }
        catch (InterruptedException e)
        {
            logger.warn("Interrupted waiting on IO thread", e);
        }
        catch (ExecutionException e)
        {
            logger.warn("Execution exception in IO thread", e);
        }

        return null;
    }

    /**
     * Connects to the target URI in the request, possibly forwarding via
     * another proxy.
     */
    private Socket connect(Request request, Response response, List<Header> headers)
        throws HTTPException,
            IOException
    {
        final InetSocketAddress targetAddress = parseTargetAddress(request);

        // See if we need to forward to another proxy
        if (connection.getOptions().getForwardProxyURI() != null
            && !connection.getOptions().hostMatchesForwardProxyBypassHosts(targetAddress.getHostName()))
        {
            return connectViaProxy(connection.getOptions().getForwardProxyURI(), targetAddress, headers);
        }

        // Direct connection
        return connect(targetAddress);
    }

    /**
     * Connects to the given address via the given HTTP proxy. Collects headers
     * read from the proxy in the headers list.
     */
    private Socket connectViaProxy(URI forwardProxyURI, InetSocketAddress address, List<Header> headers)
        throws IOException
    {
        // Is 80 a good default port here? Proxies configured for CONNECT can be
        // anywhere.

        final Socket proxyToServer =
            connect(new InetSocketAddress(forwardProxyURI.getHost(), forwardProxyURI.getPort() > 0
                ? forwardProxyURI.getPort() : 80));

        final OutputStream proxyToServerOutput = proxyToServer.getOutputStream();
        final InputStream proxyToServerInput = proxyToServer.getInputStream();

        proxyToServerOutput.write(UTF8Utils.encode(MessageFormat.format(
            "CONNECT {0}:{1} HTTP/1.0\r\n\r\n",
            address.getHostName(),
            Integer.toString(address.getPort()))));

        proxyToServerOutput.flush();

        final String statusLine = IOUtils.readLine(proxyToServerInput);

        if (statusLine == null)
        {
            throw new HTTPException("Connection closed by " + address);
        }

        logger.debug("Forward proxy responds: " + statusLine);

        final String[] parts = statusLine.split(" ", 3);
        // Need at least 2; message is optional
        if (parts.length < 2)
        {
            throw new HTTPException("Couldn't parse response line '" + statusLine + "'");
        }

        // First part is version; ignore

        // Second part is status code
        int statusCode = Integer.parseInt(parts[1]);

        if (statusCode != Status.OK)
        {
            proxyToServer.close();

            throw new HTTPException(MessageFormat.format(
                "Could not connect to {0} via proxy {1}: {2} {3}",
                address,
                forwardProxyURI,
                Integer.toString(statusCode),
                parts[2] != null ? parts[2] : ""));
        }

        // Read all headers
        headers.addAll(IOUtils.readHeaders(proxyToServerInput));

        // Socket is connected and positioned at the content (if there is any)
        return proxyToServer;
    }

    private InetSocketAddress parseTargetAddress(final Request request)
        throws HTTPException
    {
        // The connect URI will just be host:port for CONNECTs
        final String hostAndPort = request.getURI();

        String host = hostAndPort;
        int port = 80;

        int colonIndex = host.indexOf(':');
        if (colonIndex > 0)
        {
            host = hostAndPort.substring(0, colonIndex);

            try
            {
                port = Integer.parseInt(hostAndPort.substring(colonIndex + 1));
            }
            catch (NumberFormatException e)
            {
                throw new HTTPException("Could not parse port from " + hostAndPort, e);
            }
        }

        return new InetSocketAddress(host, port);
    }

    private static class IORunner
        implements Runnable
    {
        private final byte[] buffer = new byte[64 * 1024];

        private final String name;
        private final Socket inputSocket;
        private final Socket outputSocket;

        public IORunner(final String name, final Socket inputSocket, final Socket outputSocket)
        {
            this.name = name;
            this.inputSocket = inputSocket;
            this.outputSocket = outputSocket;
        }

        public void run()
        {
            String oldName = Thread.currentThread().getName();
            Thread.currentThread().setName(name);
            try
            {
                /*
                 * Read from input and write to output (there's another IO
                 * thread doing the same with the input and output sockets
                 * reversed).
                 * 
                 * Half-closed sockets aren't supported, so there's a finally
                 * block that ensures the other socket gets closed (which the
                 * other IO thread will notice very quickly).
                 */

                try
                {
                    final InputStream input = inputSocket.getInputStream();
                    final OutputStream output = outputSocket.getOutputStream();

                    while (true)
                    {
                        final int read = input.read(buffer);

                        // Peer closed input socket; stop
                        if (read == -1)
                        {
                            break;
                        }

                        // Other side closed, so can't write; stop
                        if (outputSocket.isClosed())
                        {
                            break;
                        }

                        // This is a thread's main run() method; stop
                        if (Thread.currentThread().isInterrupted())
                        {
                            break;
                        }

                        output.write(buffer, 0, read);
                    }
                }
                catch (SocketTimeoutException e)
                {
                    // Normal read timeout
                }
                catch (SocketException e)
                {
                    /*
                     * If we're blocked on read() when the other IO thread
                     * closes our input socket, we'll get this exception. We can
                     * surely get other kinds of socket errors this way.
                     * 
                     * Log at a low level because this is common.
                     */
                    logger.trace("SocketException", e);
                }
                catch (IOException e)
                {
                    // Unlikely to get a non-SocketException
                    logger.info("Non socket exception doing socket IO", e);
                }
                finally
                {
                    /*
                     * We don't support half-open sockets, so always close both
                     * sides.
                     */

                    IOUtils.close(inputSocket);
                    IOUtils.close(outputSocket);
                }
            }
            finally
            {
                Thread.currentThread().setName(oldName);
            }
        }
    }
}

/*
 * Poxy: a simple HTTP proxy for testing.
 * 
 * Copyright (c) Microsoft Corporation. All rights reserved.
 */

package com.microsoft.tfs.tools.poxy;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import com.microsoft.tfs.tools.poxy.handlers.ConnectRequestHandler;
import com.microsoft.tfs.tools.poxy.handlers.DefaultRequestHandler;
import com.microsoft.tfs.tools.poxy.handlers.RequestHandler;
import com.microsoft.tfs.tools.poxy.logger.LogLevel;
import com.microsoft.tfs.tools.poxy.logger.Logger;

/**
 * A connection corresponds to one client-to-proxy TCP socket, which is
 * long-lived (handles multiple HTTP requests) when HTTP keep-alive is enabled,
 * short lived otherwise.
 * <p>
 * This is a {@link Runnable} and is always run in its own thread. It runs until
 * the client-to-proxy socket closes, or until the proxy-to-server socket
 * closes, or until some fatal error causes both sides to close.
 */
public class Connection
    implements Runnable
{
    private final static Logger logger = Logger.getLogger(Connection.class);

    private final Socket clientToProxySocket;
    private final Options options;
    private final ExecutorService executorService;

    /**
     * Maps a pair of addresses (client-to-proxy, proxy-to-server) to the
     * proxyToServer {@link Socket} that was previously established for
     * communicating with that server.
     */
    private Map<PersistentServerSocketMapKey, Socket> persistentProxyToServerSockets =
        new HashMap<PersistentServerSocketMapKey, Socket>();

    public Connection(final Socket socket, final Options options, final ExecutorService executorService)
    {
        this.clientToProxySocket = socket;
        this.options = options;
        this.executorService = executorService;
    }

    public Options getOptions()
    {
        return options;
    }

    public ExecutorService getExecutorService()
    {
        return executorService;
    }

    public Socket getClientToProxySocket()
    {
        return clientToProxySocket;
    }

    public void putPersistentProxyToServerSocket(PersistentServerSocketMapKey key, Socket proxyToServerSocket)
    {
        persistentProxyToServerSockets.put(key, proxyToServerSocket);
    }

    public void removePersistentProxyToServerSocket(PersistentServerSocketMapKey key)
    {
        persistentProxyToServerSockets.remove(key);
    }

    /**
     * @return the proxy-to-server {@link Socket} that was previously
     *         established for the key, or <code>null</code> if there is none
     */
    public Socket getPersistentProxyToServerSocket(PersistentServerSocketMapKey key)
    {
        return persistentProxyToServerSockets.get(key);
    }

    public void run()
    {
        long requestCount = 0;
        boolean connectionHeaderRead = false;
        boolean keepAlive = true;

        String oldName = Thread.currentThread().getName();
        Thread.currentThread().setName("Connection-" + clientToProxySocket.getRemoteSocketAddress());
        try
        {
            initializeClientToProxySocket();

            final InputStream in = clientToProxySocket.getInputStream();
            final OutputStream out = clientToProxySocket.getOutputStream();

            while (keepAlive)
            {
                // Allocate a response with a default version so we can respond
                // to request protocol errors

                final Response response = new Response(out, Constants.VERSION_10);

                // Read the request

                final Request request = new Request(in);
                try
                {
                    if (!request.read())
                    {
                        /*
                         * Socket closed before reading any part of request,
                         * which is a valid way to close a kept-alive connection
                         * that has already done at least one request, but is
                         * invalid otherwise.
                         */
                        if (!keepAlive || requestCount == 0)
                        {
                            logger.write(LogLevel.WARNING,
                                "Connection closed before request could be read on socket "
                                + clientToProxySocket);
                        }
                        break;
                    }

                    requestCount++;

                    final Header connectionHeader = findHeader(Constants.CONNECTION_HEADER, request.getHeaders());
                    if (connectionHeader != null)
                    {
                        keepAlive = connectionHeader.getValue().equalsIgnoreCase(Constants.CONNECTION_KEEP_ALIVE);
                        connectionHeaderRead = true;
                    }
                    else if (!connectionHeaderRead)
                    {
                        keepAlive = request.getVersion().equals(Constants.VERSION_11);
                    }
                }
                catch (HTTPException e)
                {
                    // Protocol error or similar
                    response.writeError(Status.BAD_REQUEST, e);
                    break;
                }
                catch (SocketException e)
                {
                    // Socket problem so don't try to write an error response
                    logger.write(LogLevel.DEBUG, "SocketException", e);
                    break;
                }
                catch (IOException e)
                {
                    // A non-protocol error, but still don't try to write
                    // an error response
                    logger.write(LogLevel.DEBUG, "Non protocol exception doing socket IO", e);
                    break;
                }

                // Upgrade the response to use the version the client gave us
                response.setVersion(request.getVersion());
                
                if (options.isAuthenticationRequired() &&
                	!handleAuthentication(request, response))
                {
                	break;
               	}

                final RequestHandler handler;
                if (request.getMethod().equals(Constants.CONNECT_METHOD))
                {
                    handler = new ConnectRequestHandler(this);
                }
                else if (request.getMethod().equals(Constants.GET_METHOD)
                    || request.getMethod().equals(Constants.POST_METHOD)
                    || request.getMethod().equals(Constants.HEAD_METHOD))
                {
                    handler = new DefaultRequestHandler(this);
                }
                else
                {
                    response.writeError(
                        Status.BAD_REQUEST,
                        "This proxy server does not support the " + request.getMethod() + " method");
                    break;
                }

                /*
                 * After here we can't write an error response because some
                 * bytes may have already been sent by the handler. Handlers
                 * mostly handle their own errors.
                 */

                if (!handler.handle(request, response))
                {
                    /*
                     * The handler was unsuccessful and we should close this
                     * connection.
                     */
                    logger.write(LogLevel.DEBUG, "Handler " + handler + " was unsuccessful, closing connection");

                    // Best effort flush
                    try
                    {
                        response.flush();
                    }
                    catch (IOException e)
                    {
                        // Ignore
                    }

                    break;
                }

                // Ensure everything was written
                response.flush();

                /*
                 * Make sure we wrote the same number of bytes the header
                 * declared. If we wrote too few the client will may wait a long
                 * time to get more; if we wrote too many the client may close
                 * the connection on us.
                 * 
                 * Skip this for a HEAD request, because we write no body in
                 * that case.
                 */
                if (!request.getMethod().equalsIgnoreCase(Constants.HEAD_METHOD)
                    && response.getContentLengthHeaderValue() != -1
                    && response.getContentLengthHeaderValue() != response.getActualResponseBodyLength())
                {
                    logger.write(LogLevel.WARNING, MessageFormat.format(
                        "Header Content-Length {0} != {1} actually written bytes",
                        response.getContentLengthHeaderValue(),
                        response.getActualResponseBodyLength()));

                    break;
                }

                if (HeaderUtils.isConnectionClose(response.getHeaders())
                    || HeaderUtils.isProxyConnectionClose(response.getHeaders()))
                {
                    keepAlive = false;
                }
            }
        }
        catch (SocketTimeoutException e)
        {
            logger.write(LogLevel.DEBUG, "Read timeout on " + clientToProxySocket);
        }
        catch (IOException e)
        {
            logger.write(LogLevel.DEBUG, "IOException on socket " + clientToProxySocket, e);
        }
        catch (Exception e)
        {
            logger.write(LogLevel.WARNING, "Unhandled exception on socket " + clientToProxySocket, e);
        }
        finally
        {
            IOUtils.close(clientToProxySocket);
            Thread.currentThread().setName(oldName);
        }
    }
    
    private boolean handleAuthentication(Request request, Response response)
    	throws IOException
    {
        final Header authentication = findHeader(Constants.PROXY_AUTHORIZATION_HEADER, request.getHeaders());

        if (authentication != null &&
        	authentication.getValue().startsWith("Basic "))
        {
        	String value = new String(
        		Base64Utils.decode(authentication.getValue().substring(6)),
        		Charset.forName("UTF-8"));
        	String[] credentials = value.split(":", 2);
        	
        	if (options.credentialsMatchProxyCredentials(credentials[0], credentials[1]))
        	{
            	return true;        		
        	}        	
        }

        response.writeStatus(Status.PROXY_AUTHENTICATION_REQUIRED, "Proxy Authentication Required");
        response.writeHeader(new Header(Constants.PROXY_AUTHENTICATE_HEADER, "Basic realm=\"Proxy\""));
        response.writeHeader(new Header(Constants.CONNECTION_HEADER, Constants.CONNECTION_CLOSE));
        response.writeHeader(new Header(Constants.CONTENT_TYPE_HEADER, Constants.CONTENT_TYPE_TEXT_HTML));
        response.endHeaders();
        response.writeLine("<html><head><title>Proxy Authentication Required</title></head><body><p>Proxy Authentication Required</p></body></html>");
        response.flush();
        return false;
    }

    private void initializeClientToProxySocket()
        throws SocketException
    {
        clientToProxySocket.setTcpNoDelay(true);
        clientToProxySocket.setSoTimeout(options.getSocketReadTimeoutSeconds() * 1000);
    }

    private Header findHeader(final String name, final List<Header> list)
    {
        for (Header h : list)
        {
            if (name.equals(h.getName()))
            {
                return h;
            }
        }

        return null;
    }

    @Override
    public String toString()
    {
        return clientToProxySocket.toString();
    }
}

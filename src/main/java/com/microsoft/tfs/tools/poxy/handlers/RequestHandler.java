/*
 * Poxy: a simple HTTP proxy for testing.
 * 
 * Copyright (c) Microsoft Corporation. All rights reserved.
 */

package com.microsoft.tfs.tools.poxy.handlers;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import com.microsoft.tfs.tools.poxy.Connection;
import com.microsoft.tfs.tools.poxy.Request;
import com.microsoft.tfs.tools.poxy.Response;

public abstract class RequestHandler
{
    protected final Connection connection;

    public RequestHandler(Connection connection)
    {
        this.connection = connection;
    }

    /**
     * @return <code>true</code> if the request was serviced and another request
     *         can be processed on this connection, <code>false</code> if the
     *         request was unsuccessful in a way that should prevent further
     *         requests from being serviced on this connection
     */
    public abstract boolean handle(Request request, Response response)
        throws IOException;

    /**
     * Connects directly to the given address using the connect timeout
     * specified in the {@link #connection}'s options.
     * <p>
     * NoDelay is enabled on the socket.
     */
    protected Socket connect(InetSocketAddress address)
        throws IOException
    {
        final Socket socket = new Socket();

        socket.setTcpNoDelay(true);
        socket.connect(address, connection.getOptions().getConnectTimeoutSeconds() * 1000);

        socket.setSoTimeout(connection.getOptions().getSocketReadTimeoutSeconds() * 1000);

        return socket;
    }

    @Override
    public String toString()
    {
        return getClass().getName() + " [" + connection + "]";
    }
}

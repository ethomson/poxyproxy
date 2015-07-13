/*
 * Poxy: a simple HTTP proxy for testing.
 * 
 * Copyright (c) Microsoft Corporation. All rights reserved.
 */

package com.microsoft.tfs.tools.poxy;

import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * A key for the {@link Connection}'s map of these keys to {@link Socket}s that
 * were previously used to contact the desired server address.
 * 
 * @author sterwill
 * @threadsafety unknown
 */
public class PersistentServerSocketMapKey
{
    private final Socket clientSocket;
    private final InetSocketAddress serverAddress;

    public PersistentServerSocketMapKey(final Socket clientSocket, final InetSocketAddress serverAddress)
    {
        this.clientSocket = clientSocket;
        this.serverAddress = serverAddress;
    }

    public Socket getClientSocket()
    {
        return clientSocket;
    }

    public InetSocketAddress getServerAddress()
    {
        return serverAddress;
    }

    @Override
    public int hashCode()
    {
        int result = 17;

        result = result * 37 + clientSocket.hashCode();
        result = result * 37 + serverAddress.hashCode();

        return result;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj == this)
        {
            return true;
        }
        if (obj instanceof PersistentServerSocketMapKey == false)
        {
            return false;
        }

        return ((PersistentServerSocketMapKey) obj).clientSocket.equals(clientSocket)
            && ((PersistentServerSocketMapKey) obj).serverAddress.equals(serverAddress);
    }
}
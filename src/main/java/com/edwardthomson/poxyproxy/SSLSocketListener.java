package com.edwardthomson.poxyproxy;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;

public class SSLSocketListener extends SocketListener
{
    private final SSLContext sslContext;

    public SSLSocketListener(ServerSocket serverSocket, ExecutorService executorService, Options options,
            SSLContext sslContext)
    {
        super(serverSocket, executorService, options);

        this.sslContext = sslContext;
    }

    @Override
    protected Socket accept() throws Exception
    {
        Socket rawSocket = getServerSocket().accept();

        final SSLSocket sslSocket = (SSLSocket) sslContext.getSocketFactory().createSocket(rawSocket, null,
                rawSocket.getPort(), false);
        sslSocket.setUseClientMode(false);

        return sslSocket;
    }
}

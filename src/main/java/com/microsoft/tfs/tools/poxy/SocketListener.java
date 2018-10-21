package com.microsoft.tfs.tools.poxy;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;

import com.microsoft.tfs.tools.poxy.logger.LogLevel;
import com.microsoft.tfs.tools.poxy.logger.Logger;

public class SocketListener implements Runnable
{
    private final Logger logger = Logger.getLogger(SocketListener.class);

    private final ServerSocket serverSocket;
    private final ExecutorService executorService;
    private final Options options;

    public SocketListener(ServerSocket serverSocket, ExecutorService executorService, Options options)
    {
        this.serverSocket = serverSocket;
        this.executorService = executorService;
        this.options = options;
    }

    protected ServerSocket getServerSocket()
    {
        return serverSocket;
    }

    protected Options getOptions()
    {
        return options;
    }

    protected Socket accept() throws Exception
    {
        return serverSocket.accept();
    }

    public final void run()
    {
        while (true)
        {
            Socket client;

            try
            {
                client = accept();
            }
            catch (Exception e)
            {
                logger.write(LogLevel.FATAL, "Could not accept client socket", e);
                continue;
            }

            executorService.submit(new Connection(client, options, executorService));
        }
    }
}

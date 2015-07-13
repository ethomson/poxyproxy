/*
 * Poxy: a simple HTTP proxy for testing.
 * 
 * Copyright (c) Microsoft Corporation. All rights reserved.
 */

package com.microsoft.tfs.tools.poxy;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.microsoft.tfs.tools.poxy.GetOptions.Option;
import com.microsoft.tfs.tools.poxy.GetOptions.OptionException;
import com.microsoft.tfs.tools.poxy.logger.LogLevel;
import com.microsoft.tfs.tools.poxy.logger.Logger;

public class Poxy
{
    private final Logger logger = Logger.getLogger(Poxy.class);

    private ExecutorService executorService;

    public static void main(String[] args)
    {
        new Poxy(args).run();
    }

    private final String[] args;

    public Poxy(final String[] args)
    {
        this.args = args;
    }

    private static void usage()
    {
        System.err.println("Usage: Poxy [-d|--debug] [-p|--port port] [--max-threads num]");
        System.err.println("         [--connect-timeout secs] [--socket-read-timeout secs]");
        System.err.println("         [--forward-proxy url] [--forward-proxy-bypass host1,]...");
        System.err.println("         [--default-domain domain] [--add-response-delay ms]");
        System.err.println("         [--credentials username:password,]...");
    }

    public void run()
    {
        final Options options = getOptionsAndConfigureLogging();
        if (options == null)
        {
            System.exit(1);
        }

        executorService = Executors.newFixedThreadPool(options.getMaxThreads());

        try
        {
            @SuppressWarnings("resource")
            final ServerSocket serverSocket = new ServerSocket(options.getLocalPort(), 4096);

            while (true)
            {
                final Socket connectionSocket = serverSocket.accept();
                executorService.submit(new Connection(connectionSocket, options, executorService));
            }
        }
        catch (IOException e)
        {
            logger.write(LogLevel.FATAL, "Could not start server", e);
            System.exit(1);
        }
    }

    /**
     * Parses options and configures the logging (with debug enabled if that
     * option was set).
     * 
     * @return the options or <code>null</code> if an error happened
     */
    private Options getOptionsAndConfigureLogging()
    {
        // Configure our logger at the INFO level
        Logger.setLevel(LogLevel.INFO);

        /* Setup command-line options (with defaults) */
        final Option[] availableOptions =
            new Option[]
            {
                /* Bind on port 8000 */
                new Option("port", 'p', true, "8000"),

                /* Allow debugging */
                new Option("debug", 'd'),

                /* IO */
                new Option("max-threads", true),
                new Option("connect-timeout", true),
                new Option("socket-read-timeout", true),

                /* Proxy chaining */
                new Option("forward-proxy", true),
                new Option("forward-proxy-bypass", true, true),
                new Option("default-domain", true),
                
                /* Authentication */
                new Option("credentials", true, true),

                /* Debugging aids */
                new Option("add-response-delay", true, "0"),
            };

        final GetOptions getOptions = new GetOptions(availableOptions);

        /* Try to parse command line */
        try
        {
            getOptions.parse(args);
        }
        catch (OptionException e)
        {
            System.err.println(e.getMessage());
            usage();
            return null;
        }

        if (getOptions.getFreeArguments().size() > 0)
        {
            usage();
            return null;
        }

        Options proxyOptions = new Options();

        // Debug
        if (getOptions.getArguments().get("debug") != null)
        {
            Logger.setLevel(LogLevel.DEBUG);
            logger.write(LogLevel.DEBUG, "Log level set to " + LogLevel.DEBUG);
        }

        // Integer options
        try
        {
            if (getOptions.getArgument("port") != null)
            {
                proxyOptions.setLocalPort(Integer.parseInt(getOptions.getArgument("port")));
            }

            if (getOptions.getArgument("max-threads") != null)
            {
                proxyOptions.setMaxThreads(Integer.parseInt(getOptions.getArgument("max-threads")));
            }

            if (getOptions.getArgument("connect-timeout") != null)
            {
                proxyOptions.setConnectTimeoutSeconds(Integer.parseInt(getOptions.getArgument("connect-timeout")));
            }

            if (getOptions.getArgument("socket-read-timeout") != null)
            {
                proxyOptions.setSocketReadTimeoutSeconds(Integer.parseInt(getOptions.getArgument("socket-read-timeout")));
            }

            if (getOptions.getArgument("add-response-delay") != null)
            {
                proxyOptions.setResponseDelayMilliseconds(Integer.parseInt(getOptions.getArgument("add-response-delay")));
            }
        }
        catch (NumberFormatException e)
        {
            System.err.println("Number expected " + e.getMessage());
            usage();
            return null;
        }

        // Forwarding options
        if (getOptions.getArgument("forward-proxy") != null)
        {
            proxyOptions.setForwardProxyURI(getOptions.getArgument("forward-proxy"));
            proxyOptions.setForwardProxyBypassHosts(getOptions.getArguments("forward-proxy-bypass"));
            proxyOptions.setForwardProxyBypassHostDefaultDomain(getOptions.getArgument("default-domain"));
        }
        
        if (getOptions.getArgument("credentials") != null)
        {
            proxyOptions.setAuthenticationRequired(true);
            proxyOptions.setProxyCredentials(getOptions.getArguments("credentials"));
        }

        logger.write(LogLevel.INFO, "Starting server on port " + Integer.toString(proxyOptions.getLocalPort()));

        return proxyOptions;
    }
}

/*
 * Poxy: a simple HTTP proxy for testing.
 *
 * Copyright (c) Microsoft Corporation. All rights reserved.
 */

package com.microsoft.tfs.tools.poxy;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

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
        System.err.println("Usage: Poxy [-d|--debug] [-a|--address address] [-p|--port port]");
        System.err.println("         [-s|--ssl-port port] [--ssl-keystore file] [--ssl-keystore-password pass]");
        System.err.println("         [--max-threads num] [--connect-timeout secs]");
        System.err.println("         [--socket-read-timeout secs] [--forward-proxy url]");
        System.err.println("         [--forward-proxy-bypass host1,...] [--default-domain domain]");
        System.err.println("         [--add-response-delay ms] [--credentials username:password,...]");
    }

    public void run()
    {
        final ArrayList<Thread> listenerThreads = new ArrayList<Thread>();
        final Options options = getOptionsAndConfigureLogging();

        if (options == null)
        {
            System.exit(1);
        }

        executorService = Executors.newFixedThreadPool(options.getMaxThreads());

        try
        {
            final ServerSocket httpSocket = new ServerSocket(options.getLocalPort(), 4096,
                    InetAddress.getByName(options.getLocalAddress()));
            listenerThreads.add(new Thread(new SocketListener(httpSocket, executorService, options)));

            if (options.getLocalSSLPort() != 0)
            {
                final SSLContext sslContext = configureSSL(options);
                final ServerSocket httpsSocket = new ServerSocket(options.getLocalSSLPort(), 4096,
                        InetAddress.getByName(options.getLocalAddress()));
                listenerThreads
                        .add(new Thread(new SSLSocketListener(httpsSocket, executorService, options, sslContext)));
            }
        }
        catch (GeneralSecurityException e)
        {
            logger.write(LogLevel.FATAL, "Could not configure SSL", e);
            System.exit(1);
        }
        catch (IOException e)
        {
            logger.write(LogLevel.FATAL, "Could not start server", e);
            System.exit(1);
        }

        for (Thread t : listenerThreads)
        {
            t.start();
        }

        try
        {
            for (Thread t : listenerThreads)
            {
                t.join();
            }
        }
        catch (InterruptedException e)
        {
            logger.write(LogLevel.FATAL, "Failed to listen to server sockets");
            System.exit(1);
        }
    }

    private SSLContext configureSSL(Options options) throws GeneralSecurityException
    {
        final SSLContext sslContext = SSLContext.getInstance("TLSv1.2");

        if (options.getSSLKeystoreFile() != null)
        {
            try
            {
                final KeyManagerFactory keyManagerFactory = KeyManagerFactory
                        .getInstance(KeyManagerFactory.getDefaultAlgorithm());
                final KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());

                keyStore.load(new FileInputStream(options.getSSLKeystoreFile()),
                        options.getSSLKeystorePassword().toCharArray());

                keyManagerFactory.init(keyStore, options.getSSLKeystorePassword().toCharArray());

                sslContext.init(keyManagerFactory.getKeyManagers(), null, null);
            }
            catch (IOException e)
            {
                throw new GeneralSecurityException("Could not open keystore file", e);
            }
        }

        return sslContext;
    }

    /**
     * Parses options and configures the logging (with debug enabled if that option
     * was set).
     *
     * @return the options or <code>null</code> if an error happened
     */
    private Options getOptionsAndConfigureLogging()
    {
        // Configure our logger at the INFO level
        Logger.setLevel(LogLevel.INFO);

        /* Setup command-line options (with defaults) */
        final Option[] availableOptions = new Option[] {
                /* Bind on port 8000 */
                new Option("address", 'a', true, "0.0.0.0"), new Option("port", 'p', true, "8000"),

                /* SSL configuration */
                new Option("ssl-port", 's', true), new Option("ssl-keystore", true),
                new Option("ssl-keystore-password", true),

                /* Allow debugging */
                new Option("debug", 'd'),

                /* IO */
                new Option("max-threads", true), new Option("connect-timeout", true),
                new Option("socket-read-timeout", true),

                /* Proxy chaining */
                new Option("forward-proxy", true), new Option("forward-proxy-bypass", true, true),
                new Option("default-domain", true),

                /* Authentication */
                new Option("credentials", true, true),

                /* Debugging aids */
                new Option("add-response-delay", true, "0"), };

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
            if (getOptions.getArgument("address") != null)
            {
                proxyOptions.setLocalAddress(getOptions.getArgument("address"));
            }

            if (getOptions.getArgument("port") != null)
            {
                proxyOptions.setLocalPort(Integer.parseInt(getOptions.getArgument("port")));
            }

            if (getOptions.getArgument("ssl-port") != null)
            {
                proxyOptions.setLocalSSLPort(Integer.parseInt(getOptions.getArgument("ssl-port")));
            }

            if (getOptions.getArgument("ssl-keystore") != null)
            {
                proxyOptions.setSSLKeystoreFile(getOptions.getArgument("ssl-keystore"));
            }

            if (getOptions.getArgument("ssl-keystore-password") != null)
            {
                proxyOptions.setSSLKeystorePassword(getOptions.getArgument("ssl-keystore-password"));
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
                proxyOptions
                        .setSocketReadTimeoutSeconds(Integer.parseInt(getOptions.getArgument("socket-read-timeout")));
            }

            if (getOptions.getArgument("add-response-delay") != null)
            {
                proxyOptions
                        .setResponseDelayMilliseconds(Integer.parseInt(getOptions.getArgument("add-response-delay")));
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

        logger.write(LogLevel.INFO, "Starting server on " + proxyOptions.getLocalAddress() + ":"
                + Integer.toString(proxyOptions.getLocalPort()));

        if (proxyOptions.getLocalSSLPort() != 0)
        {
            logger.write(LogLevel.INFO, "Starting TLS server on " + proxyOptions.getLocalAddress() + ":"
                    + Integer.toString(proxyOptions.getLocalSSLPort()));
        }

        return proxyOptions;
    }
}

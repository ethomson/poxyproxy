/*
 * Poxy: a simple HTTP proxy for testing.
 * 
 * Copyright (c) Microsoft Corporation. All rights reserved.
 */

package com.microsoft.tfs.tools.poxy;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.MessageFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Options
{
    /**
     * Local TCP port to bind to.
     */
    private volatile int localPort = 8000;

    /**
     * If a connection to a server or forward proxy takes longer than this many
     * seconds, it errors with 504 Gateway Timeout.
     */
    private volatile int connectTimeoutSeconds = 10;

    /**
     * Set on client-to-proxy and proxy-to-server sockets.
     * <p>
     * If the client-to-proxy times out, the server socket is just closed.
     * <p>
     * If the proxy-to-server socket times out <em>before</em> any data has been
     * sent back to the server, the client gets a 504 Gateway Timeout.
     * <p>
     * If the proxy-to-server socket times out <em>after</em> some data has been
     * sent to the client, the client-to-proxy socket is simply closed.
     */
    private volatile int socketReadTimeoutSeconds = 300;

    /**
     * Thread pool size for processing all requests.
     */
    private volatile int maxThreads = 100;

    /**
     * If set this {@link URI} is used as a proxy for all outgoing requests.
     */
    private volatile URI forwardProxyURI = null;

    /**
     * Only used when {@link #forwardProxyURI} is set.
     * 
     * This string (like ".corp.microsoft.com") is appended to host names
     * lacking a dot (".") when matched against {@link #forwardProxyBypassHosts}
     * .
     */
    private volatile String forwardProxyBypassHostDefaultDomain = null;

    /**
     * Only used when {@link #forwardProxyURI} is set.
     * 
     * Request targets matching this list (of hosts and/or domains) are NOT
     * routed through the {@link #forwardProxyURI} if it is set.
     * 
     * Synchronized on {@link #forwardProxyBypassHosts}.
     */
    private final Set<String> forwardProxyBypassHosts = new HashSet<String>();

    /**
     * The maximum HTTP header size for requests/responses.
     */
    private final int maxHeaderSizeBytes = 32 * 1024;

    /**
     * Time to sleep before returning the status code with the response.
     */
    private int responseDelayMilliseconds;

    public Options()
    {
    }

    public int getLocalPort()
    {
        return this.localPort;
    }

    public void setLocalPort(int localPort)
    {
        this.localPort = localPort;
    }

    public int getConnectTimeoutSeconds()
    {
        return this.connectTimeoutSeconds;
    }

    public void setConnectTimeoutSeconds(int connectTimeoutSeconds)
    {
        this.connectTimeoutSeconds = connectTimeoutSeconds;
    }

    public int getSocketReadTimeoutSeconds()
    {
        return this.socketReadTimeoutSeconds;
    }

    public void setSocketReadTimeoutSeconds(int socketReadTimeoutSeconds)
    {
        this.socketReadTimeoutSeconds = socketReadTimeoutSeconds;
    }

    public int getResponseDelayMilliseconds()
    {
        return responseDelayMilliseconds;
    }

    public void setResponseDelayMilliseconds(int responseDelayMilliseconds)
    {
        this.responseDelayMilliseconds = responseDelayMilliseconds;
    }

    public int getMaxThreads()
    {
        return this.maxThreads;
    }

    public void setMaxThreads(int maxThreads)
    {
        this.maxThreads = maxThreads;
    }

    public URI getForwardProxyURI()
    {
        return this.forwardProxyURI;
    }

    /**
     * Set the URI string of the proxy to forward all non-bypass requests
     * through.
     * 
     * @param the
     *        URI string of the proxy
     */
    public void setForwardProxyURI(String forwardProxyURIString)
    {
        if (forwardProxyURIString == null)
        {
            this.forwardProxyURI = null;
        }
        else
        {
            URI uri;

            try
            {
                uri = new URI(forwardProxyURIString);
            }
            catch (URISyntaxException e)
            {
                throw new RuntimeException(MessageFormat.format("Invalid proxy URL: {0}", forwardProxyURIString), e);
            }

            this.forwardProxyURI = uri;
        }
    }

    public String getForwardProxyBypassHostDefaultDomain()
    {
        return this.forwardProxyBypassHostDefaultDomain;
    }

    /**
     * Sets the canonical domain to append to domain-less host names before
     * matching against the proxy bypass list.
     * 
     * @param domain
     *        the domain name suffix (like ".corp.microsoft.com")
     */
    public void setForwardProxyBypassHostDefaultDomain(String domain)
    {
        this.forwardProxyBypassHostDefaultDomain = domain;
    }

    /**
     * Add a proxy bypass host entry to an existing handler configuration
     * 
     * @param hostOrDomain
     *        new proxy bypass entry
     */
    public void addForwardProxyBypassHost(String hostOrDomain)
    {
        if (hostOrDomain == null)
        {
            return;
        }

        synchronized (forwardProxyBypassHosts)
        {
            forwardProxyBypassHosts.add(hostOrDomain.trim());
        }
    }

    /**
     * Re-initialize the proxy bypass list of existing handler object
     * 
     * @param listOfHostOrDomain
     *        list of proxy bypass entries
     */
    public void setForwardProxyBypassHosts(List<String> listOfHostOrDomain)
    {
        if (listOfHostOrDomain == null)
        {
            return;
        }

        synchronized (forwardProxyBypassHosts)
        {
            forwardProxyBypassHosts.clear();

            for (final String entry : listOfHostOrDomain)
            {
                forwardProxyBypassHosts.add(entry.trim());
            }
        }
    }

    public boolean hostMatchesForwardProxyBypassHosts(String host)
    {
        if (!host.contains("."))
        {
            if (forwardProxyBypassHostDefaultDomain != null)
            {
                host = host + "." + forwardProxyBypassHostDefaultDomain;
            }
        }

        final String lowerHost = host.toLowerCase();

        synchronized (forwardProxyBypassHosts)
        {
            for (String bypass : forwardProxyBypassHosts)
            {
                final String lowerBypass = bypass.toLowerCase();

                if (lowerHost.equals(lowerBypass) || lowerHost.endsWith(lowerBypass))
                {
                    return true;
                }
            }
        }

        return false;
    }

    public Set<String> getForwardProxyBypassHosts()
    {
        synchronized (forwardProxyBypassHosts)
        {
            return new HashSet<String>(forwardProxyBypassHosts);
        }
    }

    public int getMaxHeaderSizeBytes()
    {
        return maxHeaderSizeBytes;
    }
}

/*
 * Poxy: a simple HTTP proxy for testing.
 * 
 * Copyright (c) Microsoft Corporation. All rights reserved.
 */

package com.edwardthomson.poxyproxy;

import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import com.edwardthomson.poxyproxy.logger.LogLevel;
import com.edwardthomson.poxyproxy.logger.Logger;

public class Request
{
    private final static Logger logger = Logger.getLogger(Request.class);

    private final InputStream in;

    private final List<Header> headers = new ArrayList<Header>();
    private String method;
    private String uri;
    private String version = Constants.VERSION_10;

    public Request(final InputStream in)
    {
        this.in = in;
    }

    /**
     * Reads the request up to but not including the POST data.
     */
    public boolean read()
        throws HTTPException,
            IOException
    {
        if (readRequestLine())
        {
            headers.addAll(IOUtils.readHeaders(in));

            // Now positioned at the start of the POST data
            return true;
        }

        // End of stream before reading request
        return false;
    }

    public InputStream getInputStream()
    {
        return in;
    }

    public String getMethod()
    {
        return method;
    }

    public String getURI()
    {
        return uri;
    }

    public String getVersion()
    {
        return version;
    }

    public List<Header> getHeaders()
    {
        return headers;
    }

    @Override
    public String toString()
    {
        return MessageFormat.format("{0} {1} {2}", method, uri, version);
    }

    /**
     * Reads the request line.
     * 
     * @return true if a request line was read, false if the socket was closed
     *         before any request line could be read (client wants to end a
     *         keep-alive connection)
     * @throws IOException
     *         if the request was not in the correct format or some other socket
     *         error happened
     */
    private boolean readRequestLine()
        throws IOException
    {
        final String line = IOUtils.readLine(in);

        /*
         * A null line means end of stream, which can happen if the client wants
         * to close a keep-alive connection.
         */
        if (line == null)
        {
            return false;
        }

        final String[] parts = line.split(" ", 3);
        if (parts.length != 3)
        {
            throw new HTTPException("Not enough parts in request line '" + line + "'");
        }

        parseMethod(parts[0]);
        parseURI(parts[1]);
        parseVersion(parts[2]);

        logger.write(LogLevel.DEBUG, method + " " + uri + " " + version);

        return true;
    }

    private void parseVersion(String string)
        throws HTTPException
    {
        if (string == null || string.length() == 0)
        {
            throw new HTTPException("Empty request version");
        }

        if (!string.equals(Constants.VERSION_10) && !string.equals(Constants.VERSION_11))
        {
            throw new HTTPException("Unknown version '" + string + "'");
        }

        version = string;
    }

    private void parseURI(String string)
        throws HTTPException
    {
        if (string == null || string.length() == 0)
        {
            throw new HTTPException("Empty request URI");
        }

        uri = string;
    }

    private void parseMethod(String string)
        throws HTTPException
    {
        if (string == null || string.length() == 0)
        {
            throw new HTTPException("Empty request method");
        }

        method = string;
    }
}

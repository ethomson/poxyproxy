/*
 * Poxy: a simple HTTP proxy for testing.
 * 
 * Copyright (c) Microsoft Corporation. All rights reserved.
 */

package com.microsoft.tfs.tools.poxy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

public class IOUtils
{
    private final static Logger logger = Logger.getLogger(IOUtils.class);

    /**
     * Reads one text line from an {@link InputStream} according to HTTP RFC
     * rules, consuming the first CR/LF or LF encountered.
     * 
     * @return the line read from the input stream, <code>null</code> if
     *         end-of-stream was encountered before reading any characters
     * @throws IOException
     *         if the end-of-stream was encountered after reading at least one
     *         character on the line
     */
    public static String readLine(final InputStream input)
        throws IOException
    {
        final byte[] rawLine = readRawLine(input);

        if (rawLine == null)
        {
            return null;
        }

        return UTF8Utils.decode(rawLine);
    }

    private static byte[] readRawLine(final InputStream input)
        throws IOException
    {
        int read = 0;
        final ByteArrayOutputStream line = new ByteArrayOutputStream(128);

        while (true)
        {
            final int b = input.read();

            if (b == -1)
            {
                if (read == 0)
                {
                    return null;
                }

                throw new IOException("End of stream while reading request line");
            }
            else if (b == '\r')
            {
                /*
                 * Ignore. This has the effect of discarding bare CRs (those
                 * that do not precede a LF) from the line.
                 */
            }
            else if (b == '\n')
            {
                break;
            }
            else
            {
                line.write((byte) b);
            }

            read++;
        }

        return line.toByteArray();
    }

    public static List<Header> readHeaders(final InputStream input)
        throws IOException
    {
        final List<Header> ret = new ArrayList<Header>();

        while (true)
        {
            final String line = IOUtils.readLine(input);

            // A null line means end of stream, which shouldn't happen yet
            if (line == null)
            {
                throw new HTTPException("Connection closed while reading headers");
            }

            // An empty line means end of headers
            if (line.length() == 0)
            {
                break;
            }

            final Header h = new Header(line);
            logger.trace(h.getName() + ": " + h.getValue());
            ret.add(h);
        }

        return ret;
    }

    public static void close(final Socket socket)
    {
        if (socket == null)
        {
            return;
        }

        try
        {
            socket.close();
        }
        catch (IOException e)
        {
            logger.debug("Error closing socket", e);
        }
    }

    /**
     * Copies count bytes from input to output. If the count is negative, bytes
     * are copied until the end of stream.
     */
    public static void copyStream(final InputStream input, final OutputStream output, long count)
        throws IOException
    {
        final byte[] buffer = new byte[64 * 1024];

        // Easier to duplicate loops than to unify control behavior
        if (count < 0)
        {
            int read;
            while ((read = input.read(buffer)) != -1)
            {
                output.write(buffer, 0, read);
            }
        }
        else
        {
            while (count > 0)
            {
                // Safe to cast to int because it's less than the buffer size
                int maxToRead = count > buffer.length ? buffer.length : (int) count;

                final int read = input.read(buffer, 0, maxToRead);

                if (read == -1)
                {
                    return;
                }

                count -= read;

                output.write(buffer, 0, read);
            }
        }
    }

    public static void copyChunkedStream(InputStream input, OutputStream output)
        throws IOException
    {
        // See http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.6.1

        while (true)
        {
            final String chunkSizeAndExtension = readLine(input);

            if (chunkSizeAndExtension == null)
            {
                throw new HTTPException("Did not receive "
                    + Constants.TRANSFER_ENCODING_HEADER
                    + " "
                    + Constants.TRANSFER_ENCODING_CHUNKED
                    + " header");
            }

            output.write(UTF8Utils.encode(chunkSizeAndExtension + "\r\n"));

            final String[] parts = chunkSizeAndExtension.split(";", 2);

            final long size = Long.parseLong(parts[0].trim(), 16);

            if (size == 0)
            {
                logger.trace("Got last chunk");

                // Should be one CRLF after the last chunk
                readLine(input);
                output.write(UTF8Utils.encode("\r\n"));

                break;
            }

            logger.trace("Copying chunk of " + size + " bytes");
            copyStream(input, output, size);

            // Should be one CRLF after the data
            readLine(input);
            output.write(UTF8Utils.encode("\r\n"));
        }
    }
}

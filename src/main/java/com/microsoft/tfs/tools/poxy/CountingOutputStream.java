/*
 * Poxy: a simple HTTP proxy for testing.
 * 
 * Copyright (c) Microsoft Corporation. All rights reserved.
 */

package com.microsoft.tfs.tools.poxy;

import java.io.IOException;
import java.io.OutputStream;

public class CountingOutputStream
    extends OutputStream
{
    private final OutputStream stream;
    private long count = 0;

    public CountingOutputStream(final OutputStream stream)
    {
        this.stream = stream;
    }

    @Override
    public void flush()
        throws IOException
    {
        stream.flush();
    }

    @Override
    public void close()
        throws IOException
    {
        stream.close();
    }

    @Override
    public void write(int b)
        throws IOException
    {
        stream.write(b);
        count++;
    }

    @Override
    public void write(byte[] b)
        throws IOException
    {
        stream.write(b);
        count += b.length;
    }

    @Override
    public void write(byte[] b, int off, int len)
        throws IOException
    {
        stream.write(b, off, len);
        count += len;
    }

    public long getCount()
    {
        return count;
    }

    public void resetCount()
    {
        count = 0;
    }
}

/*
 * Poxy: a simple HTTP proxy for testing.
 * 
 * Copyright (c) Microsoft Corporation. All rights reserved.
 */

package com.microsoft.tfs.tools.poxy;

import com.microsoft.tfs.tools.poxy.logger.Logger;

public class Header
{
    static final Logger logger = Logger.getLogger(Header.class);

    private final String name;
    private final String value;

    public Header(final String name, final String value)
    {
        this.name = name;
        this.value = value;
    }

    public Header(String line)
        throws HTTPException
    {
        if (line == null || line.length() == 0)
        {
            throw new HTTPException("Can't parse an empty line as a Header");
        }

        final String[] parts = line.split(":", 2);

        if (parts.length != 2)
        {
            throw new HTTPException("Header line '" + line + "' missing separator");
        }

        this.name = parts[0].trim();
        this.value = parts[1].trim();

        if (this.name.length() == 0)
        {
            throw new HTTPException("Header line '" + line + "' missing name");
        }

        // Value may be empty
    }

    public String getName()
    {
        return name;
    }

    public boolean matchesName(final String n)
    {
        return name.equalsIgnoreCase(n);
    }

    public String getValue()
    {
        return value;
    }

    @Override
    public String toString()
    {
        return name.trim() + ": " + value;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj == this)
        {
            return true;
        }

        if (obj instanceof Header == false)
        {
            return false;
        }

        return ((Header) obj).name.equals(name) && ((Header) obj).value.equals(value);
    }

    @Override
    public int hashCode()
    {
        int result = 17;

        result = result * 37 + name.hashCode();
        result = result * 37 + value.hashCode();

        return result;
    }
}

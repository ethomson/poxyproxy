/*
 * Poxy: a simple HTTP proxy for testing.
 * 
 * Copyright (c) Microsoft Corporation. All rights reserved.
 */

package com.edwardthomson.poxyproxy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class HeaderFilter
{
    private final Set<String> headerNamesToRefuse = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);

    public HeaderFilter(final String[] headerNamesToRefuse)
    {
        this(Arrays.asList(headerNamesToRefuse));
    }

    public HeaderFilter(final Collection<String> headerNamesToRefuse)
    {
        this.headerNamesToRefuse.addAll(headerNamesToRefuse);
    }

    public List<Header> filter(final Iterable<Header> headers)
    {
        final List<Header> ret = new ArrayList<Header>();

        for (Header header : headers)
        {
            if (!headerNamesToRefuse.contains(header.getName()))
            {
                ret.add(header);
            }
        }
        return ret;
    }
}

/*
 * Poxy: a simple HTTP proxy for testing.
 * 
 * Copyright (c) Microsoft Corporation. All rights reserved.
 */

package com.microsoft.tfs.tools.poxy;

import java.util.HashMap;
import java.util.Map;

/**
 * Status codes we parse and send. Not an authoritative list.
 */
public class Status
{
    public static final int OK = 200;

    public static final int BAD_REQUEST = 400;    
    public static final int PROXY_AUTHENTICATION_REQUIRED = 407;

    public static final int INTERNAL_SERVER_ERROR = 500;
    public static final int BAD_GATEWAY = 502;
    public static final int GATEWAY_TIMEOUT = 504;

    public final static Map<Integer, String> NAMES = new HashMap<Integer, String>();
    static
    {
        NAMES.put(OK, "OK");

        NAMES.put(BAD_REQUEST, "Bad Request");
        NAMES.put(PROXY_AUTHENTICATION_REQUIRED, "Proxy Authentication Required");

        NAMES.put(INTERNAL_SERVER_ERROR, "Internal Server Error");
        NAMES.put(BAD_GATEWAY, "Bad Gateway");
        NAMES.put(GATEWAY_TIMEOUT, "Gateway Timeout");
    }
}

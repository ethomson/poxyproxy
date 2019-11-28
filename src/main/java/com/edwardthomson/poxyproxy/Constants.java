/*
 * Poxy: a simple HTTP proxy for testing.
 * 
 * Copyright (c) Microsoft Corporation. All rights reserved.
 */

package com.edwardthomson.poxyproxy;

public interface Constants
{
    // Versions

    public static final String VERSION_11 = "HTTP/1.1";
    public static final String VERSION_10 = "HTTP/1.0";

    // Methods

    public static final String GET_METHOD = "GET";
    public static final String POST_METHOD = "POST";
    public static final String HEAD_METHOD = "HEAD";
    public static final String CONNECT_METHOD = "CONNECT";

    // Headers

    public static final String CONNECTION_HEADER = "Connection";
    public static final String CONNECTION_KEEP_ALIVE = "Keep-Alive";
    public static final String CONNECTION_CLOSE = "Close";

    public static final String PROXY_CONNECTION_HEADER = "Proxy-Connection";
    
    public static final String PROXY_AUTHORIZATION_HEADER = "Proxy-Authorization";
    public static final String PROXY_AUTHENTICATE_HEADER = "Proxy-Authenticate";

    public static final String CONTENT_LENGTH_HEADER = "Content-Length";

    public static final String CONTENT_TYPE_HEADER = "Content-Type";
    public static final String CONTENT_TYPE_TEXT_HTML = "text/html";

    public static final String TRANSFER_ENCODING_HEADER = "Transfer-Encoding";
    public static final String TRANSFER_ENCODING_CHUNKED = "chunked";
    public static final String TRANSFER_ENCODING_IDENTITY = "identity";

}

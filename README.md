Poxy Proxy
==========
The "poxy proxy" is a simple HTTP proxy that supports a breadth of
proxy functionality and is suitable for testing applications that
require the ability to proxy.  The proxy can provide support for
proxying RFC 4559 authentication mechanisms, double-hop proxying
and configurable latency.

This is not suitable for production deployments.

History
-------
The poxy proxy was originally developed by the Microsoft Team Foundation
Server team to test network configurations like double-hop proxying,
proxying of NTLMSSP (RFC 4559) connections and unreliable or slow network
infrastructures.

The poxy proxy is released under an MIT license, as it provides a
code sample for providing proxy support for authentication mechanisms
that require connection affinity (NTLM, Kerberos).

The name derives from the term "pox"; an HTTP proxy is a pox upon your
network.

Copyright
---------
Copyright (c) Microsoft Corporation.  All rights reserved.

Available under an MIT license.  Please see the included file `LICENSE`
for additional details.


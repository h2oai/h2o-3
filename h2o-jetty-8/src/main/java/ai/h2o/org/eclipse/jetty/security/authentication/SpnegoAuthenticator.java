//
//  ========================================================================
//  Copyright (c) 1995-2019 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//
package ai.h2o.org.eclipse.jetty.security.authentication;

import java.io.IOException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.http.HttpHeaders;
import org.eclipse.jetty.security.ServerAuthException;
import org.eclipse.jetty.security.UserAuthentication;
import org.eclipse.jetty.security.authentication.DeferredAuthentication;
import org.eclipse.jetty.security.authentication.LoginAuthenticator;
import org.eclipse.jetty.server.Authentication;
import org.eclipse.jetty.server.Authentication.User;
import org.eclipse.jetty.server.UserIdentity;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.security.Constraint;

public class SpnegoAuthenticator extends LoginAuthenticator
{
    private static final Logger LOG = Log.getLogger(SpnegoAuthenticator.class);
    private String _authMethod = Constraint.__SPNEGO_AUTH;

    public SpnegoAuthenticator()
    {
    }

    /**
     * Allow for a custom authMethod value to be set for instances where SPNEGO may not be appropriate
     *
     * @param authMethod the auth method
     */
    public SpnegoAuthenticator(String authMethod)
    {
        _authMethod = authMethod;
    }

    @Override
    public String getAuthMethod()
    {
        return _authMethod;
    }

    @Override
    public Authentication validateRequest(ServletRequest request, ServletResponse response, boolean mandatory) throws ServerAuthException
    {
        HttpServletRequest req = (HttpServletRequest)request;
        HttpServletResponse res = (HttpServletResponse)response;

        String header = req.getHeader(HttpHeaders.AUTHORIZATION);
        String authScheme = getAuthSchemeFromHeader(header);

        if (!mandatory)
        {
            return new DeferredAuthentication(this);
        }

        // The client has responded to the challenge we sent previously
        if (header != null && isAuthSchemeNegotiate(authScheme))
        {
            String spnegoToken = header.substring(10);

            UserIdentity user = login(null, spnegoToken, request);

            if (user != null)
            {
                return new UserAuthentication(getAuthMethod(), user);
            }
        }

        // A challenge should be sent if any of the following cases are true:
        //   1. There was no Authorization header provided
        //   2. There was an Authorization header for a type other than Negotiate
        try
        {
            if (DeferredAuthentication.isDeferred(res))
            {
                return Authentication.UNAUTHENTICATED;
            }

            LOG.debug("Sending challenge");
            res.setHeader(HttpHeaders.WWW_AUTHENTICATE, HttpHeaders.NEGOTIATE);
            res.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return Authentication.SEND_CONTINUE;
        }
        catch (IOException ioe)
        {
            throw new ServerAuthException(ioe);
        }
    }

    /**
     * Extracts the auth_scheme from the HTTP Authorization header, {@code Authorization: <auth_scheme> <token>}.
     *
     * @param header The HTTP Authorization header or null.
     * @return The parsed auth scheme from the header, or the empty string.
     */
    String getAuthSchemeFromHeader(String header)
    {
        // No header provided, return the empty string
        if (header == null || header.isEmpty())
        {
            return "";
        }
        // Trim any leading whitespace
        String trimmedHeader = header.trim();
        // Find the first space, all characters prior should be the auth_scheme
        int index = trimmedHeader.indexOf(' ');
        if (index > 0)
        {
            return trimmedHeader.substring(0, index);
        }
        // If we don't find a space, this is likely malformed, just return the entire value
        return trimmedHeader;
    }

    /**
     * Determines if provided auth scheme text from the Authorization header is case-insensitively
     * equal to {@code negotiate}.
     *
     * @param authScheme The auth scheme component of the Authorization header
     * @return True if the auth scheme component is case-insensitively equal to {@code negotiate}, False otherwise.
     */
    boolean isAuthSchemeNegotiate(String authScheme)
    {
        if (authScheme == null || authScheme.length() != HttpHeaders.NEGOTIATE.length())
        {
            return false;
        }
        // Headers should be treated case-insensitively, so we have to jump through some extra hoops.
        return authScheme.equalsIgnoreCase(HttpHeaders.NEGOTIATE);
    }

    @Override
    public boolean secureResponse(ServletRequest request, ServletResponse response, boolean mandatory, User validatedUser) throws ServerAuthException
    {
        return true;
    }
}

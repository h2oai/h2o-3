//
//  ========================================================================
//  Copyright (c) 1995-2016 Mort Bay Consulting Pty. Ltd.
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

package water.webserver.jetty8.security;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Locale;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import javax.servlet.http.HttpSession;

import org.eclipse.jetty.http.HttpHeaders;
import org.eclipse.jetty.http.HttpMethods;
import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.security.ServerAuthException;
import org.eclipse.jetty.security.UserAuthentication;
import org.eclipse.jetty.security.authentication.DeferredAuthentication;
import org.eclipse.jetty.security.authentication.LoginAuthenticator;
import org.eclipse.jetty.security.authentication.SessionAuthentication;
import org.eclipse.jetty.server.AbstractHttpConnection;
import org.eclipse.jetty.server.Authentication;
import org.eclipse.jetty.server.Authentication.User;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.UserIdentity;
import org.eclipse.jetty.util.MultiMap;
import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.util.URIUtil;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.security.Constraint;

/**
 * FORM Authenticator.
 *
 * <p>This authenticator implements form authentication will use dispatchers to
 * the login page if the {@link #__FORM_DISPATCH} init parameter is set to true.
 * Otherwise it will redirect.</p>
 *
 * <p>The form authenticator redirects unauthenticated requests to a log page
 * which should use a form to gather username/password from the user and send them
 * to the /j_security_check URI within the context.  FormAuthentication uses 
 * {@link SessionAuthentication} to wrap Authentication results so that they
 * are  associated with the session.</p>
 *
 *
 */
public class FormAuthenticator extends LoginAuthenticator
{
    private static final Logger LOG = Log.getLogger(FormAuthenticator.class);

    public final static String __FORM_LOGIN_PAGE="org.eclipse.jetty.security.form_login_page";
    public final static String __FORM_ERROR_PAGE="org.eclipse.jetty.security.form_error_page";
    public final static String __FORM_DISPATCH="org.eclipse.jetty.security.dispatch";
    public final static String __J_URI = "org.eclipse.jetty.security.form_URI";
    public final static String __J_POST = "org.eclipse.jetty.security.form_POST";
    public final static String __J_SECURITY_CHECK = "/j_security_check";
    public final static String __J_USERNAME = "j_username";
    public final static String __J_PASSWORD = "j_password";

    private String _formErrorPage;
    private String _formErrorPath;
    private String _formLoginPage;
    private String _formLoginPath;
    private boolean _dispatch;
    private boolean _alwaysSaveUri;
    private boolean _useRelativeRedirects;

    public FormAuthenticator()
    {
    }

    /* ------------------------------------------------------------ */
    public FormAuthenticator(String login,String error,boolean dispatch,boolean useRelativeRedirects)
    {
        this();
        if (login!=null)
            setLoginPage(login);
        if (error!=null)
            setErrorPage(error);
        _dispatch=dispatch;
        _useRelativeRedirects=useRelativeRedirects;
    }

    /* ------------------------------------------------------------ */
    /**
     * If true, uris that cause a redirect to a login page will always
     * be remembered. If false, only the first uri that leads to a login
     * page redirect is remembered.
     * See https://bugs.eclipse.org/bugs/show_bug.cgi?id=379909
     * @param alwaysSave
     */
    public void setAlwaysSaveUri (boolean alwaysSave)
    {
        _alwaysSaveUri = alwaysSave;
    }


    /* ------------------------------------------------------------ */
    public boolean getAlwaysSaveUri ()
    {
        return _alwaysSaveUri;
    }

    public boolean getUseRelativeRedirects ()
    {
        return _useRelativeRedirects;
    }

    /* ------------------------------------------------------------ */
    /**
     * @see org.eclipse.jetty.security.authentication.LoginAuthenticator#setConfiguration(org.eclipse.jetty.security.Authenticator.AuthConfiguration)
     */
    @Override
    public void setConfiguration(AuthConfiguration configuration)
    {
        super.setConfiguration(configuration);
        String login=configuration.getInitParameter(FormAuthenticator.__FORM_LOGIN_PAGE);
        if (login!=null)
            setLoginPage(login);
        String error=configuration.getInitParameter(FormAuthenticator.__FORM_ERROR_PAGE);
        if (error!=null)
            setErrorPage(error);
        String dispatch=configuration.getInitParameter(FormAuthenticator.__FORM_DISPATCH);
        _dispatch = dispatch==null?_dispatch:Boolean.valueOf(dispatch);
    }

    /* ------------------------------------------------------------ */
    public String getAuthMethod()
    {
        return Constraint.__FORM_AUTH;
    }

    /* ------------------------------------------------------------ */
    private void setLoginPage(String path)
    {
        if (!path.startsWith("/"))
        {
            LOG.warn("form-login-page must start with /");
            path = "/" + path;
        }
        _formLoginPage = path;
        _formLoginPath = path;
        if (_formLoginPath.indexOf('?') > 0)
            _formLoginPath = _formLoginPath.substring(0, _formLoginPath.indexOf('?'));
    }

    /* ------------------------------------------------------------ */
    private void setErrorPage(String path)
    {
        if (path == null || path.trim().length() == 0)
        {
            _formErrorPath = null;
            _formErrorPage = null;
        }
        else
        {
            if (!path.startsWith("/"))
            {
                LOG.warn("form-error-page must start with /");
                path = "/" + path;
            }
            _formErrorPage = path;
            _formErrorPath = path;

            if (_formErrorPath.indexOf('?') > 0)
                _formErrorPath = _formErrorPath.substring(0, _formErrorPath.indexOf('?'));
        }
    }


    /* ------------------------------------------------------------ */
    @Override
    public UserIdentity login(String username, Object password, ServletRequest request)
    {

        UserIdentity user = super.login(username,password,request);
        if (user!=null)
        {
            HttpSession session = ((HttpServletRequest)request).getSession(true);
            Authentication cached=new SessionAuthentication(getAuthMethod(),user,password);
            session.setAttribute(SessionAuthentication.__J_AUTHENTICATED, cached);
        }
        return user;
    }

    /* ------------------------------------------------------------ */
    public Authentication validateRequest(ServletRequest req, ServletResponse res, boolean mandatory) throws ServerAuthException
    {
        HttpServletRequest request = (HttpServletRequest)req;
        HttpServletResponse response = (HttpServletResponse)res;
        String uri = request.getRequestURI();
        if (uri==null)
            uri=URIUtil.SLASH;

        mandatory|=isJSecurityCheck(uri);
        if (!mandatory)
            return new DeferredAuthentication(this);

        if (isLoginOrErrorPage(URIUtil.addPaths(request.getServletPath(),request.getPathInfo())) &&!DeferredAuthentication.isDeferred(response))
            return new DeferredAuthentication(this);

        HttpSession session = request.getSession(true);

        try
        {
            // Handle a request for authentication.
            if (isJSecurityCheck(uri))
            {
                final String username = request.getParameter(__J_USERNAME);
                final String password = request.getParameter(__J_PASSWORD);

                UserIdentity user = login(username, password, request);
                session = request.getSession(true);
                if (user!=null)
                {
                    // Redirect to original request
                    String nuri;
                    synchronized(session)
                    {
                        nuri = (String) session.getAttribute(__J_URI);

                        if (nuri == null || nuri.length() == 0)
                        {
                            nuri = request.getContextPath();
                            if (nuri.length() == 0)
                                nuri = URIUtil.SLASH;
                        }
                    }
                    response.setContentLength(0);
                    response.sendRedirect(response.encodeRedirectURL(nuri));

                    return new FormAuthentication(getAuthMethod(),user);
                }

                // not authenticated
                if (LOG.isDebugEnabled())
                    LOG.debug("Form authentication FAILED for " + StringUtil.printable(username));
                if (_formErrorPage == null)
                {
                    if (response != null)
                        response.sendError(HttpServletResponse.SC_FORBIDDEN);
                }
                else if (_dispatch)
                {
                    RequestDispatcher dispatcher = request.getRequestDispatcher(_formErrorPage);
                    response.setHeader(HttpHeaders.CACHE_CONTROL,"No-cache");
                    response.setDateHeader(HttpHeaders.EXPIRES,1);
                    dispatcher.forward(new FormRequest(request), new FormResponse(response));
                }
                else
                {
                    response.sendRedirect(response.encodeRedirectURL(URIUtil.addPaths(request.getContextPath(),_formErrorPage)));
                }

                return Authentication.SEND_FAILURE;
            }

            // Look for cached authentication
            Authentication authentication = (Authentication) session.getAttribute(SessionAuthentication.__J_AUTHENTICATED);
            if (authentication != null)
            {
                // Has authentication been revoked?
                if (authentication instanceof Authentication.User &&
                        _loginService!=null &&
                        !_loginService.validate(((Authentication.User)authentication).getUserIdentity()))
                {

                    session.removeAttribute(SessionAuthentication.__J_AUTHENTICATED);
                }
                else
                {
                    String j_uri=(String)session.getAttribute(__J_URI);
                    if (j_uri!=null)
                    {
                        MultiMap<String> j_post = (MultiMap<String>)session.getAttribute(__J_POST);
                        if (j_post!=null)
                        {
                            StringBuffer buf = extractJUri(request);
                            if (j_uri.equals(buf.toString()))
                            {
                                // This is a retry of an original POST request
                                // so restore method and parameters

                                session.removeAttribute(__J_POST);
                                Request base_request = (req instanceof Request)?(Request)req:AbstractHttpConnection.getCurrentConnection().getRequest();
                                base_request.setMethod(HttpMethods.POST);
                                base_request.setParameters(j_post);
                            }
                        }
                        else
                            session.removeAttribute(__J_URI);

                    }
                    return authentication;
                }
            }

            // if we can't send challenge
            if (DeferredAuthentication.isDeferred(response))
            {
                LOG.debug("auth deferred {}",session.getId());
                return Authentication.UNAUTHENTICATED;
            }

            // remember the current URI
            synchronized (session)
            {
                // But only if it is not set already, or we save every uri that leads to a login form redirect
                if (session.getAttribute(__J_URI)==null || _alwaysSaveUri)
                {
                    StringBuffer buf = extractJUri(request);
                    session.setAttribute(__J_URI, buf.toString());

                    if (MimeTypes.FORM_ENCODED.equalsIgnoreCase(req.getContentType()) && HttpMethods.POST.equals(request.getMethod()))
                    {
                        Request base_request = (req instanceof Request)?(Request)req:AbstractHttpConnection.getCurrentConnection().getRequest();
                        base_request.extractParameters();
                        session.setAttribute(__J_POST, new MultiMap<String>(base_request.getParameters()));
                    }
                }
            }

            // send the the challenge
            if (_dispatch)
            {
                RequestDispatcher dispatcher = request.getRequestDispatcher(_formLoginPage);
                response.setHeader(HttpHeaders.CACHE_CONTROL,"No-cache");
                response.setDateHeader(HttpHeaders.EXPIRES,1);
                dispatcher.forward(new FormRequest(request), new FormResponse(response));
            }
            else
            {
                response.sendRedirect(response.encodeRedirectURL(URIUtil.addPaths(request.getContextPath(),_formLoginPage)));
            }
            return Authentication.SEND_CONTINUE;


        }
        catch (IOException e)
        {
            throw new ServerAuthException(e);
        }
        catch (ServletException e)
        {
            throw new ServerAuthException(e);
        }
    }

    StringBuffer extractJUri(HttpServletRequest request) {
        final StringBuffer buf; 
        if (_useRelativeRedirects) {
            buf = new StringBuffer(request.getContextPath());
        } else
            buf = request.getRequestURL();
        if (request.getQueryString() != null)
            buf.append("?").append(request.getQueryString());
        return buf;
    }

    /* ------------------------------------------------------------ */
    public boolean isJSecurityCheck(String uri)
    {
        int jsc = uri.indexOf(__J_SECURITY_CHECK);

        if (jsc<0)
            return false;
        int e=jsc+__J_SECURITY_CHECK.length();
        if (e==uri.length())
            return true;
        char c = uri.charAt(e);
        return c==';'||c=='#'||c=='/'||c=='?';
    }

    /* ------------------------------------------------------------ */
    public boolean isLoginOrErrorPage(String pathInContext)
    {
        return pathInContext != null && (pathInContext.equals(_formErrorPath) || pathInContext.equals(_formLoginPath));
    }

    /* ------------------------------------------------------------ */
    public boolean secureResponse(ServletRequest req, ServletResponse res, boolean mandatory, User validatedUser) throws ServerAuthException
    {
        return true;
    }

    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    protected static class FormRequest extends HttpServletRequestWrapper
    {
        public FormRequest(HttpServletRequest request)
        {
            super(request);
        }

        @Override
        public long getDateHeader(String name)
        {
            if (name.toLowerCase(Locale.ENGLISH).startsWith("if-"))
                return -1;
            return super.getDateHeader(name);
        }

        @Override
        public String getHeader(String name)
        {
            if (name.toLowerCase(Locale.ENGLISH).startsWith("if-"))
                return null;
            return super.getHeader(name);
        }

        @Override
        public Enumeration getHeaderNames()
        {
            return Collections.enumeration(Collections.list(super.getHeaderNames()));
        }

        @Override
        public Enumeration getHeaders(String name)
        {
            if (name.toLowerCase(Locale.ENGLISH).startsWith("if-"))
                return Collections.enumeration(Collections.EMPTY_LIST);
            return super.getHeaders(name);
        }
    }

    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    protected static class FormResponse extends HttpServletResponseWrapper
    {
        public FormResponse(HttpServletResponse response)
        {
            super(response);
        }

        @Override
        public void addDateHeader(String name, long date)
        {
            if (notIgnored(name))
                super.addDateHeader(name,date);
        }

        @Override
        public void addHeader(String name, String value)
        {
            if (notIgnored(name))
                super.addHeader(name,value);
        }

        @Override
        public void setDateHeader(String name, long date)
        {
            if (notIgnored(name))
                super.setDateHeader(name,date);
        }

        @Override
        public void setHeader(String name, String value)
        {
            if (notIgnored(name))
                super.setHeader(name,value);
        }

        private boolean notIgnored(String name)
        {
            if (HttpHeaders.CACHE_CONTROL.equalsIgnoreCase(name) ||
                    HttpHeaders.PRAGMA.equalsIgnoreCase(name) ||
                    HttpHeaders.ETAG.equalsIgnoreCase(name) ||
                    HttpHeaders.EXPIRES.equalsIgnoreCase(name) ||
                    HttpHeaders.LAST_MODIFIED.equalsIgnoreCase(name) ||
                    HttpHeaders.AGE.equalsIgnoreCase(name))
                return false;
            return true;
        }
    }

    /* ------------------------------------------------------------ */
    /** This Authentication represents a just completed Form authentication.
     * Subsequent requests from the same user are authenticated by the presents 
     * of a {@link SessionAuthentication} instance in their session.
     */
    public static class FormAuthentication extends UserAuthentication implements Authentication.ResponseSent
    {
        public FormAuthentication(String method, UserIdentity userIdentity)
        {
            super(method,userIdentity);
        }

        @Override
        public String toString()
        {
            return "Form"+super.toString();
        }
    }
}

package water.webserver.jetty12;

import water.webserver.iface.H2OHttpView;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Servlet filter replacing the Jetty 9 gate {@code AbstractHandler} in the new Jetty 12 ee8
 * architecture. Runs before every request; initializes request-time ThreadLocals
 * (see {@code ServletUtils.startRequestLifecycle}), blocks TRACE, and sets common headers.
 * If {@link H2OHttpView#gateHandler} reports the request as handled (e.g. TRACE → 405),
 * the filter stops the chain so no servlet downstream sees it.
 */
class H2OGateFilter implements Filter {
    private final H2OHttpView view;

    H2OGateFilter(H2OHttpView view) {
        this.view = view;
    }

    @Override
    public void init(FilterConfig filterConfig) {}

    @Override
    public void destroy() {}

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;
        boolean handled = view.gateHandler(req, res);
        if (!handled) {
            chain.doFilter(request, response);
        }
    }
}

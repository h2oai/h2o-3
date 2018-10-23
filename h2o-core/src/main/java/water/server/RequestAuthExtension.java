package water.server;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Extension point for HTTP request handling. Managed by {@link water.ExtensionManager}.
 */
public interface RequestAuthExtension {
  /**
   * Extended handler for customizing HTTP request authentication.
   *
   * @param target -
   * @param request -
   * @param response -
   * @return true if the request should be considered handled, false otherwise
   * @throws IOException -
   * @throws ServletException -
   */
  boolean handle(String target, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException;
}

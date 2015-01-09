package water.web.repl

import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.{ServletHolder, ServletContextHandler}
import water.H2O

/**
 * Created by michal on 1/6/15.
 */
object H2OWithRepl {

  def main(args: Array[String]): Unit = {
    // Launch Boot and and then H2O and ShalalaRepl
    H2O.main(args)
    launchReplServer()
  }

  def launchReplServer(): Server = {
    val server = new Server(54444)

    val context = new ServletContextHandler(ServletContextHandler.SESSIONS)
    context.setContextPath("/")
    server.setHandler(context)

    context.addServlet(new ServletHolder(new H2OReplServlet()), "/socket/*")
    server.start()
    server
  }
}

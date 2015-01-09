package water.web.repl

import javax.servlet.http.HttpServletRequest

import org.eclipse.jetty.websocket.WebSocket.{OnTextMessage, Connection}
import org.eclipse.jetty.websocket.{WebSocket, WebSocketServlet}

/**
 * Created by michal on 1/6/15.
 */
class H2OReplServlet extends WebSocketServlet {

  override def doWebSocketConnect(request: HttpServletRequest, protocol: String): WebSocket = {
    new H2OReplWebSocket
  }
}

class H2OReplWebSocket extends OnTextMessage {
  override def onOpen(connection: Connection): Unit = {
    println("Open")
  }

  override def onClose(closeCode: Int, message: String): Unit = {
    println("Close")
  }

  override def onMessage(data: String): Unit = {
    println("Message " + data)
  }
}

package water;

/**
 * A simple message which informs cluster about a new client
 * which was connected. The event is used only in flatfile mode
 * to allow client to connect to a single node, which will
 * inform a cluster about the client.
 * Hence, the rest of nodes will start ping client with heartbeat, and
 * vice versa.
 */
public class UDPClientEvent extends UDP {

  @Override
  AutoBuffer call(AutoBuffer ab) {
    // Handle only by non-client nodes
    if (ab._h2o != H2O.SELF
        && !H2O.ARGS.client
        && H2O.STATIC_H2OS != null) {
      ClientEvent ce = new ClientEvent().read(ab);
      if (ce.type == ClientEvent.Type.CONNECT) {
        H2O.STATIC_H2OS.add(ce.clientNode);
      }
    }

    return ab;
  }

  public static class ClientEvent extends Iced<ClientEvent> {

    public enum Type {
      CONNECT,
      DISCONNECT;

      public void broadcast(H2ONode clientNode) {
        ClientEvent ce = new ClientEvent(this, clientNode);
        ce.write(new AutoBuffer(H2O.SELF, udp.client_event._prior).putUdp(udp.client_event)).close();
      }
    }
    // Type of client event
    public Type type;
    // Client
    public H2ONode clientNode;

    public ClientEvent() {}
    public ClientEvent(Type type, H2ONode clientNode) {
      this.type = type;
      this.clientNode = clientNode;
    }
  }
}

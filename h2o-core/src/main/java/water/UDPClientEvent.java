package water;

import water.util.Log;

/**
 * A simple message which informs cluster about a new client
 * which was connected.
 * The event is used only in flatfile mode, where it
 * allows the client to connect to a single node, which will
 * inform the current rest of the cluster about the client. Hence, the rest of nodes will
 * start ping client with heartbeat, and vice versa.
 */
public class UDPClientEvent extends UDP {

  @Override
  AutoBuffer call(AutoBuffer ab) {

    ClientEvent ce = new ClientEvent().read(ab);
    // Ignore client events when H2O is started without client connections enabled
    if (!H2O.ARGS.allow_clients) {
      return ab;
    }
    // Ignore messages from different cloud
    if (ce.senderHeartBeat._cloud_name_hash != H2O.SELF._heartbeat._cloud_name_hash) {
      return ab;
    }

    if (!H2O.ARGS.client) {
      switch (ce.type) {
        // Connect event is not sent in multicast mode
        case CONNECT:
          if (H2O.isFlatfileEnabled()) {
            Log.info("Client reported via broadcast message " + ce.clientNode + " from " + ab._h2o);

            // It is important to propagate Client's HeartBeat information to the rest of the nodes
            ce.clientNode.setHeartBeat(ce.clientHeartBeat);

            H2O.addNodeToFlatfile(ce.clientNode);
          }
          break;
          default:
          throw new RuntimeException("Unsupported Client event: " + ce.type);
      }
    }

    return ab;
  }

  public static class ClientEvent extends Iced<ClientEvent> {

    public enum Type {
      CONNECT;
      
      public void broadcast(H2ONode clientNode) {
        ClientEvent ce = new ClientEvent(this, H2O.SELF._heartbeat, clientNode);
        ce.write(new AutoBuffer(H2O.SELF, udp.client_event._prior).putUdp(udp.client_event)).close();
      }

    }

    // Type of client event
    public Type type;
    public H2ONode clientNode;
    public HeartBeat senderHeartBeat;
    public HeartBeat clientHeartBeat;

    public ClientEvent() {
    }

    public ClientEvent(Type type, HeartBeat senderHeartBeat, H2ONode clientNode) {
      this.type = type;
      this.senderHeartBeat = senderHeartBeat;
      this.clientNode = clientNode;
      this.clientHeartBeat = clientNode._heartbeat;
    }

  }
}

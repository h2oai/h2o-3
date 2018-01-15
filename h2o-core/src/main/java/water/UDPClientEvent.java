package water;

import water.util.Log;

/**
 * A simple message which informs cluster about a new client
 * which was connected or about existing client who wants to disconnect.
 * The event is used only in flatfile mode where in case of connecting, it
 * allows the client to connect to a single node, which will
 * inform a cluster about the client. Hence, the rest of nodes will
 * start ping client with heartbeat, and vice versa.
 */
public class UDPClientEvent extends UDP {

  @Override
  AutoBuffer call(AutoBuffer ab) {

    ClientEvent ce = new ClientEvent().read(ab);
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
            H2ONode client = ce.clientNode;
            client._heartbeat = ce.clientHeartBeat;

            H2O.addNodeToFlatfile(ce.clientNode);
            H2O.reportClient(ce.clientNode);
          }
          break;
        // Regular disconnect event also doesn't have any effect in multicast mode.
        // However we need to catch the watchdog disconnect event in both multicast and flatfile mode.
        case DISCONNECT:
          // handle regular disconnection
          if (H2O.isFlatfileEnabled()) {
            Log.info("Client: " + ce.clientNode + " has been disconnected on: " + ab._h2o);
            H2O.removeNodeFromFlatfile(ce.clientNode);
            H2O.removeClient(ce.clientNode);
          }

          // In case the disconnection comes from the watchdog client, stop the cloud ( in both multicast and flatfile mode )
          if (ce.clientHeartBeat._watchdog_client) {
            Log.info("Stopping H2O cloud because watchdog client is disconnecting from the cloud.");
            // client is sending disconnect message on purpose, we can stop the cloud even without asking
            // the rest of the nodes for consensus on this
            H2O.shutdown(0);
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
      CONNECT,
      DISCONNECT;

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
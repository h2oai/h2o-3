package water;

import water.util.Log;

/**
 * A simple message which informs cluster about a new client
 * which was connected or about existing client who wants to disconnect.
 * The event is used only in flatfile mode where in case of connecting, it
 * it allows the client to connect to a single node, which will
 * inform a cluster about the client. Hence, the rest of nodes will
 * start ping client with heartbeat, and vice versa.
 */
public class UDPClientEvent extends UDP {

  @Override
  AutoBuffer call(AutoBuffer ab) {
    // Handle only by non-client nodes
    if (ab._h2o != H2O.SELF && !H2O.ARGS.client) {
      ClientEvent ce = new ClientEvent().read(ab);
      switch(ce.type){
        // Connect event is not sent in multicast mode
        case CONNECT:
          if(H2O.isFlatfileEnabled()) {
            H2O.addNodeToFlatfile(ce.clientNode);
            H2O.reportClient(ce.clientNode);
          }
          break;
        // Regular disconnect event also doesn't have any effect in multicast mode.
        // However we need to catch the watchdog disconnect event in both multicast and flatfile mode.
        case DISCONNECT:
          // handle regular disconnection
          if(H2O.isFlatfileEnabled()) {
            Log.info("Client: " + ce.clientNode + " has been disconnected on: " + ab._h2o);
            H2O.removeNodeFromFlatfile(ce.clientNode);
            H2O.removeClient(ce.clientNode);
          }

          // In case the disconnection comes from the watchdog client, stop the cloud ( in both multicast and flatfile mode )
          if(ce.clientNode._heartbeat._watchdog_client){
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
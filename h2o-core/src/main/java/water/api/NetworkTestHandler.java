package water.api;

import water.init.NetworkTest;

public class NetworkTestHandler extends Handler {
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public NetworkTestV2 fetch(int version, NetworkTestV2 js) {
    return js.fillFromImpl(new NetworkTest().execImpl());
  }
}

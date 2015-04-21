package water.api;

import water.init.NetworkTest;

public class NetworkTestHandler extends Handler {
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public NetworkTestV3 fetch(int version, NetworkTestV3 js) {
    return js.fillFromImpl(new NetworkTest().execImpl());
  }
}

package water.api;

import water.api.schemas3.NetworkBenchV3;
import water.api.schemas3.NetworkTestV3;
import water.init.NetworkBench;
import water.init.NetworkTest;

public class NetworkTestHandler extends Handler {
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public NetworkTestV3 fetch(int version, NetworkTestV3 js) {
    return js.fillFromImpl(new NetworkTest().execImpl());
  }
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public NetworkBenchV3 runBench(int version, NetworkBenchV3 nb){ return nb.fillFromImpl(new NetworkBench().doTest());}


}

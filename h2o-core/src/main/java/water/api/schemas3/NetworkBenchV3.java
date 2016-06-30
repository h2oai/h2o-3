package water.api.schemas3;

import water.api.API;
import water.init.NetworkBench;

/**
 */
public class NetworkBenchV3 extends SchemaV3<NetworkBench, NetworkBenchV3> {
  @API(help="NetworkBenchResults", direction = API.Direction.OUTPUT)
  TwoDimTableV3[] results;

  @Override
  public NetworkBenchV3 fillFromImpl(NetworkBench impl) {
    if(impl._results != null) {
      results = new TwoDimTableV3[impl._results.length];
      for(int i = 0; i < results.length; ++i)
        results[i] = (TwoDimTableV3)new TwoDimTableV3().fillFromImpl(impl._results[i].to2dTable());
    }
    return this;
  }

}

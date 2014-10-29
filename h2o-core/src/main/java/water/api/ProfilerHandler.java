package water.api;

import water.H2O;
import water.Iced;
import water.api.ProfilerHandler.Profiler;
import water.util.JProfile;

public class ProfilerHandler extends Handler<Profiler, ProfilerV2> { // TODO: recursive generics seem to prevent more specific types here
  @Override protected int min_ver() { return 2; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  protected static final class Profiler extends Iced {
    //Input
    public int _depth;

    //Output
    public String[][] _stacktraces;
    public int[][] _counts;
  }

  @Override protected ProfilerV2 schema(int version) {
    switch (version) {
      case 2:
        return new ProfilerV2();
      default:
        throw H2O.fail("Bad version for Profiler schema: " + version);
    }
  }
  public ProfilerBase fetch(int version, Profiler p) {
    JProfile profile = new JProfile(p._depth).execImpl(true);
    int i=0;
    p._stacktraces = new String[profile.nodes.length][];
    p._counts = new int[profile.nodes.length][];
    for (JProfile.ProfileSummary s : profile.nodes) {
      p._stacktraces[i] = s.profile.stacktraces;
      p._counts[i] = s.profile.counts;
      i++;
    }
    return schema(version).fillFromImpl(p);
  }
}

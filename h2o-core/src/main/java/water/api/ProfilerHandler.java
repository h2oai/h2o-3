package water.api;

import water.H2O;
import water.util.JProfile;

public class ProfilerHandler extends Handler<ProfilerHandler,ProfilerBase> {
  @Override protected int min_ver() { return 1; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  //Input
  public int _depth;

  //Output
  public String[][] _stacktraces;
  public int[][] _counts;

  @Override protected ProfilerBase schema(int version) {
    switch (version) {
      case 2:
        return new ProfilerV2();
      default:
        throw H2O.fail("Bad version for Frames schema: " + version);
    }
  }
  @Override public void compute2() {
    JProfile profile = new JProfile(_depth).execImpl();
    int i=0;
    _stacktraces = new String[profile.nodes.length][];
    _counts = new int[profile.nodes.length][];
    for (JProfile.ProfileSummary p : profile.nodes) {
      _stacktraces[i] = p.profile.stacktraces;
      _counts[i] = p.profile.counts;
      i++;
    }
  }
}

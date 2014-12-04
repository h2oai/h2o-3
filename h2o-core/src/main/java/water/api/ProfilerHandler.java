package water.api;

import water.util.JProfile;

public class ProfilerHandler extends Handler {
  @Override protected int min_ver() { return 2; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public ProfilerBase fetch(int version, ProfilerV2 p) {
    JProfile profile = new JProfile(p.depth).execImpl(true);
    int i=0;
    p.stacktraces = new String[profile.nodes.length][];
    p.counts = new int[profile.nodes.length][];
    for (JProfile.ProfileSummary s : profile.nodes) {
      p.stacktraces[i] = s.profile.stacktraces;
      p.counts[i] = s.profile.counts;
      i++;
    }
    return p;
  }
}

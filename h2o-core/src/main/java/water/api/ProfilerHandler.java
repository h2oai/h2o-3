package water.api;

import water.api.schemas3.ProfilerNodeV3;
import water.api.schemas3.ProfilerV3;
import water.util.JProfile;

public class ProfilerHandler extends Handler {
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public ProfilerV3 fetch(int version, ProfilerV3 p) {
    if (p.depth < 1) throw new IllegalArgumentException("depth must be >= 1.");

    JProfile profile = new JProfile(p.depth).execImpl(true);
    p.nodes = new ProfilerNodeV3[profile.nodes.length];
    int i=0;
    for (JProfile.ProfileSummary s : profile.nodes) {
      ProfilerNodeV3 n = new ProfilerNodeV3();

      n.node_name = s.profile.node_name;
      n.timestamp = s.profile.timestamp;
      n.entries = new ProfilerNodeV3.ProfilerNodeEntryV3[s.profile.stacktraces.length];
      for (int j = 0; j < s.profile.stacktraces.length; j++) {
        ProfilerNodeV3.ProfilerNodeEntryV3 e = new ProfilerNodeV3.ProfilerNodeEntryV3();
        e.stacktrace = s.profile.stacktraces[j];
        e.count = s.profile.counts[j];
        n.entries[j] = e;
      }

      p.nodes[i] = n;
      i++;
    }
    return p;
  }
}

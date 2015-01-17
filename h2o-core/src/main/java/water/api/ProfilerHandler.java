package water.api;

import water.util.JProfile;

public class ProfilerHandler extends Handler {
  @Override protected int min_ver() { return 2; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public ProfilerV2 fetch(int version, ProfilerV2 p) {
    if (p.depth < 1) throw new IllegalArgumentException("depth must be >= 1.");

    JProfile profile = new JProfile(p.depth).execImpl(true);
    p.nodes = new ProfilerNodeV2[profile.nodes.length];
    int i=0;
    for (JProfile.ProfileSummary s : profile.nodes) {
      ProfilerNodeV2 n = new ProfilerNodeV2();

      n.node_name = s.profile.node_name;
      n.timestamp = s.profile.timestamp;
      n.entries = new ProfilerNodeV2.ProfilerNodeEntryV2[s.profile.stacktraces.length];
      for (int j = 0; j < s.profile.stacktraces.length; j++) {
        ProfilerNodeV2.ProfilerNodeEntryV2 e = new ProfilerNodeV2.ProfilerNodeEntryV2();
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

package water.util;

import water.H2O;
import water.Iced;

public class JProfile extends Iced {
  public static class ProfileSummary extends Iced {
    public ProfileSummary( String name, ProfileCollectorTask.NodeProfile profile) { this.name=name; this.profile=profile; }
    public final String name;
    public final ProfileCollectorTask.NodeProfile profile;
  }

  public final String node_name;
  public final long timestamp;
  public final int depth;
  public JProfile(int d) {
    depth = d;
    node_name = H2O.getIpPortString();
    timestamp = System.currentTimeMillis();
  }

  public ProfileSummary nodes[];

  public JProfile execImpl(boolean print) {
    ProfileCollectorTask.NodeProfile profiles[] = new ProfileCollectorTask(depth).doAllNodes()._result;
    nodes = new ProfileSummary[H2O.CLOUD.size()];
    for( int i=0; i<nodes.length; i++ ) {
      assert(profiles[i] != null);
      nodes[i] = new ProfileSummary(H2O.CLOUD._memary[i].toString(), profiles[i]);
    }
    if( !print ) return this;

    for( int i=0; i<nodes.length; i++ ) {
      Log.info(nodes[i].name);
      for (int j = 0; j < nodes[i].profile.counts.length; ++j) {
        Log.info(nodes[i].profile.counts[j]);
        Log.info(nodes[i].profile.stacktraces[j]);
      }
    }

    return this;
  }
}
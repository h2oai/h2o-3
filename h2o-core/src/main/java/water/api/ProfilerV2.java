package water.api;
import water.api.ProfilerHandler.Profiler;
import water.util.DocGen;
import water.util.JProfile;

public class ProfilerV2 extends ProfilerBase<ProfilerV2> {
  // Input
  @API(help="Stack trace depth", required=true)
  public int depth = 5;

  // Output
  @API(help="Array of Profiles, one per Node in the Cluster")
  public JProfile profile;

  @Override public Profiler createImpl() {
    if (depth < 1) throw new IllegalArgumentException("depth must be >= 1.");
    Profiler profiler = new Profiler();
    profiler._depth = depth;
    return profiler;
  }

  @Override public ProfilerV2 fillFromImpl(Profiler profiler) {
    depth = profiler._depth;
    // TODO: profile = profiler._profile;
    return this;
  }
}

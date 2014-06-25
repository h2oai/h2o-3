package water.api;
import water.util.DocGen;
import water.util.JProfile;

public class ProfilerV2 extends Schema<ProfilerHandler,ProfilerV2> {
  // Input
  @API(help="Stack trace depth", required=true)
  public int depth = 5;

  // Output
  @API(help="Array of Profiles, one per Node in the Cluster")
  public JProfile profile;

  @Override protected ProfilerV2 fillInto(ProfilerHandler profiler) {
    if (depth < 1) throw new IllegalArgumentException("depth must be >= 1.");
    profiler._depth = depth;
    return this;
  }

  @Override public ProfilerV2 fillFrom(ProfilerHandler profiler) {
    depth = profiler._depth;
    profile = profiler._profile;
    return this;
  }

  @Override public DocGen.HTML writeHTML_impl( DocGen.HTML ab ) {
    if (profile == null || profile.nodes == null) return ab;
    StringBuilder sb = new StringBuilder();
    profile.toHTML(sb);
    ab.p(sb.toString());
    return ab;
  }
}

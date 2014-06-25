package water.api;

import water.util.JProfile;

public class ProfilerHandler extends Handler<ProfilerHandler,ProfilerV2> {
  @Override protected int min_ver() { return 1; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  //Input
  public int _depth;

  //Output
  JProfile _profile;

  @Override protected ProfilerV2 schema(int version) { return new ProfilerV2(); }
  @Override public void compute2() {
    _profile = new JProfile(_depth);
    _profile.execImpl(); }
}

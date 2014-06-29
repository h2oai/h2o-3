package water.api;

public class ProfilerV2 extends ProfilerBase {
  @Override protected ProfilerV2 fillInto(ProfilerHandler profiler) {
    super.fillInto(profiler);
    return this;
  }

  @Override public ProfilerV2 fillFrom(ProfilerHandler profiler) {
    super.fillFrom(profiler);
    return this;
  }
}

package water.api;

import water.*;
import water.schemas.JobPollV2;

// This is a place-holder Handler to handle polling.
// The JobPollV2 Schema pulls out all the polling information from the Job.
public class JobPollHandler extends Handler<JobPollHandler,JobPollV2> {
  // Supported at V1 same as always
  @Override protected int min_ver() { return 1; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  // Inputs
  public Key _jobkey;
  // Output
  // No outputs
  @Override public void compute2() { throw H2O.fail(); }
  protected void poll() { }
  @Override protected JobPollV2 schema(int version) { return new JobPollV2(); }
}

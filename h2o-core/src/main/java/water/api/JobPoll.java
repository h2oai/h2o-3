package water.api;

import water.*;
import water.schemas.JobPollV2;

// This is a place-holder Handler to handle polling.
// The JobPollV2 Schema pulls out all the polling information from the Job.
public class JobPoll extends Handler<JobPoll,JobPollV2> {
  // Inputs
  public Key _jobkey;
  // Output
  // No outputs
  @Override public void compute2() { throw H2O.fail(); }
  protected void poll() { }
  @Override protected JobPollV2 schema(int version) { return new JobPollV2(); }
}

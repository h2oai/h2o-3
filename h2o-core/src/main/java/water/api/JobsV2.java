package water.api;

import water.*;

public class JobsV2 extends Schema<JobsHandler,JobsV2> {
  // Input fields
  // This Schema has no inputs

  // Output fields
  @API(help="jobs")
  JobPollV2[] jobs;

  //==========================
  // Customer adapters Go Here

  // Version&Schema-specific filling into the handler
  @Override protected JobsV2 fillInto( JobsHandler h ) {
    return this;                // No inputs
  }

  // Version&Schema-specific filling from the handler
  @Override protected JobsV2 fillFrom( JobsHandler h ) {
    Job[] js = h.jobs;
    jobs = new JobPollV2[js.length];
    for( int i=0; i<js.length; i++ )
      jobs[i] = new JobPollV2(js[i]._key,js[i]._state.toString(),js[i].progress(),js[i].msec(),js[i]._exception);
    return this;
  }
}

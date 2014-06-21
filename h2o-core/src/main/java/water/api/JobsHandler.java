package water.api;

import water.*;

class JobsHandler extends Handler<JobsHandler,JobsV2> {
  // Supported at V1 same as always
  @Override protected int min_ver() { return 1; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  // Inputs
  // No inputs

  // Output
  Job[] jobs;
  @Override public void compute2() { throw H2O.fail(); }
  void list() { jobs = Job.jobs(); } // All work in schema
  @Override protected JobsV2 schema(int version) { return new JobsV2(); }
}

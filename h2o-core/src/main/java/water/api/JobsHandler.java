package water.api;

import water.*;
import water.api.JobsHandler.Jobs;

class JobsHandler extends Handler<Jobs,JobsV2> {
  // Supported at V1 same as always
  @Override protected int min_ver() { return 1; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  /** Impl class for a collection of jobs; only used in the API.  */
  protected static final class Jobs extends Iced {
    public Jobs() { }
    public Jobs(Key key, Job[] jobs) { this.key = key; this.jobs = jobs; }

    // Inputs
    @API(help="Job key")
    public Key key;

    // Output
    public Job[] jobs;
  }

  public static final Schema jobToSchemaHelper(int version, Job job) {
    // TODO: we really should have a single instance of each handler. . .
    return new JobsHandler().schema(version).fillFromImpl(new Jobs(job._key, new Job[] { job } ));
  }

  @Override public void compute2() { throw H2O.fail(); }                       // TODO: what to do about Key here?

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public Schema list(int version, Jobs jobs) { return schema(version).fillFromImpl(new Jobs(null, Job.jobs())); } // All work in schema

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public Schema fetch(int version, Jobs jobs) {
    Key key = jobs.key;
    Value val = DKV.get(key);
    if( null == val ) throw new IllegalArgumentException("Job is missing");
    Iced ice = val.get();
    if( !(ice instanceof Job) ) throw new IllegalArgumentException("Must be a Job not a "+ice.getClass());
    jobs.jobs = new Job[1];
    jobs.jobs[0] = (Job) ice;
    return schema(version).fillFromImpl(jobs);
  }

  @Override protected JobsV2 schema(int version) { return new JobsV2(); }
}

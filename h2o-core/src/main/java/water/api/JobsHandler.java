package water.api;

import water.*;
import water.util.PojoUtils;

public class JobsHandler extends Handler {
  // Supported at V1 same as always
  @Override protected int min_ver() { return 1; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  /** Impl class for a collection of jobs; only used in the API to make it easier to cons up the jobs array via the magic of PojoUtils.copyProperties.  */
  public static final class Jobs extends Iced {
    public Key _key;
    public Job[] _jobs;

    public Jobs() {}
    public Jobs(Job j) { _jobs = new Job[1]; _jobs[0] = j; }
  }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public Schema list(int version, JobsV2 s) {
    Jobs j = new Jobs();
    j._jobs = Job.jobs();
    PojoUtils.copyProperties(s, j, PojoUtils.FieldNaming.CONSISTENT);
    return s;
  }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public Schema fetch(int version, JobsV2 s) {
    Key key = s.key.key.key();
    Value val = DKV.get(key);
    if( null == val ) throw new IllegalArgumentException("Job is missing");
    Iced ice = val.get();
    if( !(ice instanceof Job) ) throw new IllegalArgumentException("Must be a Job not a "+ice.getClass());

    Jobs jobs = new Jobs();
    jobs._jobs = new Job[1];
    jobs._jobs[0] = (Job) ice;
    s.jobs = new JobV2[0]; // Give PojoUtils.copyProperties the destination type.
    return s.fillFromImpl(jobs);
  }
}

package water.api;

import water.*;
import water.util.PojoUtils;

public class JobsHandler extends Handler {
  /** Impl class for a collection of jobs; only used in the API to make it easier to cons up the jobs array via the magic of PojoUtils.copyProperties.  */
  public static final class Jobs extends Iced {
    public Key _job_id;
    public Job[] _jobs;

    public Jobs() {}
    public Jobs(Job j) { _jobs = new Job[1]; _jobs[0] = j; }
  }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public Schema list(int version, JobsV3 s) {
    Jobs j = new Jobs();
    j._jobs = Job.jobs();
    PojoUtils.copyProperties(s, j, PojoUtils.FieldNaming.ORIGIN_HAS_UNDERSCORES);
    return s;
  }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public Schema fetch(int version, JobsV3 s) {
    Key key = s.job_id.key();
    Value val = DKV.get(key);
    if( null == val ) throw new IllegalArgumentException("Job is missing");
    Iced ice = val.get();
    if( !(ice instanceof Job) ) throw new IllegalArgumentException("Must be a Job not a "+ice.getClass());

    Jobs jobs = new Jobs();
    jobs._jobs = new Job[1];
    jobs._jobs[0] = (Job) ice;
    s.jobs = new JobV3[0]; // Give PojoUtils.copyProperties the destination type.
    s.fillFromImpl(jobs);
    return s;
  }

  public Schema cancel(int version, JobsV3 c) {
    Job j = DKV.getGet(c.job_id.key());
    if (j == null) {
      throw new IllegalArgumentException("No job with key " + c.job_id.key());
    }
    j.cancel();
    return c;
  }
}

package water.api;

import water.*;
import water.exceptions.H2OIllegalArgumentException;
import water.exceptions.H2ONotFoundArgumentException;
import water.util.PojoUtils;

public class JobsHandler extends Handler {
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
    PojoUtils.copyProperties(s, j, PojoUtils.FieldNaming.ORIGIN_HAS_UNDERSCORES);
    return s;
  }

  /**
   * Given a JobsV2 return the Job object, or throw an exception that will result in a nice message and a 404.
   * @param s
   * @return
   */
  private static Job safeGetJob(JobsV2 s) {
    if (null == s.key) throw new H2ONotFoundArgumentException("No job with key: null", "No job with key: null"); // 404
    if (null == s.key.key) throw new H2ONotFoundArgumentException("No job with key: " + s.key, "No job with key: " + s.key); // 404
    if (null == s.key.key.key()) throw new H2ONotFoundArgumentException("No job with key: " + s.key.key, "No job with key: " + s.key.key); // 404

    Key key = s.key.key.key();
    Value val = DKV.get(key);
    if( null == val ) throw new H2ONotFoundArgumentException("No job with key: " + key, "No job with key: " + key); // 404
    Iced ice = val.get();
    if( !(ice instanceof Job) ) throw new H2OIllegalArgumentException("Found a non-Job object when given a Job key: " + s.key,
            "Found a non-Job object when given a Job key: " + s.key + "; got a: " + ice.getClass());
    return (Job)ice;
  }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public Schema fetch(int version, JobsV2 s) {
    Job job = safeGetJob(s);
    Jobs jobs = new Jobs();
    jobs._jobs = new Job[1];
    jobs._jobs[0] = job;
    s.jobs = new JobV2[0]; // Give PojoUtils.copyProperties the destination type.
    return s.fillFromImpl(jobs);
  }

  public Schema cancel(int version, JobsV2 s) {
    Job job = safeGetJob(s);
    job.cancel();
    return s;
  }
}

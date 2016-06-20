package water.api;

import water.*;
import water.exceptions.H2ONotFoundArgumentException;

public class JobsHandler extends Handler {
  /** Impl class for a collection of jobs; only used in the API to make it easier to cons up the jobs array via the magic of PojoUtils.copyProperties.  */

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public JobsV3 list(int version, JobsV3 s) {
    Job[] jobs = Job.jobs();
    // Jobs j = new Jobs();
    // j._jobs = Job.jobs();
    // PojoUtils.copyProperties(s, j, PojoUtils.FieldNaming.ORIGIN_HAS_UNDERSCORES);
    s.jobs = new JobV3[jobs.length];

    int i = 0;
    for (Job j : jobs) {
      try { s.jobs[i] = (JobV3) SchemaServer.schema(version, j).fillFromImpl(j); }
      // no special schema for this job subclass, so fall back to JobV3
      catch (H2ONotFoundArgumentException e) { s.jobs[i] = new JobV3().fillFromImpl(j); }
      i++; // Java does the increment before the function call which throws?!
    }
    return s;
  }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public JobsV3 fetch(int version, JobsV3 s) {
    Key key = s.job_id.key();
    Value val = DKV.get(key);
    if( null == val ) throw new IllegalArgumentException("Job is missing");
    Iced ice = val.get();
    if( !(ice instanceof Job) ) throw new IllegalArgumentException("Must be a Job not a "+ice.getClass());

    Job j = (Job) ice;
    s.jobs = new JobV3[1];
    // s.fillFromImpl(jobs);
    try { s.jobs[0] = (JobV3) SchemaServer.schema(version, j).fillFromImpl(j); }
    // no special schema for this job subclass, so fall back to JobV3
    catch (H2ONotFoundArgumentException e) { s.jobs[0] = new JobV3().fillFromImpl(j); }
    return s;
  }

  public JobsV3 cancel(int version, JobsV3 c) {
    Job j = DKV.getGet(c.job_id.key());
    if (j == null) {
      throw new IllegalArgumentException("No job with key " + c.job_id.key());
    }
    j.stop(); // Request Job stop
    return c;
  }
}

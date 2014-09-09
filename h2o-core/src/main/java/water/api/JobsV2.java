package water.api;

import water.*;
import water.api.JobsHandler.Jobs;

public class JobsV2 extends Schema<Jobs,JobsV2> {
  // Input fields
  @API(help="Optional Job key")
  public Key key;

  // Output fields
  @API(help="jobs")
  JobV2[] jobs;

  //==========================
  // Custom adapters go here

  // Version&Schema-specific filling into the impl
  @Override public Jobs createImpl( ) {
    Job[] jobs = null;
    if (null != this.jobs) {
      jobs = new Job[this.jobs.length];
      for (int i = 0; i < this.jobs.length; i++)
        jobs[i] = this.jobs[i].createImpl();
    } else {
      jobs = new Job[0];
    }
    return new Jobs(this.key, jobs);
  }

  // Version&Schema-specific filling from the impl
  @Override public JobsV2 fillFromImpl(Jobs j) {
    this.key = j.key;
    Job[] js = j.jobs;
    jobs = new JobV2[js.length];
    for( int i=0; i<js.length; i++ ) {
      Job job = js[i];
      jobs[i] = new JobV2(job._key, job._description, job._state.toString(), job.progress(), job.msec(), job.dest(), job._exception);
    }
    return this;
  }
}

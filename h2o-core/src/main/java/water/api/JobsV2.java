package water.api;

import water.*;
import water.api.JobsHandler.Jobs;

public class JobsV2 extends Schema<Jobs,JobsV2> {
  // Input fields
  @API(help="Optional Job key")
  public Key key;

  // Output fields
  @API(help="jobs", direction=API.Direction.OUTPUT)
  JobV2[] jobs;

  //==========================
  // Custom adapters go here

  @Override public Jobs fillImpl(Jobs j) {
    Job[] jobs = null;
    if (null != this.jobs) {
      jobs = new Job[this.jobs.length];
      for (int i = 0; i < this.jobs.length; i++)
        jobs[i] = this.jobs[i].createImpl();
    } else {
      jobs = new Job[0];
    }
    super.fillImpl(j);
    return j;
  }

  // Version&Schema-specific filling from the impl
  @Override public JobsV2 fillFromImpl(Jobs j) {
    this.key = j.key;
    Job[] js = j.jobs;
    jobs = null;
    if (null != js) {
      jobs = new JobV2[js.length];

      for (int i = 0; i < js.length; i++) {
        Job job = js[i];
        jobs[i] = new JobV2(job._key, job._description, job._state.toString(), job.progress(), job.msec(), job.dest(), job._exception);
      }
    }
    return this;
  }
}

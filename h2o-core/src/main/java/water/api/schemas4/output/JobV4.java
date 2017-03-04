package water.api.schemas4.output;

import water.Job;
import water.TypeMap;
import water.api.API;
import water.api.schemas4.OutputSchemaV4;

import java.io.PrintWriter;
import java.io.StringWriter;


/** Schema for a single Job. */
public class JobV4 extends OutputSchemaV4<Job<?>, JobV4> {

  @API(help="Job id")
  public String job_id;

  @API(help="Job status", values={"RUNNING", "DONE", "STOPPING", "CANCELLED", "FAILED"})
  public Status status;

  @API(help="Current progress, a number going from 0 to 1")
  public float progress;

  @API(help="Current progress status description")
  public String progress_msg;

  @API(help="Start time")
  public long start_time;

  @API(help="Runtime in milliseconds")
  public long duration;

  @API(help="Id of the target object (being created by this Job)")
  public String target_id;

  @API(help="Type of the target: Frame, Model, etc.")
  public String target_type;

  @API(help="Exception message, if an exception occurred")
  public String exception;

  @API(help="Stacktrace")
  public String stacktrace;

  public enum Status {
    RUNNING, DONE, STOPPING, CANCELLED, FAILED
  }


  @Override public JobV4 fillFromImpl(Job<?> job) {
    if (job == null) return this;

    job_id = job._key.toString();
    progress = job.progress();
    progress_msg = job.progress_msg();
    duration = job.msec();

    if (job.isRunning()) {
      status = job.stop_requested()? Status.STOPPING : Status.RUNNING;
    } else {
      status = job.stop_requested()? Status.CANCELLED : Status.DONE;
    }
    Throwable ex = job.ex();
    if (ex != null) {
      status = Status.FAILED;
      exception = ex.toString();
      StringWriter sw = new StringWriter();
      ex.printStackTrace(new PrintWriter(sw));
      stacktrace = sw.toString();
    }

    target_id = job._result == null || !job.readyForView()? null : job._result.toString();
    target_type = TypeMap.theFreezable(job._typeid).getClass().getSimpleName();

    return this;
  }

}

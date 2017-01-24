package water.api.schemas4.output;

import water.Job;
import water.Keyed;
import water.TypeMap;
import water.api.API;
import water.api.schemas3.KeyV3;
import water.api.schemas4.OutputSchemaV4;

import java.io.PrintWriter;
import java.io.StringWriter;


/** Schema for a single Job. */
public class JobV4 extends OutputSchemaV4<Job<?>, JobV4> {

  // TODO: replace all KeyV3's with KeyV4's

  @API(help="Job key")
  public KeyV3.JobKeyV3 key;

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

  @API(help="Key of the target object (being created by this Job)")
  public KeyV3 dest;

  @API(help="Exception message, if an exception occurred")
  public String exception;

  @API(help="Stacktrace")
  public String stacktrace;

  @API(help="ready for view")
  public boolean ready_for_view;

  public enum Status {
    RUNNING, DONE, STOPPING, CANCELLED, FAILED
  }


  @Override public JobV4 fillFromImpl(Job<?> job) {
    if (job == null) return this;

    key = new KeyV3.JobKeyV3(job._key);
    progress = job.progress();
    progress_msg = job.progress_msg();
    duration = job.msec();
    ready_for_view = job.readyForView();

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

    Keyed dest_type = (Keyed) TypeMap.theFreezable(job._typeid);
    dest = job._result == null ? null : KeyV3.make(dest_type.makeSchema(), job._result);
    return this;
  }

}

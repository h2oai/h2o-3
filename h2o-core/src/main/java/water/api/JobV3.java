package water.api;

import water.*;
import water.api.KeyV3.JobKeyV3;
import water.util.PojoUtils;

import java.io.PrintWriter;
import java.io.StringWriter;

/** Schema for a single Job. */
public class JobV3 extends SchemaV3<Job, JobV3> {

  // Input fields
  @API(help="Job Key")
  public JobKeyV3 key;

  @API(help="Job description")
  public String description;

  // Output fields
  @API(help="job status", direction=API.Direction.OUTPUT)
  public String status;

  @API(help="progress, from 0 to 1", direction=API.Direction.OUTPUT)
  public float progress;               // A number from 0 to 1

  @API(help="current progress status description", direction=API.Direction.OUTPUT)
  public String progress_msg;

  @API(help="Start time", direction=API.Direction.OUTPUT)
  public long start_time;

  @API(help="Runtime in milliseconds", direction=API.Direction.OUTPUT)
  public long msec;

  @API(help="destination key", direction=API.Direction.INOUT)
  public KeyV3 dest;

  @API(help="exception", direction=API.Direction.OUTPUT)
  public String [] warnings;
  @API(help="exception", direction=API.Direction.OUTPUT)
  public String exception;

  @API(help="stacktrace", direction=API.Direction.OUTPUT)
  public String stacktrace;

  @API(help="ready for view", direction=API.Direction.OUTPUT)
  public boolean ready_for_view;

  //==========================
  // Custom adapters go here

  // Version&Schema-specific filling into the impl
  @SuppressWarnings("unchecked")
  @Override public Job createImpl( ) { throw H2O.fail(); } // Cannot make a new Job directly via REST

  // Version&Schema-specific filling from the impl
  @Override public JobV3 fillFromImpl( Job job ) {
    if( job == null ) return this;
    // Handle fields in subclasses:
    PojoUtils.copyProperties(this, job, PojoUtils.FieldNaming.ORIGIN_HAS_UNDERSCORES);
    PojoUtils.copyProperties(this, job, PojoUtils.FieldNaming.CONSISTENT);  // TODO: make consistent and remove

    key = new JobKeyV3(job._key);
    description = job._description;
    warnings = job.warns();
    progress = job.progress();
    progress_msg = job.progress_msg();
    // Bogus status; Job no longer has these states, but we fake it for /3/Job poller's.
    // Notice state "CREATED" no long exists and is never returned.
    // Notice new state "CANCEL_PENDING".
    if( job.isRunning() )
      if( job.stop_requested() ) status = "CANCEL_PENDING";
      else status = "RUNNING";
    else
      if( job.stop_requested() ) status = "CANCELLED";
      else status = "DONE";
    Throwable ex = job.ex();
    if( ex != null ) status = "FAILED";
    exception = ex == null ? null : ex.toString();
    if (ex!=null) {
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      ex.printStackTrace(pw);
      stacktrace = sw.toString();
    }
    msec = job.msec();
    ready_for_view = job.readyForView();

    Keyed dest_type = (Keyed)TypeMap.theFreezable(job._typeid);
    dest = job._result == null ? null : KeyV3.make(dest_type.makeSchema(),job._result);
    return this;
  }

  //==========================
  // Helper so Jobs can link to JobPoll
  public static String link(Key key) { return "/Jobs/"+key; }
}

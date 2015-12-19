package water.api;

import water.*;
import water.api.KeyV3.JobKeyV3;
import water.exceptions.H2OFailException;
import water.util.Log;
import water.util.PojoUtils;
import water.util.ReflectionUtils;

/** Schema for a single Job. */
public class JobV3 extends RequestSchema<Job, JobV3> {

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
  public String exception;

  //==========================
  // Custom adapters go here

  // Version&Schema-specific filling into the impl
  @SuppressWarnings("unchecked")
  @Override public Job createImpl( ) {
    try {
      Key k = key == null?Key.make():key.key();
      return this.getImplClass().getConstructor(new Class[]{Key.class,String.class}).newInstance(k,description);
    }catch (Exception e) {
      String msg = "Exception instantiating implementation object of class: " + this.getImplClass().toString() + " for schema class: " + this.getClass();
      Log.err(msg + ": " + e);
      throw H2O.fail(msg, e);
    }
  }

  // Version&Schema-specific filling from the impl
  @Override public JobV3 fillFromImpl(Job job) {
    if( job == null ) return this;
    // Handle fields in subclasses:
    PojoUtils.copyProperties(this, job, PojoUtils.FieldNaming.ORIGIN_HAS_UNDERSCORES);
    PojoUtils.copyProperties(this, job, PojoUtils.FieldNaming.CONSISTENT);  // TODO: make consistent and remove

    key = new JobKeyV3(job._key);
    description = job._description;
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
    msec = job.msec();
    Key dest_key = job._result;
    Iced ice = dest_key.get();
    if( ice == null ) { dest = new KeyV3(dest_key); return this; }
    Class<? extends Keyed> dest_class = ReflectionUtils.findActualClassParameter(ice.getClass(), 0); // What type do we expect for this Job?
    try {
      dest = KeyV3.forKeyedClass(dest_class, dest_key);
    }
    catch (H2OFailException e) {
      throw e;
    }
    catch (Exception e) {
      dest = null;
      Log.warn("JobV3.fillFromImpl(): dest key for job: " + this + " is not the expected type: " + dest_class.getCanonicalName() + ": " + dest_key + ".  Returning null for the dest field.");
    }
    return this;
  }

  //==========================
  // Helper so Jobs can link to JobPoll
  public static String link(Key key) { return "/Jobs/"+key; }
}

package water.api;

import water.H2O;
import water.Job;
import water.Key;
import water.Keyed;
import water.api.KeyV3.JobKeyV3;
import water.util.Log;
import water.util.PojoUtils;
import water.util.PrettyPrint;
import water.util.ReflectionUtils;

/** Schema for a single Job. */
public class JobV3<J extends Job, S extends JobV3<J, S>> extends Schema<J, S> {

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

  @API(help="Info, warning and error messages; NOTE: can be appended to while the Job is running", direction=API.Direction.OUTPUT)
  public ValidationMessageBase messages[];

  @API(help="Count of error messages", direction=API.Direction.OUTPUT)
  public int error_count;


  //==========================
  // Custom adapters go here

  // Version&Schema-specific filling into the impl
  @SuppressWarnings("unchecked")
  @Override public J createImpl( ) {
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
  @Override public S fillFromImpl(Job job) {
    // Handle fields in subclasses:
    PojoUtils.copyProperties(this, job, PojoUtils.FieldNaming.ORIGIN_HAS_UNDERSCORES);
    PojoUtils.copyProperties(this, job, PojoUtils.FieldNaming.CONSISTENT);  // TODO: make consistent and remove

    key = new JobKeyV3(job._key);
    description = job._description;
    progress = job.progress();
    progress_msg = job.progress_msg();
    status = job._state.toString();
    msec = (job.isStopped() ? job._end_time : System.currentTimeMillis())-job._start_time;
    Key dest_key = job.dest();
    Class<? extends Keyed> dest_class = ReflectionUtils.findActualClassParameter(job.getClass(), 0); // What type do we expect for this Job?
    dest = KeyV3.forKeyedClass(dest_class, dest_key);
    exception = job._exception;

    // NOTE: the message list can be getting longer in another thread; copy so we don't have a race.
    Job.ValidationMessage[] vms = job._messages;
    int num_messages = vms.length;
    this.messages = new ValidationMessageBase[num_messages];
    this.error_count = 0;

    for( int i = 0; i < num_messages; ) {
      Job.ValidationMessage vm = vms[i];
      if (null == vm) {
        break;
      }
      this.messages[i] = new ValidationMessageV3().fillFromImpl(vm); // TODO: version
      if (vm.getMessageType() == Job.ValidationMessage.MessageType.ERROR) {
        this.error_count++;
      }
      i++;
    }

    return (S) this;
  }

  //==========================
  // Helper so Jobs can link to JobPoll
  public static String link(Key key) { return "/Jobs/"+key; }
}

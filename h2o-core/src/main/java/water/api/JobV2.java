package water.api;

import water.*;
import water.api.KeyV1.JobKeyV1;
import water.util.DocGen.HTML;
import water.util.PojoUtils;
import water.util.PrettyPrint;
import water.util.ReflectionUtils;

/** Schema for a single Job. */
public class JobV2<J extends Job, S extends JobV2<J, S>> extends Schema<J, S> {

  // Input fields
  @API(help="Job Key")
  public JobKeyV1 key;

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

  @API(help="runtime", direction=API.Direction.OUTPUT)
  public long msec;

  @API(help="destination key", direction=API.Direction.INOUT)
  public KeyV1 dest;

  @API(help="exception", direction=API.Direction.OUTPUT)
  public String exception;

  //==========================
  // Custom adapters go here

  // Version&Schema-specific filling into the impl
  @SuppressWarnings("unchecked")
  @Override public J createImpl( ) { return (J) new Job(key.key(), description); }

  // Version&Schema-specific filling from the impl
  @Override public S fillFromImpl(Job job) {
    // Handle fields in subclasses:
    PojoUtils.copyProperties(this, job, PojoUtils.FieldNaming.ORIGIN_HAS_UNDERSCORES);
    PojoUtils.copyProperties(this, job, PojoUtils.FieldNaming.CONSISTENT);  // TODO: make consistent and remove

    key = new JobKeyV1(job._key);
    description = job._description;
    progress = job.progress();
    progress_msg = job.progress_msg();
    status = job._state.toString();
    msec = (job.isStopped() ? job._end_time : System.currentTimeMillis())-job._start_time;
    Key dest_key = job.dest();
    Class<? extends Keyed> dest_class = ReflectionUtils.findActualClassParameter(job.getClass(), 0); // What type do we expect for this Job?
    dest = KeyV1.forKeyedClass(dest_class, dest_key);
    exception = job._exception;
    return (S) this;
  }

  //==========================
  // Helper so Jobs can link to JobPoll
  public static String link(Key key) { return "/Jobs/"+key; }

  @Override public HTML writeHTML_impl( HTML ab ) {
    ab.title("Job Poll");
    if( "DONE".equals(status) ) {
      Job job = (Job)Key.make(key.name).get();
      String url = InspectV1.link(job.dest());
      ab.href("Inspect",url,url).putStr("status",status).put4f("progress",progress);
    } else {
      String url = link(key.key());
      ab.href("JobPoll",url,url).putStr("status",status).put4f("progress",progress);
  }
    return ab.putStr("msec",PrettyPrint.msecs(msec,false)).putStr("exception",exception);
  }
}

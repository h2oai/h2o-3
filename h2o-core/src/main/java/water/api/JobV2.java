package water.api;

import water.*;
import water.api.KeyV1.JobKeyV1;
import water.util.DocGen.HTML;
import water.util.PrettyPrint;

/** Schema for a single Job. */
public class JobV2<J extends Schema> extends Schema<Job, JobV2<J>> {

  // Input fields
  @API(help="Job Key",required=true)
  public JobKeyV1 key;

  @API(help="Job description")
  public String description;

  // Output fields
  @API(help="job status", direction=API.Direction.OUTPUT)
  public String status;

  @API(help="progress, from 0 to 1", direction=API.Direction.OUTPUT)
  public float progress;               // A number from 0 to 1

  @API(help="runtime", direction=API.Direction.OUTPUT)
  public long msec;

  @API(help="destination key", direction=API.Direction.INOUT)
  public KeySchema dest;

  @API(help="exception", direction=API.Direction.OUTPUT)
  public String exception;

  public JobV2() {}
  public JobV2(Key<Job> key, String description, String status, float progress, long msec, Key dest, String exception) {
    this.key = new JobKeyV1(key);
    this.description = description;
    this.status = status;
    this.progress = progress;
    this.msec = msec;
    this.dest = KeySchema.make(dest);
    this.exception = exception;
  }

  //==========================
  // Custom adapters go here

  // Version&Schema-specific filling into the impl
  @Override public Job createImpl( ) { return new Job(key.key(), description); }

  // Version&Schema-specific filling from the impl
  @Override public JobV2 fillFromImpl(Job job) {
    // Fetch the latest Job status from the K/V store
    // Do this in the handler:
    // Job job = DKV.get(j._key).get();
    key = new JobKeyV1(job._key);
    progress = job.progress();
    status = job._state.toString();
    msec = (job.isStopped() ? job._end_time : System.currentTimeMillis())-job._start_time;
    dest = KeySchema.make(job.dest());
    exception = job._exception;
    return this;
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

package water.api;

import water.*;
import water.util.DocGen.HTML;
import water.util.PrettyPrint;

/** Schema for a single Job */
public class JobV2 extends Schema<Job, JobV2> {

  // Input fields
  @API(help="Job Key",required=true)
  Key key;

  @API(help="Job description")
  String description;

  // Output fields
  @API(help="job status", direction=API.Direction.OUTPUT)
  String status;

  @API(help="progress", direction=API.Direction.OUTPUT)
  float progress;               // A number from 0 to 1

  @API(help="runtime", direction=API.Direction.OUTPUT)
  long msec;

  @API(help="destination key", direction=API.Direction.OUTPUT)
  Key dest;

  @API(help="exception", direction=API.Direction.OUTPUT)
  String exception;

  JobV2() {}
  JobV2(Key key, String description, String status, float progress, long msec, Key dest, String exception) {
    this.key = key;
    this.description = description;
    this.status = status;
    this.progress = progress;
    this.msec = msec;
    this.dest = dest;
    this.exception = exception;
  }

  //==========================
  // Custom adapters go here

  // Version&Schema-specific filling into the impl
  @Override public Job createImpl( ) {
    Job j = new Job(key, description);
    return j;
  }

  // Version&Schema-specific filling from the impl
  @Override public JobV2 fillFromImpl(Job job) {
    // Fetch the latest Job status from the K/V store
    // Do this in the handler:
    // Job job = DKV.get(j._key).get();
    progress = job.progress();
    status = job._state.toString();
    msec = (job.isStopped() ? job._end_time : System.currentTimeMillis())-job._start_time;
    dest = job.dest();
    exception = job._exception;
    return this;
  }

  //==========================
  // Helper so Jobs can link to JobPoll
  public static String link(Key key) { return "/Jobs/"+key; }

  @Override public HTML writeHTML_impl( HTML ab ) {
    ab.title("Job Poll");
    if( "DONE".equals(status) ) {
      Job job = key.get();
      String url = InspectV1.link(job.dest());
      ab.href("Inspect",url,url).putStr("status",status).put4f("progress",progress);
    } else {
      String url = link(key);
      ab.href("JobPoll",url,url).putStr("status",status).put4f("progress",progress);
  }
    return ab.putStr("msec",PrettyPrint.msecs(msec,false)).putStr("exception",exception);
  }
}

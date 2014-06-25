package water.api;

import water.*;
import water.util.DocGen.HTML;
import water.util.PrettyPrint;

public class JobPollV2 extends Schema<JobPollHandler,JobPollV2> {

  // Input fields
  @API(help="Job Key",required=true)
  Key key;

  // Output fields
  @API(help="job status")
  String status;

  @API(help="progress")
  float progress;               // A number from 0 to 1

  @API(help="runtime")
  long msec;

  @API(help="destination key")
  Key dest;

  @API(help="exception")
  String exception;

  JobPollV2( ) {}
  JobPollV2( Key key, String status, float progress, long msec, Key dest, String exception ) {
    this.key = key;
    this.status = status;
    this.progress = progress;
    this.msec = msec;
    this.dest = dest;
    this.exception = exception;
  }

  //==========================
  // Customer adapters Go Here

  // Version&Schema-specific filling into the handler
  @Override protected JobPollV2 fillInto( JobPollHandler h ) {
    assert key != null;         // checked by required-field parsing
    Value val = DKV.get(key);
    if( val==null ) throw new IllegalArgumentException("Job is missing");
    Iced ice = val.get();
    if( !(ice instanceof Job) ) throw new IllegalArgumentException("Must be a Job not a "+ice.getClass());
    h._jobkey = key;
    return this;
  }

  // Version&Schema-specific filling from the handler
  @Override protected JobPollV2 fillFrom( JobPollHandler h ) {
    // Fetch the latest Job status from the K/V store
    Job job = DKV.get(h._jobkey).get();
    progress = job.progress();
    status = job._state.toString();
    msec = (job.isStopped() ? job._end_time : System.currentTimeMillis())-job._start_time;
    dest = job.dest();
    exception = job._exception;
    return this;
  }

  //==========================
  // Helper so Jobs can link to JobPoll
  public static String link(Key key) { return "JobPoll?key="+key; }

  @Override public HTML writeHTML_impl( HTML ab ) {
    ab.title("Job Poll");
    if( "DONE".equals(status) ) {
      Job job = DKV.get(key).get();
      String url = InspectV2.link(job.dest());
      ab.href("Inspect",url,url).putStr("status",status).put4f("progress",progress);
    } else {
      String url = link(key);
      ab.href("JobPoll",url,url).putStr("status",status).put4f("progress",progress);
  }
    return ab.putStr("msec",PrettyPrint.msecs(msec,false)).putStr("exception",exception);
  }
}

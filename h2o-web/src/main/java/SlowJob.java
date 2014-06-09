
import jsr166y.CountedCompleter;
import water.*;
import water.api.Handler;

public class SlowJob extends Handler<SlowJob,SlowJobV2> {
  // Inputs
  public int _work;
  
  // Output
  public Slow _job;

  // Running all in exec2, no need for backgrounding on F/J threads
  public SlowJob( ) { super(null); }
  private SlowJob( SlowJob sj, int lo, int hi ) { super(sj); _job = sj._job; _lo=lo; _hi=hi; }
  transient int _lo, _hi;
  @Override public void compute2() {
    if( _hi-_lo >= 2 ) { // Multi-chunk case: just divide-and-conquer to 1 chunk
      final int mid = (_lo+_hi)>>>1; // Mid-point
      setPendingCount(1);     // One fork awaiting completion
      new SlowJob(this,mid,_hi).fork();
      new SlowJob(this,_lo,mid).compute2();
    } else {
      if( _hi > _lo ) {
        // do some work
        try { Thread.sleep(1000); }
        catch( InterruptedException ignore ) { }
        System.out.println("Work "+_lo+" "+_hi);
        _job.update(1);         // Report progress
      }
      tryComplete();
    }
  }
  @Override public void onCompletion( CountedCompleter cc ) {
    if( _lo==0 && _hi==_work ) _job.done();
  }

  protected void work() {
    _lo=0; _hi=_work;
    Key k = Key.make("TheOneTrueSlowJobDestKey");
    _job = new Slow(k,_work);
    _job.start(this);
    DKV.put(k, new Res(k));
  }

  @Override protected SlowJobV2 schema(int version) { return new SlowJobV2(); }

  // A Job, and Progress?
  public static class Slow extends Job<Res> {
    Slow(Key k, int work) { super(k,"Slow Job Description",work); }
  }

  // Jobs require a result Keyed; this is the result object
  private static class Res extends Keyed {
    Res(Key k) { super(k); }
  }
}

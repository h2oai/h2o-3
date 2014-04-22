package water.parser;

import jsr166y.CountedCompleter;
import java.util.HashSet;
import water.*;
import water.fvec.*;
import water.util.PrettyPrint;

public class ParseDataset2 extends Job<Frame> {
  final ParseProgressMonitor _progress;

  // Keys are limited to ByteVec Keys and Frames-of-1-ByteVec Keys
  public static Frame parse(Key okey, Key... keys) { return parse(okey,keys,true, 0/*guess header*/); }

  // Guess setup from inspecting the first Key only, then parse.
  // Suitable for e.g. testing setups, where the data is known to be sane.
  // NOT suitable for random user input!
  public static Frame parse(Key okey, Key[] keys, boolean delete_on_done, int checkHeader) {
    Key k = keys[0];
    byte[] bits = ZipUtil.getFirstUnzippedBytes(getByteVec(k));
    ParserSetup globalSetup = ParserSetup.guessSetup(bits, checkHeader);
    if( globalSetup._ncols == 0 ) throw new java.lang.IllegalArgumentException(globalSetup.toString());
    return forkParseDataset(okey, keys, globalSetup, delete_on_done).get();
  }

  // Allow both ByteVec keys and Frame-of-1-ByteVec
  private static ByteVec getByteVec(Key key) {
    Iced ice = DKV.get(key).get();
    return (ByteVec)(ice instanceof ByteVec ? ice : ((Frame)ice).vecs()[0]);
  }

  // Same parse, as a backgroundable Job
  public static ParseDataset2 forkParseDataset(final Key dest, final Key[] keys, final ParserSetup setup, boolean delete_on_done) {
    // Some quick sanity checks: no overwriting your input key, and a resource check.
    HashSet<String> conflictingNames = setup.checkDupColumnNames();
    for( String x : conflictingNames ) 
      throw new IllegalArgumentException("Found duplicate column name "+x);
    long sum=0;
    for( Key k : keys ) {
      if( dest.equals(k) )
        throw new IllegalArgumentException("Destination key "+dest+" must be different from all sources");
      sum += getByteVec(k).length(); // Sum of all input filesizes
    }
    long memsz = H2O.CLOUD.memsz();
    if( sum > memsz*4 )
      throw new IllegalArgumentException("Total input file size of "+PrettyPrint.bytes(sum)+" is much larger than total cluster memory of "+PrettyPrint.bytes(memsz)+", please use either a larger cluster or smaller data.");

    // Fire off the parse
    ParseDataset2 job = new ParseDataset2(dest, sum);
    new Frame(job.dest(),new String[0],new Vec[0]).delete_and_lock(job._key); // Write-Lock BEFORE returning
    for( Key k : keys ) Lockable.read_lock(k,job._key); // Read-Lock BEFORE returning
    ParserFJTask fjt = new ParserFJTask(job, keys, setup, delete_on_done); // Fire off background parse
    job.start(fjt);
    H2O.submitTask(fjt);
    return job;
  }
  // Setup a private background parse job
  private ParseDataset2(Key dest, long totalLen) {
    super(dest,"Parse");
    _progress = new ParseProgressMonitor(totalLen);
  }

  // -----------------------------
  // Class to track parsing progress
  static class ParseProgressMonitor extends Keyed implements ProgressMonitor {
    final long _total;
    private long _value;
    DException _ex;
    ParseProgressMonitor( long totalLen ) { super(Key.make((byte) 0, Key.JOB)); _total = totalLen; }
    @Override public void update( long len ) { throw H2O.unimpl(); }
    long progress() { return _value; }
  }

  // -------------------------------
  // Simple internal class doing background parsing, with trackable Job status
  public static class ParserFJTask extends water.H2O.H2OCountedCompleter {
    final ParseDataset2 _job;
    final Key[] _keys;
    final ParserSetup _setup;
    final boolean _delete_on_done;

    public ParserFJTask( ParseDataset2 job, Key[] keys, ParserSetup setup, boolean delete_on_done) {
      _job = job;
      _keys = keys;
      _setup = setup;
      _delete_on_done = delete_on_done;
    }
    @Override public void compute2() {
      parse_impl(_job, _keys, _setup, _delete_on_done);
      tryComplete();
    }

    @Override public boolean onExceptionalCompletion(Throwable ex, CountedCompleter caller){
      if(_job != null) _job.cancel(ex.toString());
      ex.printStackTrace();
      return true;
    }
  }

  // --------------------------------------------------------------------------
  // Top-level parser driver
  private static void parse_impl(ParseDataset2 job, Key [] fkeys, ParserSetup setup, boolean delete_on_done) {
    throw H2O.unimpl();
  }
}

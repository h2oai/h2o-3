package water.util;

import jsr166y.CountedCompleter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.util.Random;

import water.DKV;
import water.H2O;
import water.Job;
import water.Key;
import water.MRTask;
import water.MemoryManager;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NFSFileVec;
import water.fvec.Vec;
import water.parser.ParseDataset;
import water.parser.ParseSetup;

public class FrameUtils {


  /** Parse given file(s) into the form of single frame represented by the given key.
   *
   * @param okey  destination key for parsed frame
   * @param files  files to parse
   * @return a new frame
   */
  public static Frame parseFrame(Key okey, File ...files) throws IOException {
    if (files == null || files.length == 0) {
      throw new IllegalArgumentException("List of files is empty!");
    }
    for (File f : files) {
      if (!f.exists())
        throw new FileNotFoundException("File not found " + f);
    }
    // Create output key if it is not given
    if(okey == null) okey = Key.make(files[0].getName());
    Key[] inKeys = new Key[files.length];
    for (int i=0; i<files.length; i++) inKeys[i] =  NFSFileVec.make(files[i])._key;
    return ParseDataset.parse(okey, inKeys);
  }

  /** Parse given set of URIs and produce a frame's key representing output.
   *
   * @param okey key for ouput frame. Can be null
   * @param uris array of URI (file://, hdfs://, s3n://, s3a://, s3://, ...) to parse
   * @return a frame which is saved into DKV under okey
   * @throws IOException in case of parse error.
   */
  public static Frame parseFrame(Key okey, URI ...uris) throws IOException {
    return parseFrame(okey, null, uris);
  }

  public static Frame parseFrame(Key okey, ParseSetup parseSetup, URI ...uris) throws IOException {
    if (uris == null || uris.length == 0) {
      throw new IllegalArgumentException("List of uris is empty!");
    }
    if(okey == null) okey = Key.make(uris[0].toString());
    Key[] inKeys = new Key[uris.length];
    for (int i=0; i<uris.length; i++)  inKeys[i] = H2O.getPM().anyURIToKey(uris[i]);
    // Return result
    return parseSetup != null ? ParseDataset.parse(okey, inKeys, true, ParseSetup.guessSetup(inKeys, parseSetup))
                              : ParseDataset.parse(okey, inKeys);
  }

  public static ParseSetup guessParserSetup(ParseSetup userParserSetup, URI ...uris) throws IOException {
    Key[] inKeys = new Key[uris.length];
    for (int i=0; i<uris.length; i++)  inKeys[i] = H2O.getPM().anyURIToKey(uris[i]);

    return ParseSetup.guessSetup(inKeys, userParserSetup);
  }

  private static class Vec2ArryTsk extends MRTask<Vec2ArryTsk> {
    final int N;
    public double [] res;
    public Vec2ArryTsk(int N){this.N = N;}
    @Override public void setupLocal(){
      res = MemoryManager.malloc8d(N);
    }
    @Override public void map(Chunk c){
      final int off = (int)c.start();
      for(int i = 0; i < c._len; i = c.nextNZ(i))
        res[off+i] = c.atd(i);
    }
    @Override public void reduce(Vec2ArryTsk other){
      if(res != other.res) {
        for(int i = 0; i < res.length; ++i) {
          assert res[i] == 0 || other.res[i] == 0;
          res[i] += other.res[i]; // assuming only one nonzero
        }
      }
    }
  }
  public static double [] asDoubles(Vec v){
    if(v.length() > 100000) throw new IllegalArgumentException("Vec is too big to be extracted into array");
    return new Vec2ArryTsk((int)v.length()).doAll(v).res;
  }
  private static class Vec2IntArryTsk extends MRTask<Vec2IntArryTsk> {
    final int N;
    public int [] res;
    public Vec2IntArryTsk(int N){this.N = N;}
    @Override public void setupLocal(){
      res = MemoryManager.malloc4(N);
    }
    @Override public void map(Chunk c){
      final int off = (int)c.start();
      for(int i = 0; i < c._len; i = c.nextNZ(i))
        res[off+i] = (int)c.at8(i);
    }
    @Override public void reduce(Vec2IntArryTsk other){
      if(res != other.res) {
        for(int i = 0; i < res.length; ++i) {
          assert res[i] == 0 || other.res[i] == 0;
          res[i] += other.res[i]; // assuming only one nonzero
        }
      }
    }
  }
  public static int [] asInts(Vec v){
    if(v.length() > 100000) throw new IllegalArgumentException("Vec is too big to be extracted into array");
    return new Vec2IntArryTsk((int)v.length()).doAll(v).res;
  }

  /**
   * Compute a chunk summary (how many chunks of each type, relative size, total size)
   * @param fr
   * @return chunk summary
   */
  public static ChunkSummary chunkSummary(Frame fr) {
    return new ChunkSummary().doAll(fr);
  }

  /** Generate given numbers of keys by suffixing key by given numbered suffix. */
  public static Key[] generateNumKeys(Key mk, int num) { return generateNumKeys(mk, num, "_part"); }
  public static Key[] generateNumKeys(Key mk, int num, String delim) {
    Key[] ks = new Key[num];
    String n = mk!=null ? mk.toString() : "noname";
    String suffix = "";
    if (n.endsWith(".hex")) {
      n = n.substring(0, n.length()-4); // be nice
      suffix = ".hex";
    }
    for (int i=0; i<num; i++) ks[i] = Key.make(n+delim+i+suffix);
    return ks;
  }

  public static double sparseRatio(Frame fr) {
    double reg = 1.0/fr.numCols();
    double res = 0;
    for(Vec v:fr.vecs())
      res += v.sparseRatio();
    return res * reg;
  }

  /**
   * Helper to insert missing values into a Frame
   */
  public static class MissingInserter extends Job<Frame> {
    final Key _dataset;
    final double _fraction;
    final long _seed;

    public MissingInserter(Key frame, long seed, double frac){
      super(frame, "MissingValueInserter");
      _dataset = frame; _seed = seed; _fraction = frac;
    }

    /**
     * Driver for MissingInserter
     */
    class MissingInserterDriver extends H2O.H2OCountedCompleter {
      final Frame _frame;
      MissingInserterDriver(Frame frame) {_frame = frame; }
      @Override
      protected void compute2() {
        new MRTask() {
          @Override public void map (Chunk[]cs){
            final Random rng = RandomUtils.getRNG(0);
            for (int c = 0; c < cs.length; c++) {
              for (int r = 0; r < cs[c]._len; r++) {
                rng.setSeed(_seed + 1234 * c ^ 1723 * (cs[c].start() + r));
                if (rng.nextDouble() < _fraction) cs[c].setNA(r);
              }
            }
            update(1);
          }
        }.doAll(_frame);
        tryComplete();
      }

      @Override
      public void onCompletion(CountedCompleter caller){
        done();
      }

      public boolean onExceptionalCompletion(Throwable ex, CountedCompleter cc) {
        failed(ex);
        return true;
      }
    }

    public void execImpl() {
      if (DKV.get(_dataset) == null)
        throw new IllegalArgumentException("Invalid Frame key " + _dataset + " (Frame doesn't exist).");
      if (_fraction < 0 || _fraction > 1 ) throw new IllegalArgumentException("fraction must be between 0 and 1.");
      try {
        final Frame frame = DKV.getGet(_dataset);
        MissingInserterDriver mid = new MissingInserterDriver(frame);
        int work = frame.vecs()[0].nChunks();
        start(mid, work, true);
      } catch (Throwable t) {
        Job thisJob = DKV.getGet(_key);
        if (thisJob._state == JobState.CANCELLED) {
          Log.info("Job cancelled by user.");
        } else {
          failed(t);
          throw t;
        }
      }
    }
  }

  /**
   * compute fraction of sparse chunks in this array.
   * @param chks
   * @return
   */
  public static double sparseRatio(Chunk [] chks) {
    int cnt = 0;
    double reg = 1.0/chks.length;
    for(Chunk c :chks)
      if(c.isSparse())
        ++cnt;
    return cnt * reg;
  }

  public static class WeightedMean extends MRTask<WeightedMean> {
    private double _wresponse;
    private double _wsum;
    public  double weightedMean() {
      return _wsum == 0 ? 0 : _wresponse / _wsum;
    }
    @Override public void map(Chunk response, Chunk weight, Chunk offset) {
      for (int i=0;i<response._len;++i) {
        if (response.isNA(i)) continue;
        double w = weight.atd(i);
        if (w == 0) continue;
        _wresponse += w*(response.atd(i)-offset.atd(i));
        _wsum += w;
      }
    }
    @Override public void reduce(WeightedMean mrt) {
      _wresponse += mrt._wresponse;
      _wsum += mrt._wsum;
    }
  }

}

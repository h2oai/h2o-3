package water.util;

import java.io.*;
import java.net.URI;
import java.util.Random;

import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NFSFileVec;
import water.parser.ParseDataset;

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

  public static Frame parseFrame(Key okey, URI ...uris) throws IOException {
    if (uris == null || uris.length == 0) {
      throw new IllegalArgumentException("List of uris is empty!");
    }
    Key[] inKeys = new Key[uris.length];
    for (int i=0; i<uris.length; i++)  inKeys[i] = H2O.getPM().anyURIToKey(uris[i]);
    if(okey == null) okey = Key.make(uris[0].toString());
    return ParseDataset.parse(okey, inKeys);
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

  /**
   * Helper to insert missing values into a Frame
   */
  public static class MissingInserter extends Job<MissingInserter> {
    final Frame _dataset;
    final double _fraction;
    final long _seed;

    public MissingInserter(Frame frame, long seed, double frac){
      super(null, null);
      _dataset = frame; _seed = seed; _fraction = frac;
    }

    class MI extends MRTask<MI> {
      long _seed;
      double _frac;
      MI(long seed, double frac) {
        _seed=seed;
        _frac=frac;
      }
      @Override public void map (Chunk[]cs){
        final Random rng = new Random();
        for (int c = 0; c < cs.length; c++) {
          for (int r = 0; r < cs[c]._len; r++) {
            rng.setSeed(_seed + 1234 * c ^ 1723 * (cs[c].start() + r));
            if (rng.nextDouble() < _frac) cs[c].setNA(r);
          }
        }
        update(1);
      }
    }
    public void execImpl() {
      if (_dataset == null) throw new IllegalArgumentException("Invalid dataset key (doesn't exist).");
      if (_fraction < 0 || _fraction > 1 ) throw new IllegalArgumentException("fraction must be between 0 and 1.");

      DKV.put(_progressKey = Key.make(), new Progress(_dataset.vecs()[0].nChunks()));
      new MI(_seed, _fraction).doAll(_dataset);
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
}

package water.util;

import java.io.*;
import java.net.URI;
import java.util.Random;

import water.*;
import water.fvec.*;
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
    @Override public void map(Chunks c){
      res = c.getDoubles(0,new double[c.numRows()]);
    }
    @Override public void reduce(Vec2ArryTsk other){
      res = ArrayUtils.append(res,other.res);
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

    @Override public void map(Chunks c){
      res = c.getIntegers(0,new int[c.numRows()],0);
    }
    @Override public void reduce(Vec2IntArryTsk other){
      res = ArrayUtils.append(res,other.res);
    }
  }

  public static int [] asInts(VecAry v){
    if(v.numRows() > 100000) throw new IllegalArgumentException("Vec is too big to be extracted into array");
    return new Vec2IntArryTsk((int)v.numRows()).doAll(v).res;
  }

  /**
   * Compute a chunk summary (how many chunks of each type, relative size, total size)
   * @param fr
   * @return chunk summary
   */
  public static ChunkSummary chunkSummary(Frame fr) {
    return new ChunkSummary().doAll(fr.vecs());
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
   * Helper to convert a categorical variable into a "binary" encoding format. In this format each categorical value is
   * first assigned an integer value, then that integer is written in binary, and each bit column is converted into a
   * separate column. This is intended as an improvement to an existing one-hot transformation.
   * For each categorical variable we assume that the number of categories is 1 + domain cardinality, the extra
   * category is reserved for NAs.
   * See http://www.willmcginnis.com/2015/11/29/beyond-one-hot-an-exploration-of-categorical-variables/
   */
  public static class CategoricalBinaryEncoder extends Iced {
    final Key<Frame> _frameKey;
    Job<Frame> _job;

    public CategoricalBinaryEncoder(Key<Frame> dataset) {
      _frameKey = dataset;
    }

    /**
     * Driver for CategoricalBinaryEncoder
     */
    class CategoricalBinaryEncoderDriver extends H2O.H2OCountedCompleter {
      final Frame _frame;
      final Key<Frame> _destKey;
      CategoricalBinaryEncoderDriver(Frame frame, Key<Frame> destKey) { _frame = frame; _destKey = destKey; }

      class BinaryConverter extends MRTask<BinaryConverter> {
        int[] _categorySizes;
        public BinaryConverter(int[] categorySizes) { _categorySizes = categorySizes; }

        @Override public void map(Chunks cs, Chunks.AppendableChunks ncs) {
          int targetColOffset = 0;
          for (int iCol = 0; iCol < cs.numCols(); ++iCol) {

            int numTargetColumns = _categorySizes[iCol];
            for (int iRow = 0; iRow < cs.numRows(); ++iRow) {
              long val = cs.isNA(iRow,iCol)? 0 : 1 + cs.at8(iRow,iCol);
              for (int j = 0; j < numTargetColumns; ++j) {
                ncs.addNum(targetColOffset + j,val & 1, 0);
                val >>>= 1;
              }
              assert val == 0 : "";
            }
            targetColOffset += numTargetColumns;
          }
        }
      }

      @Override public void compute2() {
        VecAry frameVecs = _frame.vecs();
        int [] cats = frameVecs.categoricals();
        int numCategoricals = cats.length;
        Frame categoricalFrame = new Frame(_frame._names.getNames(cats),_frame.vecs().getVecs(cats));
        Frame outputFrame = new Frame(_destKey, _frame);
        outputFrame.remove(cats);
        outputFrame = outputFrame.deepCopy();
        int[] binaryCategorySizes = new int[numCategoricals];
        int numOutputColumns = 0;
        VecAry cvecs = categoricalFrame.vecs();
        for (int i = 0, j = 0; i < cvecs.len(); ++i) {
          int numCategories = cvecs.cardinality(i); // Returns -1 if non-categorical variable
          if (numCategories > 0) {
            binaryCategorySizes[j] = 1 + MathUtils.log2(numCategories - 1 + 1/* for NAs */);
            numOutputColumns += binaryCategorySizes[j];
            ++j;
          }
        }
        BinaryConverter mrtask = new BinaryConverter(binaryCategorySizes);
        Frame binaryCols = mrtask.doAll(numOutputColumns, Vec.T_NUM, cvecs).outputFrame();
        // change names of binaryCols so that they reflect the original names of the categories
        String [] names = binaryCols._names.getNames();
        for (int i = 0, j = 0; i < binaryCategorySizes.length; j += binaryCategorySizes[i++]) {
          for (int k = 0; k < binaryCategorySizes[i]; ++k) {
            names[j + k] = categoricalFrame.name(i) + ":" + k;
          }
        }
        binaryCols._names = new Frame.Names(names);
        outputFrame.add(binaryCols);
        DKV.put(outputFrame);
        tryComplete();
      }
    }

    public Job<Frame> exec() {
      final Frame frame = DKV.getGet(_frameKey);
      if (frame == null)
        throw new IllegalArgumentException("Invalid Frame key " + _frameKey + " (Frame doesn't exist).");
      Key<Frame> destKey = Key.make();
      _job = new Job<>(destKey, Frame.class.getName(), "CategoricalBinaryEncoder");
      int workAmount = frame.vecs().nChunks();
      return _job.start(new CategoricalBinaryEncoderDriver(frame, destKey), workAmount);
    }
  }

  /**
   * Helper to insert missing values into a Frame
   */
  public static class MissingInserter extends Iced {
    Job<Frame> _job;
    final Key<Frame> _dataset;
    final double _fraction;
    final long _seed;

    public MissingInserter(Key<Frame> frame, long seed, double frac){
      _dataset = frame; _seed = seed; _fraction = frac;
    }

    /**
     * Driver for MissingInserter
     */
    class MissingInserterDriver extends H2O.H2OCountedCompleter {
      transient final Frame _frame;
      MissingInserterDriver(Frame frame) {_frame = frame; }
      @Override
      public void compute2() {
        new MRTask() {
          @Override public void map (Chunks cs){
            final Random rng = RandomUtils.getRNG(0);
            for (int c = 0; c < cs.numCols(); c++) {
              for (int r = 0; r < cs.numRows(); r++) {
                rng.setSeed(_seed + 1234 * c ^ 1723 * (cs.start() + r));
                if (rng.nextDouble() < _fraction) cs.setNA(r,c);
              }
            }
            _job.update(1);
          }
        }.doAll(_frame.vecs());
        tryComplete();
      }
    }

    public Job<Frame> execImpl() {
      _job = new Job<>(_dataset, Frame.class.getName(), "MissingValueInserter");
      if (DKV.get(_dataset) == null)
        throw new IllegalArgumentException("Invalid Frame key " + _dataset + " (Frame doesn't exist).");
      if (_fraction < 0 || _fraction > 1 ) throw new IllegalArgumentException("fraction must be between 0 and 1.");
      final Frame frame = DKV.getGet(_dataset);
      MissingInserterDriver mid = new MissingInserterDriver(frame);
      int work = frame.vecs().nChunks();
      return _job.start(mid, work);
    }
  }



  public static double sparseRatio(Frame fr) {
    double reg = 1.0/fr.numCols();
    double res = 0;
    VecAry vecs = fr.vecs();
    for(int i = 0; i < vecs.len(); ++i)
      res += vecs.sparseRatio(i);
    return res * reg;
  }


  public static class ExportTask extends H2O.H2OCountedCompleter<ExportTask> {
    final InputStream _csv;
    final String _path;
    final String _frameName;
    final boolean _overwrite;
    final Job _j;

    public ExportTask(InputStream csv, String path, String frameName, boolean overwrite, Job j) {
      _csv = csv;
      _path = path;
      _frameName = frameName;
      _overwrite = overwrite;
      _j = j;
    }

    private long copyStream(OutputStream os, final int buffer_size) {
      long len = 0;
      int curIdx = 0;
      try {
        byte[] bytes = new byte[buffer_size];
        for (; ; ) {
          int count = _csv.read(bytes, 0, buffer_size);
          if (count <= 0) {
            break;
          }
          len += count;
          os.write(bytes, 0, count);
          int workDone = ((Frame.CSVStream) _csv)._curChkIdx;
          if (curIdx != workDone) {
            _j.update(workDone - curIdx);
            curIdx = workDone;
          }
        }
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }
      return len;
    }

    @Override public void compute2() {
      OutputStream os = null;
      long written = -1;
      try {
        os = H2O.getPM().create(_path, _overwrite);
        written = copyStream(os, 4 * 1024 * 1024);
      } finally {
        if (os != null) {
          try {
            os.flush(); // Seems redundant, but seeing a short-file-read on windows sometimes
            os.close();
            Log.info("Key '" + _frameName + "' of "+written+" bytes was written to " + _path + ".");
          } catch (Exception e) {
            Log.err(e);
          }
        }
      }
      tryComplete();
    }
  }

}

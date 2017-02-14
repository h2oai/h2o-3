package water.util;

import hex.Model;
import hex.ToEigenVec;
import jsr166y.CountedCompleter;
import water.*;
import water.fvec.*;
import water.parser.ParseDataset;
import water.parser.ParseSetup;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class FrameUtils {

  public static String SUFFIX_FOR_NA = "missing(NA)";
  
  public static String compoundName(String first, String second) {
    return first + "." + second;
  }

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

  public static Frame categoricalEncoder(Frame dataset, String[] skipCols, Model.Parameters.CategoricalEncodingScheme scheme, ToEigenVec tev) {
    switch (scheme) {
      case AUTO:
      case Enum:
      case OneHotInternal:
        return dataset; //leave as is - most algos do their own internal default handling of enums
      case OneHotExplicit:
        return new CategoricalOneHotEncoder(dataset, skipCols).exec().get();
      case Binary:
        return new CategoricalBinaryEncoder(dataset, skipCols).exec().get();
      case Eigen:
        return new CategoricalEigenEncoder(tev, dataset, skipCols).exec().get();
      default:
        throw H2O.unimpl();
    }
  }

  public static void printTopCategoricalLevels(Frame fr, boolean warn, int topK) {
    String[][] domains = fr.domains();
    String[] names = fr.names();
    int len = domains.length;
    int[] levels = new int[len];
    for (int i = 0; i < len; ++i)
      levels[i] = domains[i] != null ? domains[i].length : 0;
    Arrays.sort(levels);
    if (levels[len - 1] > 0) {
      int levelcutoff = levels[len - 1 - Math.min(topK, len - 1)];
      int count = 0;
      for (int i = 0; i < len && count < topK; ++i) {
        if (domains[i] != null && domains[i].length >= levelcutoff) {
          if (warn)
            Log.warn("Categorical feature '" + names[i] + "' has cardinality " + domains[i].length + ".");
          else
            Log.info("Categorical feature '" + names[i] + "' has cardinality " + domains[i].length + ".");
        }
        count++;
      }
    }
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
    class Driver extends H2O.H2OCountedCompleter {
      transient final Frame _frame;
      Driver(Frame frame) {_frame = frame; }
      @Override
      public void compute2() {
        new MRTask() {
          @Override public void map (Chunk[]cs){
            final Random rng = RandomUtils.getRNG(0);
            for (int c = 0; c < cs.length; c++) {
              for (int r = 0; r < cs[c]._len; r++) {
                rng.setSeed(_seed + 1234 * c ^ 1723 * (cs[c].start() + r));
                if (rng.nextDouble() < _fraction) cs[c].setNA(r);
              }
            }
            _job.update(1);
          }
        }.doAll(_frame);
        tryComplete();
      }
    }

    public Job<Frame> execImpl() {
      _job = new Job<>(_dataset, Frame.class.getName(), "MissingValueInserter");
      if (DKV.get(_dataset) == null)
        throw new IllegalArgumentException("Invalid Frame key " + _dataset + " (Frame doesn't exist).");
      if (_fraction < 0 || _fraction > 1 ) throw new IllegalArgumentException("fraction must be between 0 and 1.");
      final Frame frame = DKV.getGet(_dataset);
      Driver mid = new Driver(frame);
      int work = frame.vecs()[0].nChunks();
      return _job.start(mid, work);
    }
  }

  /**
   * compute fraction of sparse chunks in this array.
   * @param chks
   * @return
   */
  public static double sparseRatio(Chunk [] chks) {
    double cnt = 0;
    double reg = 1.0/chks.length;
    for(Chunk c :chks)
      if(c.isSparseNA()){
        cnt += c.sparseLenNA()/(double)c.len();
      } else if(c.isSparseZero()){
        cnt += c.sparseLenZero()/(double)c.len();
      } else cnt += 1;
    return cnt * reg;
  }

  public static double sparseRatio(Frame fr) {
    double reg = 1.0/fr.numCols();
    double res = 0;
    for(Vec v:fr.vecs())
      res += v.sparseRatio();
    return res * reg;
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

  public static class ExportTaskDriver extends H2O.H2OCountedCompleter<ExportTaskDriver> {
    private static long DEFAULT_TARGET_PART_SIZE = 134217728L; // 128MB, default HDFS block size
    private static int AUTO_PARTS_MAX = 128; // maximum number of parts if automatic determination is enabled
    final Frame _frame;
    final String _path;
    final String _frameName;
    final boolean _overwrite;
    final Job _j;
    int _nParts;

    public ExportTaskDriver(Frame frame, String path, String frameName, boolean overwrite, Job j, int nParts) {
      _frame = frame;
      _path = path;
      _frameName = frameName;
      _overwrite = overwrite;
      _j = j;
      _nParts = nParts;
    }

    @Override
    public void compute2() {
      _frame.read_lock(_j._key);
      if (_nParts == 1) {
        // Single file export, the file should be created by the node that was asked to export the data
        // (this is for non-distributed filesystems, we want the file to go to the local filesystem of the node)
        Frame.CSVStream is = new Frame.CSVStream(_frame, true, false);
        exportCSVStream(is, _path, 0);
        tryComplete();
      } else {
        // Multi-part export
        if (_nParts < 0) {
          _nParts = calculateNParts();
          assert _nParts > 0;
        }
        int nChunksPerPart = ((_frame.anyVec().nChunks() - 1) / _nParts) + 1;
        new PartExportTask(this, _frame._names, nChunksPerPart).dfork(_frame);
      }
    }

    @Override
    public void onCompletion(CountedCompleter caller) {
      _frame.unlock(_j);
    }

    @Override
    public boolean onExceptionalCompletion(Throwable t, CountedCompleter caller) {
      _frame.unlock(_j);
      return super.onExceptionalCompletion(t, caller);
    }

    private int calculateNParts() {
      EstimateSizeTask estSize = new EstimateSizeTask().dfork(_frame).getResult();
      Log.debug("Estimator result: ", estSize);
      // the goal is to not to create too small part files (and too many files), ideal part file size is one HDFS block
      int nParts = Math.max((int) (estSize._size / DEFAULT_TARGET_PART_SIZE), H2O.CLOUD.size() + 1);
      if (nParts > AUTO_PARTS_MAX) {
        Log.debug("Recommended number of part files (" + nParts + ") exceeds maximum limit " + AUTO_PARTS_MAX + ". " +
                "Number of part files is limited to avoid slow downs when importing back to H2O."); // @tomk
        nParts = AUTO_PARTS_MAX;
      }
      Log.info("For file of estimated size " + estSize + "B determined number of parts: " + _nParts);
      return nParts;
    }

    /**
     * Trivial CSV file size estimator. Uses the first line of each non-empty chunk to estimate the size of the chunk.
     * The total estimated size is the total of the estimated chunk sizes.
     */
    class EstimateSizeTask extends MRTask<EstimateSizeTask> {
      // OUT
      int _nNonEmpty;
      long _size;

      @Override
      public void map(Chunk[] cs) {
        if (cs[0]._len == 0) return;
        Frame.CSVStream is = new Frame.CSVStream(cs, null, 1, false);
        try {
          _nNonEmpty++;
          _size += is.getCurrentRowSize() * cs[0]._len;
        } catch (IOException e) {
          throw new RuntimeException(e);
        } finally {
          try { is.close(); } catch (Exception e) { Log.err(e); }
        }
      }

      @Override
      public void reduce(EstimateSizeTask mrt) {
        _nNonEmpty += mrt._nNonEmpty;
        _size += mrt._size;
      }

      @Override
      public String toString() {
        return "EstimateSizeTask{_nNonEmpty=" + _nNonEmpty + ", _size=" + _size + '}';
      }
    }

    private long copyCSVStream(Frame.CSVStream is, OutputStream os, int firstChkIdx, int buffer_size) throws IOException {
      long len = 0;
      byte[] bytes = new byte[buffer_size];
      int curChkIdx = firstChkIdx;
      for (;;) {
        int count = is.read(bytes, 0, buffer_size);
        if (count <= 0) {
          break;
        }
        len += count;
        os.write(bytes, 0, count);
        int workDone = is._curChkIdx - curChkIdx;
        if (workDone > 0) {
          if (_j.stop_requested()) throw new Job.JobCancelledException();
          _j.update(workDone);
          curChkIdx = is._curChkIdx;
        }
      }
      return len;
    }

    private void exportCSVStream(Frame.CSVStream is, String path, int firstChkIdx) {
      OutputStream os = null;
      long written = -1;
      try {
        os = H2O.getPM().create(path, _overwrite);
        written = copyCSVStream(is, os, firstChkIdx, 4 * 1024 * 1024);
      } catch (IOException e) {
        throw new RuntimeException(e);
      } finally {
        if (os != null) {
          try {
            os.flush(); // Seems redundant, but seeing a short-file-read on windows sometimes
            os.close();
            Log.info("Written " + written + " bytes of key '" + _frameName + "' to " + _path + ".");
          } catch (Exception e) {
            Log.err(e);
          }
        }
        try { is.close(); } catch (Exception e) { Log.err(e); }
      }
    }

    class PartExportTask extends MRTask<PartExportTask> {
      final String[] _colNames;
      final int _length;

      PartExportTask(H2O.H2OCountedCompleter<?> completer, String[] colNames, int length) {
        super(completer);
        _colNames = colNames;
        _length = length;
      }

      @Override
      public void map(Chunk[] cs) {
        Chunk anyChunk = cs[0];
        if (anyChunk.cidx() % _length > 0) {
          return;
        }
        int partIdx = anyChunk.cidx() / _length;
        String partPath = _path + "/part-m-" + String.valueOf(100000 + partIdx).substring(1);
        Frame.CSVStream is = new Frame.CSVStream(cs, _colNames, _length, false);
        exportCSVStream(is, partPath, anyChunk.cidx());
      }

      @Override
      protected void setupLocal() {
        boolean created = H2O.getPM().mkdirs(_path);
        if (! created) Log.warn("Path ", _path, " was not created.");
      }
    }
  }

  public static class CategoricalOneHotEncoder extends CategoricalEncoder {

    public CategoricalOneHotEncoder(Frame dataset, String[] skipCols) {
      super(dataset, skipCols);
    }

    @Override
    H2O.H2OCountedCompleter driver(Key<Frame> destKey) {
      return new Driver(destKey);
    }

    /**
     * Driver for CategoricalOneHotEncoder
     */
    class Driver extends H2O.H2OCountedCompleter {
      final Key<Frame> _destKey;
      Driver(Key<Frame> destKey) { 
        _destKey = destKey;
      }

      class OneHotConverter extends MRTask<OneHotConverter> {
        int[] _categorySizes;
        public OneHotConverter(int[] categorySizes) { _categorySizes = categorySizes; }

        @Override public void map(Chunk[] cs, NewChunk[] ncs) {
          int targetColOffset = 0;
          for (int iCol = 0; iCol < cs.length; ++iCol) {
            Chunk col = cs[iCol];
            int numTargetColumns = _categorySizes[iCol];
            for (int iRow = 0; iRow < col._len; ++iRow) {
              long val = col.isNA(iRow)? numTargetColumns-1 : col.at8(iRow);
              for (int j = 0; j < numTargetColumns; ++j) {
                ncs[targetColOffset + j].addNum(val==j ? 1 : 0, 0);
              }
            }
            targetColOffset += numTargetColumns;
          }
        }
      }

      @Override public void compute2() {
        Vec[] frameVecs = _frame.vecs();
        int numCategoricals = numCategoricals();

        Frame categoricalFrame = new Frame();
        Frame outputFrame = new Frame(_destKey);
        int[] categorySizes = new int[numCategoricals];
        int numOutputColumns = 0;
        List<String> catnames= new ArrayList<>();
        
        for (int i = 0, j = 0; i < frameVecs.length; ++i) {
          if (skipThisColumn(i)) continue;
          int numCategories = frameVecs[i].cardinality(); // Returns -1 if non-categorical variable
          if (numCategories > 0) {
            categoricalFrame.add(_frame.name(i), frameVecs[i]);
            categorySizes[j] = numCategories + 1/* for NAs */;
            numOutputColumns += categorySizes[j];
            
            // adding the regular category names
            for (int k=0;k<categorySizes[j]-1;++k)
              catnames.add(compoundName(_frame.name(i), _frame.vec(i).domain()[k]));
            
            // in the end adding an extra column for missing data
            catnames.add(compoundName(_frame.name(i), SUFFIX_FOR_NA));
            ++j;
          } else {
            outputFrame.add(_frame.name(i), frameVecs[i].makeCopy());
          }
        }
        OneHotConverter mrtask = new OneHotConverter(categorySizes);
        Frame binaryCols = mrtask.doAll(numOutputColumns, Vec.T_NUM, categoricalFrame).outputFrame();
        binaryCols._names = catnames.toArray(new String[0]);
        outputFrame.add(binaryCols);

        addSkippedColumns(outputFrame);
        DKV.put(outputFrame);
        tryComplete();
      }
    }
  }


  /**
   * Helper to convert a categorical variable into a "binary" encoding format. In this format each categorical value is
   * first assigned an integer value, then that integer is written in binary, and each bit column is converted into a
   * separate column. This is intended as an improvement to an existing one-hot transformation.
   * For each categorical variable we assume that the number of categories is 1 + domain cardinality, the extra
   * category is reserved for NAs.
   * See http://www.willmcginnis.com/2015/11/29/beyond-one-hot-an-exploration-of-categorical-variables/
   */
  public static class CategoricalBinaryEncoder extends CategoricalEncoder {

    public CategoricalBinaryEncoder(Frame dataset, String[] skipCols) {
      super(dataset, skipCols);
    }

    @Override
    H2O.H2OCountedCompleter driver(Key<Frame> destKey) {
      return new Driver(destKey);
    }

    /**
     * Driver for CategoricalBinaryEncoder
     */
    class Driver extends H2O.H2OCountedCompleter {
      final Key<Frame> _destKey;
      Driver(Key<Frame> destKey) { _destKey = destKey; }

      class BinaryConverter extends MRTask<BinaryConverter> {
        int[] _categorySizes;
        public BinaryConverter(int[] categorySizes) { _categorySizes = categorySizes; }

        @Override public void map(Chunk[] cs, NewChunk[] ncs) {
          int targetColOffset = 0;
          for (int iCol = 0; iCol < cs.length; ++iCol) {
            Chunk col = cs[iCol];
            int numTargetColumns = _categorySizes[iCol];
            for (int iRow = 0; iRow < col._len; ++iRow) {
              long val = col.isNA(iRow)? 0 : 1 + col.at8(iRow);
              for (int j = 0; j < numTargetColumns; ++j) {
                ncs[targetColOffset + j].addNum(val & 1, 0);
                val >>>= 1;
              }
              assert val == 0 : "";
            }
            targetColOffset += numTargetColumns;
          }
        }
      }

      @Override public void compute2() {
        Vec[] frameVecs = _frame.vecs();
        int numCategoricals = numCategoricals();

        Frame categoricalFrame = new Frame();
        Frame outputFrame = new Frame(_destKey);
        int[] binaryCategorySizes = new int[numCategoricals];
        int numOutputColumns = 0;
        for (int i = 0, j = 0; i < frameVecs.length; ++i) {
          if (skipThisColumn(i)) continue;
          int numCategories = frameVecs[i].cardinality(); // Returns -1 if non-categorical variable
          if (numCategories > 0) {
            categoricalFrame.add(_frame.name(i), frameVecs[i]);
            binaryCategorySizes[j] = 1 + MathUtils.log2(numCategories - 1 + 1/* for NAs */);
            numOutputColumns += binaryCategorySizes[j];
            ++j;
          } else
            outputFrame.add(_frame.name(i), frameVecs[i].makeCopy());
        }
        BinaryConverter mrtask = new BinaryConverter(binaryCategorySizes);
        Frame binaryCols = mrtask.doAll(numOutputColumns, Vec.T_NUM, categoricalFrame).outputFrame();
        // change names of binaryCols so that they reflect the original names of the categories
        for (int i = 0, j = 0; i < binaryCategorySizes.length; j += binaryCategorySizes[i++]) {
          for (int k = 0; k < binaryCategorySizes[i]; ++k) {
            binaryCols._names[j + k] = categoricalFrame.name(i) + ":" + k;
          }
        }
        outputFrame.add(binaryCols);

        addSkippedColumns(outputFrame);
        DKV.put(outputFrame);
        tryComplete();
      }
    }

  }

  static abstract class CategoricalEncoder extends Iced {
    Job<Frame> _job;

    Frame _frame;
    String[] _skipCols;
    CategoricalEncoder(Frame frame, String[] skipCols) {
      _frame = frame; 
      _skipCols = skipCols;
      if (_frame == null)
        throw new IllegalArgumentException("Frame doesn't exist.");
    }
    
    int numCategoricals() {
      Vec[] frameVecs = _frame.vecs();
      int count = 0;
      for (int i=0;i<frameVecs.length;++i)
        if (frameVecs[i].isCategorical() && !skipThisColumn(i))
          count++;
      
      return count;
    }
    
    boolean skipThisColumn(int i) {
      return _skipCols != null && 
          ArrayUtils.find(_skipCols, _frame._names[i]) >= 0;
    }

    void addSkippedColumns(Frame outputFrame) {
      if (_skipCols != null) {
        for (String name : _skipCols) {
          Vec skippedVec = _frame.vec(name);
          if (skippedVec != null)
            outputFrame.add(name, skippedVec.makeCopy());
        }
      }
    }
    
    abstract H2O.H2OCountedCompleter driver(Key<Frame> destKey);

    public Job<Frame> exec() {
      Key<Frame> destKey = Key.makeSystem(Key.make().toString());
      _job = new Job<>(destKey, Frame.class.getName(), getClass().getSimpleName());
      int workAmount = _frame.lastVec().nChunks();
      return _job.start(driver(destKey), workAmount);
    }
  }
  
  /**
   * Helper to convert a categorical variable into the first eigenvector of the dummy-expanded matrix.
   */
  public static class CategoricalEigenEncoder extends CategoricalEncoder {
    final ToEigenVec _tev;

    public CategoricalEigenEncoder(ToEigenVec tev, Frame dataset, String[] skipCols) {
      super(dataset, skipCols);
      _tev = tev;
      assert _tev!=null : "Override toEigenVec for this Algo!";
    }

    H2O.H2OCountedCompleter driver(Key<Frame> destKey) {
      return new Driver(destKey); 
    }

    /**
     * Driver for CategoricalEigenEncoder
     */
    class Driver extends H2O.H2OCountedCompleter {
      final Key<Frame> _destKey;
      Driver(Key<Frame> destKey) {
        _destKey = destKey;
      }

      @Override public void compute2() {
        Vec[] frameVecs = _frame.vecs();
        Frame outputFrame = new Frame(_destKey);
        for (int i = 0; i < frameVecs.length; ++i) {
          if (skipThisColumn(i)) continue;
          if (frameVecs[i].isCategorical())
            outputFrame.add(_frame.name(i) + ".Eigen", _tev.toEigenVec(frameVecs[i]));
          else
            outputFrame.add(_frame.name(i), frameVecs[i].makeCopy());
        }
        addSkippedColumns(outputFrame);
        DKV.put(outputFrame);
        tryComplete();
      }
    }
  }

  static public void cleanUp(IcedHashMap<Key, String> toDelete) {
    Futures fs = new Futures();
    for (Key k : toDelete.keySet()) {
      k.remove(fs);
    }
    fs.blockForPending();
    toDelete.clear();
  }


  /**
   * reduce the domains of all categorical columns to the actually observed subset
   * @param frameToModifyInPlace
   */
  static public void shrinkDomainsToObservedSubset(Frame frameToModifyInPlace) {
    for (Vec v : frameToModifyInPlace.vecs()) {
      if (v.isCategorical()) {
        long[] uniques = (v.min() >= 0 && v.max() < Integer.MAX_VALUE - 4) ? new VecUtils.CollectDomainFast((int)v.max()).doAll(v).domain() : new VecUtils.CollectDomain().doAll(v).domain();
        String[] newDomain = new String[uniques.length];
        final int[] fromTo = new int[(int)ArrayUtils.maxValue(uniques)+1];
        for (int i=0;i<newDomain.length;++i) {
          newDomain[i] = v.domain()[(int) uniques[i]];
          fromTo[(int)uniques[i]] = i; //helper for value mapping
        }
        new MRTask() {
          @Override
          public void map(Chunk c) {
            for (int i=0;i<c._len;++i) {
              if (!c.isNA(i)) c.set(i, fromTo[(int)c.at8(i)]);
            }
          }
        }.doAll(v);
        v.setDomain(newDomain);
      }
    }
  }

}

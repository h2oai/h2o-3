package water.util;

import hex.Interaction;
import hex.Model;
import hex.ToEigenVec;
import jsr166y.CountedCompleter;
import org.apache.commons.io.IOUtils;
import water.*;
import water.fvec.*;
import water.parser.BufferedString;
import water.parser.ParseDataset;
import water.parser.ParseSetup;
import water.persist.Persist;
import water.persist.PersistManager;

import java.io.*;
import java.net.URI;
import java.net.URL;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class FrameUtils {

  public static final int MAX_VEC_NUM_ROWS_FOR_ARRAY_EXPORT = 100_000;

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
   * @param uris array of URI (file://, hdfs://, s3n://, s3a://, s3://, http://, https:// ...) to parse
   * @return a frame which is saved into DKV under okey
   * @throws IOException in case of parse error.
   */
  public static Frame parseFrame(Key okey, URI ...uris) throws IOException {
    return parseFrame(okey, null, uris);
  }

  public static Key eagerLoadFromHTTP(String path) throws IOException {
    return eagerLoadFromURL(path, new URL(path));
  }

  public static Key<?> eagerLoadFromURL(String sourceId, URL url) throws IOException {
    try (InputStream is = url.openStream()) {
      return eagerLoadFromInputStream(sourceId, is);
    }
  }

  private static Key<?> eagerLoadFromInputStream(String sourceId, InputStream is) throws IOException {
    Key<?> destination_key = Key.make(sourceId);
    UploadFileVec.ReadPutStats stats = new UploadFileVec.ReadPutStats();
    UploadFileVec.readPut(destination_key, is, stats);
    return destination_key;
  }

  public static Frame parseFrame(Key okey, ParseSetup parseSetup, URI ...uris) throws IOException {
    if (uris == null || uris.length == 0) {
      throw new IllegalArgumentException("List of uris is empty!");
    }
    if(okey == null) okey = Key.make(uris[0].toString());
    Key[] inKeys = new Key[uris.length];
    for (int i = 0; i < uris.length; i++){
      if ("http".equals(uris[i].getScheme()) || "https".equals(uris[i].getScheme())) {
        inKeys[i] = eagerLoadFromHTTP(uris[i].toString());
      } else{
        inKeys[i] = H2O.getPM().anyURIToKey(uris[i]);
      }
    }
    // Return result
    return parseSetup != null ? ParseDataset.parse(okey, inKeys, true, ParseSetup.guessSetup(inKeys, parseSetup))
            : ParseDataset.parse(okey, inKeys);
  }

  public static Frame categoricalEncoder(Frame dataset, String[] skipCols, Model.Parameters.CategoricalEncodingScheme scheme, ToEigenVec tev, int maxLevels) {
    switch (scheme) {
      case AUTO:
      case Enum:
      case SortByResponse: //the work is done in ModelBuilder - the domain is all we need to change once, adaptTestTrain takes care of test set adaptation
      case OneHotInternal:
        return dataset; //leave as is - most algos do their own internal default handling of enums
      case OneHotExplicit:
        return new CategoricalOneHotEncoder(dataset, skipCols).exec().get();
      case Binary:
        return new CategoricalBinaryEncoder(dataset, skipCols).exec().get();
      case EnumLimited:
        return new CategoricalEnumLimitedEncoder(maxLevels, dataset, skipCols).exec().get();
      case Eigen:
        return new CategoricalEigenEncoder(tev, dataset, skipCols).exec().get();
      case LabelEncoder:
        return new CategoricalLabelEncoder(dataset, skipCols).exec().get();
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

  public static class Vec2ArryTsk extends MRTask<Vec2ArryTsk> {
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

  public static class Vecs2ArryTsk extends MRTask<Vecs2ArryTsk> {
    final int dim1;   // treat as row
    final int dim2;   // treat as column
    public double [][] res;
    public Vecs2ArryTsk(int dim1, int dim2)
    {
      this.dim1 = dim1;
      this.dim2 = dim2;
    }

    @Override public void setupLocal(){
      res = MemoryManager.malloc8d(dim1, dim2);
    }
    @Override public void map(Chunk[] c){
      final int off = (int)c[0].start();
      for (int colIndex = 0; colIndex < dim2; colIndex++) {
        for (int rowIndex = 0; rowIndex < dim1; rowIndex++) {
          res[off+rowIndex][colIndex] = c[colIndex].atd(rowIndex);
        }
      }
    }

    @Override public void reduce(Vecs2ArryTsk other){
      ArrayUtils.add(res, other.res);
    }
  }

  public static double [] asDoubles(Vec v){
    return new Vec2ArryTsk((int)v.length()).doAll(v).res;
  }

  public static double [][] asDoubles(Frame frame){
    if (frame.numRows() > MAX_VEC_NUM_ROWS_FOR_ARRAY_EXPORT)
      throw new IllegalArgumentException("Frame is too big to be extracted into array");

    double [][] frameArray = new double[frame.numCols()][];
    for (int i = 0; i < frame.numCols(); i++) {
      Vec v = frame.vec(i);
      frameArray[i] = new Vec2ArryTsk((int)v.length()).doAll(v).res;
    }

    return frameArray;
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
    class MissingInserterDriver extends H2O.H2OCountedCompleter {
      transient final Frame _frame;
      MissingInserterDriver(Frame frame) {_frame = frame; }
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
      MissingInserterDriver mid = new MissingInserterDriver(frame);
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
    private static int BUFFER_SIZE = 8 * 1024 * 1024;
    private static long DEFAULT_TARGET_PART_SIZE = 134217728L; // 128MB, default HDFS block size
    private static int AUTO_PARTS_MAX = 128; // maximum number of parts if automatic determination is enabled
    final Frame _frame;
    final String _path;
    final String _frameName;
    final boolean _overwrite;
    final Job _j;
    int _nParts;
    boolean _parallel;
    final CompressionFactory _compressor;
    final Frame.CSVStreamParams _csv_parms;

    public ExportTaskDriver(Frame frame, String path, String frameName, boolean overwrite, Job j, 
                            int nParts, boolean perChunk,
                            CompressionFactory compressor, Frame.CSVStreamParams csvParms) {
      _frame = frame;
      _path = path;
      _frameName = frameName;
      _overwrite = overwrite;
      _j = j;
      _nParts = nParts;
      _parallel = perChunk;
      _compressor = compressor;
      _csv_parms = csvParms;
    }

    @Override
    public void compute2() {
      _frame.read_lock(_j._key);
      if (_parallel && _nParts == 1) {
        _nParts = _frame.anyVec().nChunks();
        int processed = 0;
        final String compression = H2O.getSysProperty("export.csv.cache.compression", "none");
        final CompressionFactory compressor = CompressionFactory.make(compression);
        final DecompressionFactory decompressor = DecompressionFactory.make(compression);
        final String cacheStorage = H2O.getSysProperty("export.csv.cache.storage", "memory");
        final CsvChunkCache cache = "memory".equals(cacheStorage) ? new DkvCsvChunkCache() : new FileSystemCsvChunkCache();
        Log.info("Using compression=`" + compressor.getName() + 
                "` and cache=`" + cache.getName() + "` for interim partial CSV export files.");
        final ChunkExportTask chunkExportTask = cache.makeExportTask(_frame, _csv_parms, compressor);
        H2O.submitTask(new LocalMR(chunkExportTask, H2O.NUMCPUS));
        try (FileOutputStream os = new FileOutputStream(_path)) {
          byte[] buffer = new byte[BUFFER_SIZE];
          final boolean[] isChunkCompleted = new boolean[_nParts + 1];
          while (processed != _nParts) {
            final int cid = chunkExportTask._completed.take();
            isChunkCompleted[cid] = true;
            while (isChunkCompleted[processed]) {
              try (final InputStream rawInputStream = cache.getChunkCsvStream(chunkExportTask, processed);
                   final InputStream is = decompressor.wrapInputStream(rawInputStream)) {
                IOUtils.copyLarge(is, os, buffer);
              } finally {
                cache.releaseCache(chunkExportTask, processed);
              }
              processed++;
              _j.update(1);
            }
          }
        } catch (IOException e) {
          throw new RuntimeException("File export failed", e);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new RuntimeException("File export interrupted", e);
        }
        tryComplete();
      } else if (_nParts == 1) {
        // Single file export, the file should be created by the node that was asked to export the data
        // (this is for non-distributed filesystems, we want the file to go to the local filesystem of the node)
        final Frame.CSVStream is = new Frame.CSVStream(_frame, _csv_parms);
        exportCSVStream(is, _path, 0);
        tryComplete();
      } else {
        // Multi-part export
        if (_nParts < 0) {
          _nParts = calculateNParts(_csv_parms);
          assert _nParts > 0;
        }
        final int nChunksPerPart = ((_frame.anyVec().nChunks() - 1) / _nParts) + 1;
        new PartExportTask(this, _frame._names, nChunksPerPart, _csv_parms).dfork(_frame);
      }
    }

    private interface CsvChunkCache {
      String getName();
      ChunkExportTask makeExportTask(Frame f, Frame.CSVStreamParams csvParams, CompressionFactory compressor);
      InputStream getChunkCsvStream(ChunkExportTask task, int cid) throws IOException;
      void releaseCache(ChunkExportTask task, int cid);
    } 
    
    private class FileSystemCsvChunkCache implements CsvChunkCache {

      @Override
      public String getName() {
        return "FileSystem";
      }

      @Override
      public ChunkExportTask makeExportTask(Frame f, Frame.CSVStreamParams csvParams, CompressionFactory compressor) {
        return new ChunkExportTask(f, f._names, csvParams, compressor);
      }

      @Override
      public InputStream getChunkCsvStream(ChunkExportTask task, int cid) throws IOException {
        File chunkFile = new File(task.getChunkPath(cid));
        return new FileInputStream(chunkFile);
      }

      @Override
      public void releaseCache(ChunkExportTask task, int cid) {
        File chunkFile = new File(task.getChunkPath(cid));
        if (! chunkFile.delete()) {
          Log.warn("Temporary file " + chunkFile.getAbsoluteFile() + " couldn't be deleted.");
        }
      }
    }
    
    private class DkvCsvChunkCache implements CsvChunkCache {
      private final Key<?> _vecKey;

      public DkvCsvChunkCache() {
        _vecKey = Vec.newKey();
      }

      @Override
      public String getName() {
        return "DKV";
      }

      @Override
      public ChunkExportTask makeExportTask(Frame f, Frame.CSVStreamParams csvParams, CompressionFactory compressor) {
        return new InMemoryChunkExportTask(_vecKey, f, f._names, csvParams, compressor);
      }

      @Override
      public InputStream getChunkCsvStream(ChunkExportTask task, int cid) {
        Key<?> ck = Vec.chunkKey(_vecKey, cid);
        PersistManager pm = H2O.getPM();
        return pm.open(pm.toHexPath(ck));
      }

      @Override
      public void releaseCache(ChunkExportTask task, int cid) {
        Key<?> ck = Vec.chunkKey(_vecKey, cid);
        DKV.remove(ck);
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

    private int calculateNParts(Frame.CSVStreamParams parms) {
      EstimateSizeTask estSize = new EstimateSizeTask(parms).dfork(_frame).getResult();
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
    static class EstimateSizeTask extends MRTask<EstimateSizeTask> {
      // IN
      private final Frame.CSVStreamParams _parms;
      // OUT
      int _nNonEmpty;
      long _size;

      public EstimateSizeTask(Frame.CSVStreamParams parms) {
        _parms = parms;
      }

      @Override
      public void map(Chunk[] cs) {
        if (cs[0]._len == 0) return;
        try (Frame.CSVStream is = new Frame.CSVStream(cs, null, 1, _parms)) {
          _nNonEmpty++;
          _size += (long) is.getCurrentRowSize() * cs[0]._len;
        } catch (IOException e) {
          throw new RuntimeException(e);
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

    private long copyCSVStream(Frame.CSVStream is, OutputStream os, int firstChkIdx) throws IOException {
      long len = 0;
      byte[] bytes = new byte[BUFFER_SIZE];
      int curChkIdx = firstChkIdx;
      for (;;) {
        int count = is.read(bytes, 0, BUFFER_SIZE);
        if (count <= 0) {
          break;
        }
        len += count;
        os.write(bytes, 0, count);
        int workDone = is._curChkIdx - curChkIdx;
        if (workDone > 0) {
          if (_j.stop_requested()) throw new Job.JobCancelledException(_j);
          _j.update(workDone);
          curChkIdx = is._curChkIdx;
        }
      }
      return len;
    }

    private void exportCSVStream(Frame.CSVStream is, String path, int firstChkIdx) {
      exportCSVStream(is, path, firstChkIdx, _compressor);
    }

    private void exportCSVStream(Frame.CSVStream is, String path, int firstChkIdx, CompressionFactory compressor) {
      OutputStream os = null;
      long written = -1;
      try {
        os = H2O.getPM().create(path, _overwrite);
        if (compressor != null) {
          os = compressor.wrapOutputStream(os);
        }
        written = copyCSVStream(is, os, firstChkIdx);
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
      final Frame.CSVStreamParams _csv_parms;

      PartExportTask(H2O.H2OCountedCompleter<?> completer, String[] colNames, int length, Frame.CSVStreamParams csvParms) {
        super(completer);
        _colNames = colNames;
        _length = length;
        _csv_parms = csvParms;
      }

      @Override
      public void map(Chunk[] cs) {
        Chunk anyChunk = cs[0];
        if (anyChunk.cidx() % _length > 0) {
          return;
        }
        int partIdx = anyChunk.cidx() / _length;
        String partPath = _path + "/part-m-" + String.valueOf(100000 + partIdx).substring(1);
        Frame.CSVStream is = new Frame.CSVStream(cs, _colNames, _length, _csv_parms);
        exportCSVStream(is, partPath, anyChunk.cidx());
      }

      @Override
      protected void setupLocal() {
        boolean created = H2O.getPM().mkdirs(_path);
        if (! created) Log.warn("Path ", _path, " was not created.");
      }
    }

    class ChunkExportTask extends MrFun<ChunkExportTask> {
      private final transient AtomicInteger _chunkIndex = new AtomicInteger(-1);
      private final transient BlockingQueue<Integer> _completed = new LinkedBlockingQueue<>();

      final Frame _fr;
      final String[] _colNames;
      final Frame.CSVStreamParams _csv_parms;
      final CompressionFactory _compressor;

      ChunkExportTask(Frame fr, String[] colNames, Frame.CSVStreamParams csvParms, CompressionFactory compressor) {
        _fr = fr;
        _colNames = colNames;
        _csv_parms = csvParms;
        _compressor = compressor;
      }

      @Override
      protected void map(int id) {
        final int nChunks = _fr.anyVec().nChunks(); 
        int cid;
        while ((cid = _chunkIndex.incrementAndGet()) < nChunks) {
          Chunk[] cs = new Chunk[_fr.numCols()];
          for (int i = 0; i < cs.length; i++) {
            Vec v = _fr.vec(i);
            cs[i] = v.chunkForChunkIdx(cid);
          }
          String chunkPath = getChunkPath(cid);
          Frame.CSVStream is = new Frame.CSVStream(cs, cid == 0 ? _colNames : null, 1, _csv_parms);
          exportCSVStream(is, chunkPath, cid, _compressor);
          _completed.add(cid);
        }
      }

      String getChunkPath(int cid) {
        return _path + ".chunk-" + String.valueOf(100000 + cid).substring(1);
      }
    }

    class InMemoryChunkExportTask extends ChunkExportTask {
      private final Key<?> _k;

      InMemoryChunkExportTask(Key<?> k, Frame fr, String[] colNames, Frame.CSVStreamParams csvParms, CompressionFactory compressor) {
        super(fr, colNames, csvParms, compressor);
        _k = k;
      }

      @Override
      String getChunkPath(int cid) {
        return H2O.getPM().toHexPath(Vec.chunkKey(_k, cid));
      }
    }
  }

  public static class CategoricalOneHotEncoder extends Iced {
    final Frame _frame;
    Job<Frame> _job;
    final String[] _skipCols;

    public CategoricalOneHotEncoder(Frame dataset, String[] skipCols) {
      _frame = dataset;
      _skipCols = skipCols;
    }

    /**
     * Driver for CategoricalOneHotEncoder
     */
    class CategoricalOneHotEncoderDriver extends H2O.H2OCountedCompleter {
      final Frame _frame;
      final Key<Frame> _destKey;
      final String[] _skipCols;
      CategoricalOneHotEncoderDriver(Frame frame, Key<Frame> destKey, String[] skipCols) { _frame = frame; _destKey = destKey; _skipCols = skipCols; }

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
        int numCategoricals = 0;
        for (int i=0;i<frameVecs.length;++i)
          if (frameVecs[i].isCategorical() && ArrayUtils.find(_skipCols, _frame._names[i])==-1)
            numCategoricals++;

        Vec[] extraVecs = new Vec[_skipCols.length];
        for (int i=0; i< extraVecs.length; ++i) {
          Vec v = _frame.vec(_skipCols[i]); //can be null
          if (v!=null) extraVecs[i] = v;
        }

        Frame categoricalFrame = new Frame();
        Frame outputFrame = new Frame(_destKey);
        int[] categorySizes = new int[numCategoricals];
        int numOutputColumns = 0;
        List<String> catnames= new ArrayList<>();
        for (int i = 0, j = 0; i < frameVecs.length; ++i) {
          if (ArrayUtils.find(_skipCols, _frame._names[i])>=0) continue;
          int numCategories = frameVecs[i].cardinality(); // Returns -1 if non-categorical variable
          if (numCategories > 0) {
            categoricalFrame.add(_frame.name(i), frameVecs[i]);
            categorySizes[j] = numCategories + 1/* for NAs */;
            numOutputColumns += categorySizes[j];
            for (int k=0;k<categorySizes[j]-1;++k)
              catnames.add(_frame.name(i) + "." + _frame.vec(i).domain()[k]);
            catnames.add(_frame.name(i) + ".missing(NA)");
            ++j;
          } else {
            outputFrame.add(_frame.name(i), frameVecs[i].makeCopy());
          }
        }
        OneHotConverter mrtask = new OneHotConverter(categorySizes);
        Frame binaryCols = mrtask.doAll(numOutputColumns, Vec.T_NUM, categoricalFrame).outputFrame();
        binaryCols.setNames(catnames.toArray(new String[0]));
        outputFrame.add(binaryCols);
        for (int i=0;i<extraVecs.length;++i) {
          if (extraVecs[i]!=null)
            outputFrame.add(_skipCols[i], extraVecs[i].makeCopy());
        }
        DKV.put(outputFrame);
        tryComplete();
      }
    }

    public Job<Frame> exec() {
      if (_frame == null)
        throw new IllegalArgumentException("Frame doesn't exist.");
      Key<Frame> destKey = Key.makeSystem(Key.make().toString());
      _job = new Job<>(destKey, Frame.class.getName(), "CategoricalOneHotEncoder");
      int workAmount = _frame.lastVec().nChunks();
      return _job.start(new CategoricalOneHotEncoderDriver(_frame, destKey, _skipCols), workAmount);
    }
  }

  public static class CategoricalLabelEncoder extends Iced {
    final Frame _frame;
    Job<Frame> _job;
    final String[] _skipCols;

    public CategoricalLabelEncoder(Frame dataset, String[] skipCols) {
      _frame = dataset;
      _skipCols = skipCols;
    }

    /**
     * Driver for CategoricalLabelEncoder
     */
    class CategoricalLabelEncoderDriver extends H2O.H2OCountedCompleter {
      final Frame _frame;
      final Key<Frame> _destKey;
      final String[] _skipCols;
      CategoricalLabelEncoderDriver(Frame frame, Key<Frame> destKey, String[] skipCols) { _frame = frame; _destKey = destKey; _skipCols = skipCols; }

      @Override public void compute2() {
        Vec[] frameVecs = _frame.vecs();
        Vec[] extraVecs = _skipCols==null?null:new Vec[_skipCols.length];
        if (extraVecs!=null) {
          for (int i = 0; i < extraVecs.length; ++i) {
            Vec v = _frame.vec(_skipCols[i]); //can be null
            if (v != null) extraVecs[i] = v;
          }
        }
        Frame outputFrame = new Frame(_destKey);
        for (int i = 0, j = 0; i < frameVecs.length; ++i) {
          if (_skipCols!=null && ArrayUtils.find(_skipCols, _frame._names[i])>=0) continue;
          int numCategories = frameVecs[i].cardinality(); // Returns -1 if non-categorical variable
          if (numCategories > 0) {
            outputFrame.add(_frame.name(i), frameVecs[i].toNumericVec());
          } else
            outputFrame.add(_frame.name(i), frameVecs[i].makeCopy());
        }
        if (_skipCols!=null) {
          for (int i = 0; i < extraVecs.length; ++i) {
            if (extraVecs[i] != null)
              outputFrame.add(_skipCols[i], extraVecs[i].makeCopy());
          }
        }
        DKV.put(outputFrame);
        tryComplete();
      }
    }

    public Job<Frame> exec() {
      if (_frame == null)
        throw new IllegalArgumentException("Frame doesn't exist.");
      Key<Frame> destKey = Key.makeSystem(Key.make().toString());
      _job = new Job<>(destKey, Frame.class.getName(), "CategoricalLabelEncoder");
      int workAmount = _frame.lastVec().nChunks();
      return _job.start(new CategoricalLabelEncoderDriver(_frame, destKey, _skipCols), workAmount);
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
  public static class CategoricalBinaryEncoder extends Iced {
    final Frame _frame;
    Job<Frame> _job;
    final String[] _skipCols;

    public CategoricalBinaryEncoder(Frame dataset, String[] skipCols) {
      _frame = dataset;
      _skipCols = skipCols;
    }

    /**
     * Driver for CategoricalBinaryEncoder
     */
    class CategoricalBinaryEncoderDriver extends H2O.H2OCountedCompleter {
      final Frame _frame;
      final Key<Frame> _destKey;
      final String[] _skipCols;
      CategoricalBinaryEncoderDriver(Frame frame, Key<Frame> destKey, String[] skipCols) { _frame = frame; _destKey = destKey; _skipCols = skipCols; }

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
        int numCategoricals = 0;
        for (int i=0;i<frameVecs.length;++i)
          if (frameVecs[i].isCategorical() && (_skipCols==null || ArrayUtils.find(_skipCols, _frame._names[i])==-1))
            numCategoricals++;

        Vec[] extraVecs = _skipCols==null?null:new Vec[_skipCols.length];
        if (extraVecs!=null) {
          for (int i = 0; i < extraVecs.length; ++i) {
            Vec v = _frame.vec(_skipCols[i]); //can be null
            if (v != null) extraVecs[i] = v;
          }
        }

        Frame categoricalFrame = new Frame();
        Frame outputFrame = new Frame(_destKey);
        int[] binaryCategorySizes = new int[numCategoricals];
        int numOutputColumns = 0;
        for (int i = 0, j = 0; i < frameVecs.length; ++i) {
          if (_skipCols!=null && ArrayUtils.find(_skipCols, _frame._names[i])>=0) continue;
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
        if (_skipCols!=null) {
          for (int i = 0; i < extraVecs.length; ++i) {
            if (extraVecs[i] != null)
              outputFrame.add(_skipCols[i], extraVecs[i].makeCopy());
          }
        }
        DKV.put(outputFrame);
        tryComplete();
      }
    }

    public Job<Frame> exec() {
      if (_frame == null)
        throw new IllegalArgumentException("Frame doesn't exist.");
      Key<Frame> destKey = Key.makeSystem(Key.make().toString());
      _job = new Job<>(destKey, Frame.class.getName(), "CategoricalBinaryEncoder");
      int workAmount = _frame.lastVec().nChunks();
      return _job.start(new CategoricalBinaryEncoderDriver(_frame, destKey, _skipCols), workAmount);
    }
  }

  /**
   * Helper to convert a categorical variable into the first eigenvector of the dummy-expanded matrix.
   */
  public static class CategoricalEnumLimitedEncoder {
    final Frame _frame;
    Job<Frame> _job;
    final String[] _skipCols;
    final int _maxLevels;

    public CategoricalEnumLimitedEncoder(int maxLevels, Frame dataset, String[] skipCols) {
      _frame = dataset;
      _skipCols = skipCols;
      _maxLevels = maxLevels;
    }

    /**
     * Driver for CategoricalEnumLimited
     */
    class CategoricalEnumLimitedDriver extends H2O.H2OCountedCompleter {
      final Frame _frame;
      final Key<Frame> _destKey;
      final String[] _skipCols;
      CategoricalEnumLimitedDriver(Frame frame, Key<Frame> destKey, String[] skipCols) {
        _frame = frame; _destKey = destKey; _skipCols = skipCols;
      }

      @Override public void compute2() {
        Vec[] frameVecs = _frame.vecs();
        Vec[] extraVecs = new Vec[_skipCols==null?0:_skipCols.length];
        for (int i=0; i< extraVecs.length; ++i) {
          Vec v = _skipCols==null||_skipCols.length<=i?null:_frame.vec(_skipCols[i]); //can be null
          if (v!=null) extraVecs[i] = v;
        }
//        Log.info(_frame.toTwoDimTable(0, (int)_frame.numRows()));
        Frame outputFrame = new Frame(_destKey);
        for (int i = 0; i < frameVecs.length; ++i) {
          Vec src = frameVecs[i];
          if (_skipCols!=null && ArrayUtils.find(_skipCols, _frame._names[i])>=0) continue;
          if (src.cardinality() > _maxLevels && !(src.isDomainTruncated(_maxLevels))) { //avoid double-encoding by checking it was not previously truncated on first encoding
            Key<Frame> source = Key.make();
            Key<Frame> dest = Key.make();
            Frame train = new Frame(source, new String[]{"enum"}, new Vec[]{src});
            DKV.put(train);
            Log.info("Reducing the cardinality of a categorical column with " + src.cardinality() + " levels to " + _maxLevels);
            train = Interaction.getInteraction(train._key, train.names(), _maxLevels).execImpl(dest).get();
            outputFrame.add(_frame.name(i) + ".top_" + _maxLevels + "_levels", train.anyVec().makeCopy());
            train.remove();
            DKV.remove(source);
          } else {
            outputFrame.add(_frame.name(i), frameVecs[i].makeCopy());
          }
        }
        for (int i=0;i<extraVecs.length;++i) {
          if (extraVecs[i]!=null)
            outputFrame.add(_skipCols[i], extraVecs[i].makeCopy());
        }
//        Log.info(outputFrame.toTwoDimTable(0, (int)outputFrame.numRows()));
        DKV.put(outputFrame);
        tryComplete();
      }
    }

    public Job<Frame> exec() {
      if (_frame == null)
        throw new IllegalArgumentException("Frame doesn't exist.");
      Key<Frame> destKey = Key.makeSystem(Key.make().toString());
      _job = new Job<>(destKey, Frame.class.getName(), "CategoricalEnumLimited");
      int workAmount = _frame.lastVec().nChunks();
      return _job.start(new CategoricalEnumLimitedDriver(_frame, destKey, _skipCols), workAmount);
    }
  }

  /**
   * Helper to convert a categorical variable into the first eigenvector of the dummy-expanded matrix.
   */
  public static class CategoricalEigenEncoder {
    final Frame _frame;
    Job<Frame> _job;
    final String[] _skipCols;
    final ToEigenVec _tev;

    public CategoricalEigenEncoder(ToEigenVec tev, Frame dataset, String[] skipCols) {
      _frame = dataset;
      _skipCols = skipCols;
      _tev = tev;
    }

    /**
     * Driver for CategoricalEigenEncoder
     */
    class CategoricalEigenEncoderDriver extends H2O.H2OCountedCompleter {
      final Frame _frame;
      final Key<Frame> _destKey;
      final String[] _skipCols;
      final ToEigenVec _tev;
      CategoricalEigenEncoderDriver(ToEigenVec tev, Frame frame, Key<Frame> destKey, String[] skipCols) {
        _tev = tev; _frame = frame; _destKey = destKey; _skipCols = skipCols;
        assert _tev!=null : "Override toEigenVec for this Algo!";
      }

      @Override public void compute2() {
        Vec[] frameVecs = _frame.vecs();
        Vec[] extraVecs = new Vec[_skipCols==null?0:_skipCols.length];
        for (int i=0; i< extraVecs.length; ++i) {
          Vec v = _skipCols==null||_skipCols.length<=i?null:_frame.vec(_skipCols[i]); //can be null
          if (v!=null) extraVecs[i] = v;
        }
        Frame outputFrame = new Frame(_destKey);
        for (int i = 0; i < frameVecs.length; ++i) {
          if (_skipCols!=null && ArrayUtils.find(_skipCols, _frame._names[i])>=0) continue;
          if (frameVecs[i].isCategorical())
            outputFrame.add(_frame.name(i) + ".Eigen", _tev.toEigenVec(frameVecs[i]));
          else
            outputFrame.add(_frame.name(i), frameVecs[i].makeCopy());
        }
        for (int i=0;i<extraVecs.length;++i) {
          if (extraVecs[i]!=null)
            outputFrame.add(_skipCols[i], extraVecs[i].makeCopy());
        }
        DKV.put(outputFrame);
        tryComplete();
      }
    }

    public Job<Frame> exec() {
      if (_frame == null)
        throw new IllegalArgumentException("Frame doesn't exist.");
      Key<Frame> destKey = Key.makeSystem(Key.make().toString());
      _job = new Job<>(destKey, Frame.class.getName(), "CategoricalEigenEncoder");
      int workAmount = _frame.lastVec().nChunks();
      return _job.start(new CategoricalEigenEncoderDriver(_tev, _frame, destKey, _skipCols), workAmount);
    }
  }

  public static void cleanUp(Collection<Key> toDelete) {
    if (toDelete == null) {
      return;
    }
    Futures fs = new Futures();
    for (Key k : toDelete) {
      Keyed.remove(k, fs, true);
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
        long[] uniques = (v.min() >= 0 && v.max() < Integer.MAX_VALUE - 4) ? new VecUtils.CollectDomainFast((int)v.max()).doAll(v).domain() : new VecUtils.CollectIntegerDomain().doAll(v).domain();
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
              if (c.isNA(i)) continue;
              else c.set(i, fromTo[(int)c.at8(i)]);
            }
          }
        }.doAll(v);
        v.setDomain(newDomain);
      }
    }
  }

  public static void delete(Lockable ...frs) {
    for (Lockable l : frs) {
      if (l != null) l.delete();
    }
  }

  /**
   * This class will calculate the weighted mean and standard deviatioin of a target column of a data frame
   * with the weights specified in another column.
   *
   * For the weighted mean, it is calculated as (sum from i=1 to N wi*xi)/(sum from i=1 to N wi)
   * For the weigthed std, it is calculated as
   *    (sum from i=1 to N wi*(xi-weightedMean)*(xi-weightedMean))/(C *sum from i=1 to N wi)
   * where C = (M-1)/M and M is the number of nonzero weights.
   *
   */
  public static class CalculateWeightMeanSTD extends MRTask<CalculateWeightMeanSTD> {
    public double _weightedEleSum;
    public double _weightedEleSqSum;
    public double _weightedCount;
    public double _weightedMean;
    public double _weightedSigma;
    public long _nonZeroWeightsNum;

    @Override
    public void map(Chunk pcs, Chunk wcs) {
      _weightedEleSum = 0;
      _weightedEleSqSum = 0;
      _weightedCount = 0;
      _nonZeroWeightsNum = 0;
      assert pcs._len==wcs._len:"Prediction and weight chunk should have the same length.";
      // 0 contains prediction, 1 columns weight
      for (int rindex = 0; rindex < pcs._len; rindex++) {
        double weight = wcs.atd(rindex);
        double pvalue = pcs.atd(rindex);
        if ((!Double.isNaN(pvalue)) && (Math.abs(weight) > 0) && (!Double.isNaN(pvalue))) {
          double v1 = pvalue * wcs.atd(rindex);
          _weightedEleSum += v1;
          _weightedEleSqSum += v1 * pvalue;

          _weightedCount += wcs.atd(rindex);
          _nonZeroWeightsNum++;
        }
      }
    }

    @Override
    public void reduce(CalculateWeightMeanSTD other) {
      _weightedEleSum += other._weightedEleSum;
      _weightedEleSqSum += other._weightedEleSqSum;
      _weightedCount += other._weightedCount;
      _nonZeroWeightsNum += other._nonZeroWeightsNum;
    }

    @Override
    public void postGlobal() {
      _weightedMean = _weightedCount==0?Double.NaN:_weightedEleSum/_weightedCount;  // return NaN for bad input
      double scale = _nonZeroWeightsNum==1?_nonZeroWeightsNum*1.0:(_nonZeroWeightsNum-1.0);
      double scaling = _nonZeroWeightsNum*1.0/scale;
      _weightedSigma = _weightedCount==0?Double.NaN:
              Math.sqrt((_weightedEleSqSum/_weightedCount-_weightedMean*_weightedMean)*scaling);  // return NaN for bad input
    }

    public double getWeightedMean() {
      return _weightedMean;
    }

    public double getWeightedSigma() {
      return _weightedSigma;
    }
  }

  /**
   * Labels frame's rows with a sequence starting with 1 & sending with total number of rows in the frame.
   * A vector is added to the frame given, no data are duplicated.
   *
   * @param frame           Frame to label
   * @param labelColumnName Name of the label column
   */
  public static void labelRows(final Frame frame, final String labelColumnName) {
    final Vec labelVec = Vec.makeSeq(1, frame.numRows());
    frame.add(labelColumnName, labelVec);
  }

  private static String getCurrConstraintName(int id, Vec constraintsNames, BufferedString tmpStr) {
    String currConstraintName;
    if (constraintsNames.isString())
      currConstraintName =  constraintsNames.atStr(tmpStr, id).toString();
    else if (constraintsNames.isCategorical())
      currConstraintName = constraintsNames.domain()[id];
    else
      throw new IllegalArgumentException("Illegal beta constraints file, names column expected to contain column names (strings)");
    return currConstraintName;
  }

  private static void writeNewRow(String name, Frame betaConstraints, NewChunk[] nc, int id) {
    nc[0].addStr(name);
    for (int k = 1; k < nc.length; k++) {
      nc[k].addNum(betaConstraints.vec(k).at(id));
    }
  }

  public static class ExpandCatBetaConstraints extends MRTask<ExpandCatBetaConstraints> {
    public final Frame _trainFrame;
    public final Frame _betaCS;

    public ExpandCatBetaConstraints(Frame betaCs, Frame train) {
      _trainFrame = train;
      _betaCS = betaCs;
    }

    @Override
    public void map(Chunk[] chunks, NewChunk[] newChunks) {
      int chkLen = chunks[0]._len;
      int chkCol = chunks.length;
      BufferedString tempStr = new BufferedString();
      List<String> trainColNames = Arrays.asList(_trainFrame.names());
      String[] colTypes = _trainFrame.typesStr();
      for (int rowIndex=0; rowIndex<chkLen; rowIndex++) {
        String cName = chunks[0].atStr(tempStr, rowIndex).toString();
        int trainColNumber = trainColNames.indexOf(cName);
        String csTypes = colTypes[trainColNumber];
        if ("Enum".equals(csTypes)) {
          String[] domains = _trainFrame.vec(trainColNumber).domain();
          int domainLen = domains.length;
          for (int repIndex = 0; repIndex < domainLen; repIndex++) {
            String newCSName = cName+'.'+domains[repIndex];
            newChunks[0].addStr(newCSName);
            for (int colIndex = 1; colIndex < chkCol; colIndex++) {
              newChunks[colIndex].addNum(chunks[colIndex].atd(rowIndex));
            }
          }
        } else {  // copy over non-enum beta constraints
          newChunks[0].addStr(chunks[0].atStr(tempStr, rowIndex).toString());
          for (int colIndex = 1; colIndex < chkCol; colIndex++) {
            newChunks[colIndex].addNum(chunks[colIndex].atd(rowIndex));
          }
        }
      }
    }
  }

    public static Frame encodeBetaConstraints(Key key, String[] coefNames, String[] coefOriginalNames, Frame betaConstraints) {
    int ncols = betaConstraints.numCols();
    AppendableVec[] appendableVecs = new AppendableVec[ncols];
    NewChunk ncs[] = new NewChunk[ncols];
    Key keys[] = Vec.VectorGroup.VG_LEN1.addVecs(ncols);
    for (int i = 0; i < appendableVecs.length; i++) {
      appendableVecs[i] = new AppendableVec(keys[i], i == 0 ? Vec.T_STR : betaConstraints.vec(i).get_type());
    }
    Futures fs = new Futures();
    int chunknum = 0;
    if (ncs[0] == null) {
      for (int i = 0; i < ncols; i++) {
        ncs[i] = new NewChunk(appendableVecs[i],chunknum);
      }
    }
    Vec constraintsNames = betaConstraints.vec(0);
    BufferedString tmpStr = new BufferedString();
    for (int i = 0; i < constraintsNames.length(); i++) {
      String currConstraintName = getCurrConstraintName(i, constraintsNames, tmpStr);
      for (int j = 0; j < coefNames.length; j++) {
        if (coefNames[j].equals(currConstraintName)) {
          writeNewRow(currConstraintName, betaConstraints, ncs, i);
        } else if (!Arrays.asList(coefNames).contains(currConstraintName) && Arrays.asList(coefOriginalNames).contains(currConstraintName) && coefNames[j].startsWith(currConstraintName)) {
          writeNewRow(coefNames[j], betaConstraints, ncs, i);
        }
      }
    }
    if (ncs[0] != null) {
      for (int i = 0; i < ncols; i++) { 
        ncs[i].close(chunknum,fs);
      }
      ncs[0] = null;
    }
    Vec[] vecs = new Vec[ncols];
    final int rowLayout = appendableVecs[0].compute_rowLayout();
    for (int i = 0; i < appendableVecs.length; i++) {
      vecs[i] = appendableVecs[i].close(rowLayout,fs);
    }
    fs.blockForPending();
    Frame fr = new Frame(key, betaConstraints.names(), vecs);
    if (key != null) {
      DKV.put(fr);
    }
    return fr;
  }

  public static Chunk[] extractChunks(Frame fr, int chunkId, boolean runLocal) {
    final Vec v0 = fr.anyVec();
    final Vec[] vecs = fr.vecs();
    final Chunk[] chunks = new Chunk[vecs.length];
    for (int i = 0; i < vecs.length; i++) {
      if (vecs[i] != null) {
        assert runLocal || vecs[i].chunkKey(chunkId).home()
                : "Chunk=" + chunkId + " v0=" + v0 + ", k=" + v0.chunkKey(chunkId) + "   v[" + i + "]=" + vecs[i] + ", k=" + vecs[i].chunkKey(chunkId);
        chunks[i] = vecs[i].chunkForChunkIdx(chunkId);
      }
    }
    return chunks;
  }
  
}

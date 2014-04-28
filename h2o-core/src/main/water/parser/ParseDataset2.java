package water.parser;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.zip.*;
import jsr166y.CountedCompleter;
import water.*;
import water.fvec.*;
import water.fvec.Vec.VectorGroup;
import water.nbhm.NonBlockingHashMap;
import water.util.ArrayUtils;
import water.util.PrettyPrint;
import water.util.Log;

public class ParseDataset2 extends Job<Frame> {
  final ParseMonitor _progress;

  // Keys are limited to ByteVec Keys and Frames-of-1-ByteVec Keys
  public static Frame parse(Key okey, Key... keys) { return parse(okey,keys,true, 0/*guess header*/); }

  // Guess setup from inspecting the first Key only, then parse.
  // Suitable for e.g. testing setups, where the data is known to be sane.
  // NOT suitable for random user input!
  public static Frame parse(Key okey, Key[] keys, boolean delete_on_done, int checkHeader) {
    Key k = keys[0];
    byte[] bits = ZipUtil.getFirstUnzippedBytes(getByteVec(k));
    ParserSetup globalSetup = ParserSetup.guessSetup(bits, checkHeader);
    if( globalSetup._ncols <= 0 ) throw new java.lang.IllegalArgumentException(globalSetup.toString());
    return forkParseDataset(okey, keys, globalSetup, delete_on_done).get();
  }

  // Allow both ByteVec keys and Frame-of-1-ByteVec
  private static ByteVec getByteVec(Key key) {
    Iced ice = DKV.get(key).get();
    return (ByteVec)(ice instanceof ByteVec ? ice : ((Frame)ice).vecs()[0]);
  }
  private static String [] genericColumnNames(int ncols){
    String [] res = new String[ncols];
    for(int i = 0; i < res.length; ++i) res[i] = "C" + String.valueOf(i+1);
    return res;
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
    _progress = new ParseMonitor(totalLen);
  }

  @Override public Futures remove( Futures fs ) {
    _progress.remove(fs);
    super.remove(fs);
    return fs;
  }

  // -----------------------------
  // Class to track parsing progress across all nodes & files
  static class ParseMonitor extends Keyed {
    final long _total;
    private long _parsedBytes;
    ParseMonitor( long totalLen ) {
      super(Key.make((byte) 0, Key.JOB)); 
      _total = totalLen; 
      DKV.put(_key,this); 
    }
    void onProgress( final long len ) {
      // Background (lazy) atomic update the remote value
      new TAtomic<ParseMonitor>() {
        @Override public ParseMonitor atomic(ParseMonitor old) {
          if( old == null ) return null;
          old._parsedBytes += len;
          return old;
        }
      }.fork(_key);
    }
  }

  // -----------------------------
  // Class to track parsing progress within 1 file on 1 node
  static class FileMonitor implements ProgressMonitor {
    private final ParseMonitor _pmon;
    long _parsedBytes;
    FileMonitor( ParseMonitor pmon ) { _pmon = pmon; }
    @Override public void update( long len ) { synchronized(this) {_parsedBytes += len;} _pmon.onProgress(len); }
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
  private static void parse_impl(ParseDataset2 job, Key[] fkeys, ParserSetup setup, boolean delete_on_done) {
    assert setup._ncols > 0;
    if( fkeys.length == 0) { job.cancel();  return;  }

    VectorGroup vg = getByteVec(fkeys[0]).group();
    MultiFileParseTask uzpt = new MultiFileParseTask(vg,setup,job._progress._key,fkeys).doAll(fkeys);
    EnumUpdateTask eut = null;
    // Calculate enum domain
    int n = 0;
    int [] ecols = new int[uzpt._dout._nCols];
    for( int i = 0; i < ecols.length; ++i )
      if(uzpt._dout._vecs[i].shouldBeEnum())
        ecols[n++] = i;
    ecols =  Arrays.copyOf(ecols, n);
    if( ecols.length > 0 ) {
      EnumFetchTask eft = new EnumFetchTask(H2O.SELF.index(), uzpt._eKey, ecols).doAll(fkeys[0]/*dummy*/);
      Enum [] enums = eft._gEnums;
      ValueString [][] ds = new ValueString[ecols.length][];
      int j = 0;
      for( int i : ecols ) uzpt._dout._vecs[i].setDomain(ValueString.toString(ds[j++] = enums[i].computeColumnDomain()));
      eut = new EnumUpdateTask(ds, eft._lEnums, uzpt._chunk2Enum, ecols);
    }
    Frame fr = new Frame(job.dest(),setup._columnNames != null?setup._columnNames:genericColumnNames(uzpt._dout._nCols),uzpt._dout.closeVecs());
    // SVMLight is sparse format, there may be missing chunks with all 0s, fill them in
    new SVFTask(fr).doAll(fkeys[0]/*dummy*/);
    // Update enums to the globally agreed numbering
    if( eut != null ) {
      Vec[] evecs = new Vec[ecols.length];
      for( int i = 0; i < evecs.length; ++i ) evecs[i] = fr.vecs()[ecols[i]];
      eut.doAll(evecs);
    }
    
    logParseResults(job, fr);
    
    // Release the frame for overwriting
    fr.unlock(job._key);
    // Remove CSV files from H2O memory
    for( Key k : fkeys ) {
      Value val = DKV.get(k);
      if( val.isVec() && delete_on_done ) {
        ((Vec)val.get()).remove(); // Raw vecs, bytevecs & NFSFileVecs, often from test code
      } else {                     // Frames (of NFSFileVecs) from e.g. Import
        Lockable l = DKV.get(k).get();
        if( delete_on_done ) l.delete(job._key,0.0f);
        else                 l.unlock(job._key);
      }
    }
    job.remove();
  }

  // --------------------------------------------------------------------------
  /** Task to update enum values to match the global numbering scheme.
   *  Performs update in place so that values originally numbered using
   *  node-local unordered numbering will be numbered using global numbering.
   *  @author tomasnykodym
   */
  private static class EnumUpdateTask extends MRTask<EnumUpdateTask> {
    private transient int[][][] _emap;
    private final ValueString [][] _gDomain;
    private final Enum [][] _lEnums;
    private final int  [] _chunk2Enum;
    private final int [] _colIds;
    private EnumUpdateTask(ValueString [][] gDomain, Enum [][]  lEnums, int [] chunk2Enum, int [] colIds){
      _gDomain = gDomain; _lEnums = lEnums; _chunk2Enum = chunk2Enum; _colIds = colIds;
    }

    private int[][] emap(int nodeId) {
      if( _emap == null ) _emap = new int[_lEnums.length][][];
      if( _emap[nodeId] == null ) {
        int[][] emap = _emap[nodeId] = new int[_gDomain.length][];
        for( int i = 0; i < _gDomain.length; ++i ) {
          if( _gDomain[i] != null ) {
            assert _lEnums[nodeId] != null : "missing lEnum of node "  + nodeId + ", enums = " + Arrays.toString(_lEnums);
            final Enum e = _lEnums[nodeId][_colIds[i]];
            emap[i] = new int[e.maxId()+1];
            Arrays.fill(emap[i], -1);
            for(int j = 0; j < _gDomain[i].length; ++j) {
              ValueString vs = _gDomain[i][j];
              if( e.containsKey(vs) ) {
                assert e.getTokenId(vs) <= e.maxId():"maxIdx = " + e.maxId() + ", got " + e.getTokenId(vs);
                emap[i][e.getTokenId(vs)] = j;
              }
            }
          }
        }
      }
      return _emap[nodeId];
    }

    @Override public void map(Chunk [] chks){
      int[][] emap = emap(_chunk2Enum[chks[0].cidx()]);
      final int cidx = chks[0].cidx();
      for(int i = 0; i < chks.length; ++i) {
        Chunk chk = chks[i];
        if(_gDomain[i] == null) // killed, replace with all NAs
          DKV.put(chk._vec.chunkKey(chk.cidx()),new C0DChunk(Double.NaN,chk._len));
        else for( int j = 0; j < chk._len; ++j){
          if( chk.isNA0(j) )continue;
          long l = chk.at80(j);
          assert l >= 0 && l < emap[i].length : "Found OOB index "+l+" pulling from "+chk.getClass().getSimpleName();
          assert emap[i][(int)l] >= 0: H2O.SELF.toString() + ": missing enum at col:" + i + ", line: " + j + ", val = " + l + ", chunk=" + chk.getClass().getSimpleName();
          chk.set0(j, emap[i][(int)l]);
        }
        chk.close(cidx, _fs);
      }
    }
  }

  // --------------------------------------------------------------------------
  private static class EnumFetchTask extends MRTask<EnumFetchTask> {
    private final Key _k;
    private final int [] _ecols;
    private final int _homeNode; // node where the computation started, enum from this node MUST be cloned!
    private Enum [] _gEnums; // global enums per column
    private Enum [][] _lEnums; // local enums per node per column
    private EnumFetchTask(int homeNode, Key k, int [] ecols){_homeNode = homeNode; _k = k;_ecols = ecols;}
    @Override public void setupLocal() {
      _lEnums = new Enum[H2O.CLOUD.size()][];
      if( !MultiFileParseTask._enums.containsKey(_k) ) return;
      _lEnums[H2O.SELF.index()] = _gEnums = MultiFileParseTask._enums.get(_k);
      // if we are the original node (i.e. there will be no sending over wire),
      // we have to clone the enums not to share the same object (causes
      // problems when computing column domain and renumbering maps).
      if( H2O.SELF.index() == _homeNode ) {
        _gEnums = _gEnums.clone();
        for(int i = 0; i < _gEnums.length; ++i)
          _gEnums[i] = _gEnums[i].deepCopy();
      }
      MultiFileParseTask._enums.remove(_k);
    }

    @Override public void reduce(EnumFetchTask etk) {
      if(_gEnums == null) {
        _gEnums = etk._gEnums;
        _lEnums = etk._lEnums;
      } else if (etk._gEnums != null) {
        for( int i : _ecols ) _gEnums[i].merge(etk._gEnums[i]);
        for( int i = 0; i < _lEnums.length; ++i )
          if( _lEnums[i] == null ) _lEnums[i] = etk._lEnums[i];
          else assert etk._lEnums[i] == null;
      }
    }
  }

  // --------------------------------------------------------------------------
  // Run once on all nodes; fill in missing zero chunks
  private static class SVFTask extends MRTask<SVFTask> {
    private final Frame _f;
    private SVFTask( Frame f ) { _f = f; }
    @Override public void map(Key key) {
      Vec v0 = _f.anyVec();
      for( int i = 0; i < v0.nChunks(); ++i ) {
        if( !v0.chunkKey(i).home() ) continue;
        // First find the nrows as the # rows of non-missing chunks; done on
        // locally-homed chunks only - to keep the data distribution.
        int nlines = 0;
        for( Vec vec : _f.vecs() ) {
          Value val = H2O.get(vec.chunkKey(i)); // Local-get only
          if( val != null ) {
            nlines = ((Chunk)val.get()).len();
            break;
          }
        }

        // Now fill in appropriate-sized zero chunks
        for( Vec vec : _f.vecs() ) {
          Key k = vec.chunkKey(i);
          if( !k.home() ) continue; // Local keys only
          Value val = H2O.get(k);   // Local-get only
          if( val == null )         // Missing?  Fill in w/zero chunk
            H2O.putIfMatch(k, new Value(k,new C0DChunk(0, nlines)), null);
        }
      }
    }
  }

  // --------------------------------------------------------------------------
  // We want to do a standard MRTask with a collection of file-keys (so the
  // files are parsed in parallel across the cluster), but we want to throttle
  // the parallelism on each node.
  private static class MultiFileParseTask extends MRTask<MultiFileParseTask> {
    private final ParserSetup _setup; // The expected column layout
    private final VectorGroup _vg;    // vector group of the target dataset
    private final int _vecIdStart;    // Start of available vector keys
    // Shared against all concurrent unrelated parses, a map to the node-local
    // Enum lists for each concurrent parse.
    private static NonBlockingHashMap<Key, Enum[]> _enums = new NonBlockingHashMap<>();
    // The Key used to sort out *this* parse's Enum[]
    private final Key _eKey = Key.make();
    // Mapping from Chunk# to cluster-node-number holding the enum mapping.
    // It is either self for all the non-parallel parses, or the Chunk-home for parallel parses.
    private final int[] _chunk2Enum;
    // ParseMonitor key
    private final Key _progress;
    // A mapping of Key+ByteVec to rolling total Chunk counts.
    private final int[]  _fileChunkOffsets;

    // OUTPUT fields:
    FVecDataOut _dout;

    MultiFileParseTask(VectorGroup vg,  ParserSetup setup, Key progress, Key[] fkeys ) {
      _setup = setup; 
      _vg = vg; 
      _vecIdStart = _vg.reserveKeys(_setup._pType == ParserType.SVMLight ? 100000000 : setup._ncols);
      _progress = progress;

      // A mapping of Key+ByteVec to rolling total Chunk counts.
      _fileChunkOffsets = new int[fkeys.length];
      int len = 0;
      for( int i = 0; i < fkeys.length; ++i ) {
        _fileChunkOffsets[i] = len;
        len += getByteVec(fkeys[i]).nChunks();
      }

      // Mapping from Chunk# to cluster-node-number
      _chunk2Enum = MemoryManager.malloc4(len);
      Arrays.fill(_chunk2Enum, -1);
    }

    // Fetch out the node-local Enum[] using _eKey and _enums hashtable
    private Enum[] enums() {
      Enum[] enums = _enums.get(_eKey);
      if( enums != null ) return enums;
      enums = new Enum[_setup._ncols];
      for( int i = 0; i < enums.length; ++i ) enums[i] = new Enum();
      _enums.putIfAbsent(_eKey, enums);
      return _enums.get(_eKey); // Re-get incase lost insertion race
    }

    // Flag all chunk enums as being on local (self)
    private void chunksAreLocal( Vec vec, int chunkStartIdx ) {
      for(int i = 0; i < vec.nChunks(); ++i)  
        _chunk2Enum[chunkStartIdx + i] = H2O.SELF.index();
    }

    // Called once per file
    @Override public void map( Key key ) {
      // Get parser setup info for this chunk
      ByteVec vec = getByteVec(key);
      byte[] zips = vec.getFirstBytes();
      ZipUtil.Compression cpr = ZipUtil.guessCompressionMethod(zips);
      byte[] bits = ZipUtil.unzipBytes(zips,cpr);
      ParserSetup localSetup = _setup.guessSetup(bits);
      if( !localSetup._isValid ) return;

      // Parse the file
      final int chunkStartIdx = _fileChunkOffsets[ArrayUtils.find(_keys,key)];
      try {
        switch( cpr ) {
        case NONE:
          if( localSetup._pType._parallelParseSupported ) {
            DParse dp = new DParse(_vg, localSetup, _vecIdStart, chunkStartIdx,this);
            addToPendingCount(1);
            dp.setCompleter(this);
            dp.asyncExec(vec);
            for( int i = 0; i < vec.nChunks(); ++i )
              _chunk2Enum[chunkStartIdx + i] = vec.chunkKey(i).home_node().index();
          } else {
      //      FileMonitor pmon = new FileMonitor(_progress);
      //      _dout = streamParse(vec.openStream(pmon), localSetup, _vecIdStart, chunkStartIdx,pmon);
      //      chunksAreLocal(vec,chunkStartIdx);
            throw H2O.unimpl();
          }
          break;
        case ZIP: {
          // Zipped file; no parallel decompression;
          FileMonitor pmon = new FileMonitor((ParseMonitor)DKV.get(_progress).get());
          ZipInputStream zis = new ZipInputStream(vec.openStream(pmon));
          ZipEntry ze = zis.getNextEntry(); // Get the *FIRST* entry
          // There is at least one entry in zip file and it is not a directory.
          if( ze != null && !ze.isDirectory() ) 
            _dout = streamParse(zis,localSetup, _vecIdStart, chunkStartIdx,pmon);
          else zis.close();       // Confused: which zipped file to decompress
          chunksAreLocal(vec,chunkStartIdx);
          break;
        }
        case GZIP:
          // Zipped file; no parallel decompression;
          FileMonitor pmon = new FileMonitor((ParseMonitor)DKV.get(_progress).get());
          _dout = streamParse(new GZIPInputStream(vec.openStream(pmon)),localSetup,_vecIdStart, chunkStartIdx,pmon);
          // set this node as the one which processed all the chunks
          chunksAreLocal(vec,chunkStartIdx);
          break;
        }
      } catch( IOException ioe ) {
        throw new RuntimeException(ioe);
      }
    }

    // Reduce: combine errors from across files.
    // Roll-up other meta data
    @Override public void reduce( MultiFileParseTask uzpt ) {
      //assert this != uzpt;
      //// Collect & combine columns across files
      //if(_dout == null)_dout = uzpt._dout;
      //else _dout.reduce(uzpt._dout);
      //if(_chunk2Enum == null)_chunk2Enum = uzpt._chunk2Enum;
      //else if(_chunk2Enum != uzpt._chunk2Enum) { // we're sharing global array!
      //  for(int i = 0; i < _chunk2Enum.length; ++i){
      //    if(_chunk2Enum[i] == -1)_chunk2Enum[i] = uzpt._chunk2Enum[i];
      //    else assert uzpt._chunk2Enum[i] == -1:Arrays.toString(_chunk2Enum) + " :: " + Arrays.toString(uzpt._chunk2Enum);
      //  }
      //}
      throw H2O.unimpl();
    }

    // Zipped file; no parallel decompression; decompress into local chunks,
    // parse local chunks; distribute chunks later.
    private FVecDataOut streamParse( final InputStream is, final ParserSetup localSetup, int vecIdStart, int chunkStartIdx, FileMonitor pmon) throws IOException {
      // All output into a fresh pile of NewChunks, one per column
      FVecDataOut dout = new FVecDataOut(_vg, chunkStartIdx, localSetup._ncols, vecIdStart, enums());
      Parser p = localSetup.parser();
      // assume 2x inflation rate
      if( localSetup._pType._parallelParseSupported ) p.streamParse(is, dout, pmon);
      else                                            p.streamParse(is, dout);
      // Parse all internal "chunks", until we drain the zip-stream dry.  Not
      // real chunks, just flipping between 32K buffers.  Fills up the single
      // very large NewChunk.
      dout.close(_fs);
      return dout;
    }

    // ------------------------------------------------------------------------
    private class DParse extends MRTask<DParse> {
      final ParserSetup _setup;
      final int _vecIdStart;
      final int _startChunkIdx; // for multifile parse, offset of the first chunk in the final dataset
      final VectorGroup _vg;
      FVecDataOut _dout;
      transient final MultiFileParseTask _outerMFPT;

      DParse(VectorGroup vg, ParserSetup setup, int vecIdstart, int startChunkIdx, MultiFileParseTask mfpt) {
        _vg = vg;
        _setup = setup;
        _vecIdStart = vecIdstart;
        _startChunkIdx = startChunkIdx;
        _outerMFPT = mfpt;
      }
      @Override public void map( Chunk in ) {
        Enum [] enums = enums();
        // Break out the input & output vectors before the parse loop
        FVecDataIn din = new FVecDataIn(in);
        FVecDataOut dout;
        Parser p;
        switch(_setup._pType) {
        case CSV:
          p = new CsvParser(_setup);
          dout = new FVecDataOut(_vg,_startChunkIdx + in.cidx(),_setup._ncols,_vecIdStart,enums);
          break;
        //  case SVMLight:
        //    p = new SVMLightParser(_setup);
        //    dout = new SVMLightFVecDataOut(_vg, _startChunkIdx + in.cidx(), _setup._ncols, _vecIdStart, enums);
        //    break;
        default:
          throw H2O.unimpl();
        }
        p.parallelParse(in.cidx(),din,dout);
        (_dout = dout).close(_fs);
        FileMonitor fmon = new FileMonitor((ParseMonitor)DKV.get(_progress).get());
        fmon.update(in._len);   // Record bytes parsed
      }
      @Override public void reduce(DParse dp) { _dout.reduce(dp._dout); }
      @Override public void postGlobal() {
        super.postGlobal();
        _outerMFPT._dout = _dout;
      }
    }
  }

  // ------------------------------------------------------------------------
  /** Parsed data output specialized for fluid vecs.
   * @author tomasnykodym
   */
  private static class FVecDataOut extends Iced implements Parser.StreamDataOut {
    protected transient NewChunk [] _nvs;
    protected AppendableVec []_vecs;
    protected final Enum [] _enums;
    protected final byte [] _ctypes;
    long _nLines;
    int _nCols;
    int _col = -1;
    final int _cidx;
    final int _vecIdStart;
    boolean _closedVecs = false;
    private final VectorGroup _vg;

    static final private byte UCOL = 0;
    static final private byte NCOL = 1;
    static final private byte ECOL = 2;
    static final private byte TCOL = 3;

    public FVecDataOut(VectorGroup vg, int cidx, int ncols, int vecIdStart, Enum [] enums){
      _vecs = new AppendableVec[ncols];
      _nvs = new NewChunk[ncols];
      _enums = enums;
      _nCols = ncols;
      _cidx = cidx;
      _vg = vg;
      _vecIdStart = vecIdStart;
      _ctypes = MemoryManager.malloc1(ncols);
      for(int i = 0; i < ncols; ++i)
        _nvs[i] = (NewChunk)(_vecs[i] = new AppendableVec(vg.vecKey(vecIdStart + i))).chunkForChunkIdx(_cidx);

    }
    public FVecDataOut reduce(Parser.StreamDataOut sdout){
      FVecDataOut dout = (FVecDataOut)sdout;
      _nCols = Math.max(_nCols,dout._nCols);
      if(dout._vecs.length > _vecs.length){
        AppendableVec [] v = _vecs;
        _vecs = dout._vecs;
        dout._vecs = v;
      }
      for(int i = 0; i < dout._vecs.length; ++i)
        _vecs[i].reduce(dout._vecs[i]);
      return this;
    }
    @Override public FVecDataOut close(){
      Futures fs = new Futures();
      close(fs);
      fs.blockForPending();
      return this;
    }
     public void check(){
       if(_nvs != null) for(NewChunk nv:_nvs)
        assert (nv._len2 == _nLines):"unexpected number of lines in NewChunk, got " + nv._len2 + ", but expected " + _nLines;
    }
    @Override public FVecDataOut close(Futures fs){
      if( _nvs == null ) return this; // Might call close twice
      for(NewChunk nv:_nvs) nv.close(_cidx, fs);
      _nvs = null;  // Free for GC
      return this;
    }
    @Override public FVecDataOut nextChunk(){
      return  new FVecDataOut(_vg, _cidx+1, _nCols, _vecIdStart, _enums);
    }

    private Vec [] closeVecs(){
      Futures fs = new Futures();
      _closedVecs = true;
      Vec [] res = new Vec[_vecs.length];
      for(int i = 0; i < _vecs[0]._espc.length; ++i){
        int j = 0;
        while(j < _vecs.length && _vecs[j]._espc[i] == 0)++j;
        if(j == _vecs.length)break;
        final long clines = _vecs[j]._espc[i];
        for(AppendableVec v:_vecs) {
          if(v._espc[i] == 0)v._espc[i] = clines;
          else assert v._espc[i] == clines:"incompatible number of lines: " +  v._espc[i] +  " != " + clines;
        }
      }
      for(int i = 0; i < _vecs.length; ++i)
        res[i] = _vecs[i].close(fs);
      _vecs = null;  // Free for GC
      fs.blockForPending();
      return res;
    }

    @Override public void newLine() {
      if(_col >= 0){
        ++_nLines;
        for(int i = _col+1; i < _nCols; ++i)
          addInvalidCol(i);
      }
      _col = -1;
    }
    protected long linenum(){return _nLines;}
    @Override public void addNumCol(int colIdx, long number, int exp) {
      if( colIdx < _nCols ) _nvs[_col = colIdx].addNum(number,exp);
    }

    @Override public final void addInvalidCol(int colIdx) {
      if(colIdx < _nCols) _nvs[_col = colIdx].addNA();
    }
    @Override public final boolean isString(int colIdx) { return false; }

    @Override public final void addStrCol(int colIdx, ValueString str) {
      if(colIdx < _nvs.length){
        if(_ctypes[colIdx] == NCOL){ // support enforced types
          addInvalidCol(colIdx);
          return;
        }
        if(_ctypes[colIdx] == UCOL && ParseTime.attemptTimeParse(str) > 0)
          _ctypes[colIdx] = TCOL;
        if(_ctypes[colIdx] == TCOL){
          long l = ParseTime.attemptTimeParse(str);
          if( l == Long.MIN_VALUE ) addInvalidCol(colIdx);
          else {
            int time_pat = ParseTime.decodePat(l); // Get time pattern
            l = ParseTime.decodeTime(l);           // Get time
            addNumCol(colIdx, l, 0);               // Record time in msec
            _nvs[_col]._timCnt[time_pat]++; // Count histo of time parse patterns
          }
        } else if(!_enums[_col = colIdx].isKilled()) {
          // store enum id into exponent, so that it will be interpreted as NA if compressing as numcol.
          int id = _enums[colIdx].addKey(str);
          if(_ctypes[colIdx] == UCOL && id > 1)_ctypes[colIdx] = ECOL;
          _nvs[colIdx].addEnum(id);
        } else // turn the column into NAs by adding value overflowing Enum.MAX_SIZE
          _nvs[colIdx].addEnum(Integer.MAX_VALUE);
      }
    }

    /** Adds double value to the column. */
    public void addNumCol(int colIdx, double value) {
      if (Double.isNaN(value)) {
        addInvalidCol(colIdx);
      } else {
        double d= value;
        int exp = 0;
        long number = (long)d;
        while (number != d) {
          d = d * 10;
          --exp;
          number = (long)d;
        }
        addNumCol(colIdx, number, exp);
      }
    }
    public void setColumnNames(String [] names){}
    @Override public final void rollbackLine() {}
    @Override public void invalidLine(String err) { newLine(); }
    @Override public void invalidValue(int line, int col) {}
  }

  // ------------------------------------------------------------------------
  /** Parser data in taking data from fluid vec chunk.
   *  @author tomasnykodym
   */
  private static class FVecDataIn implements Parser.DataIn {
    final Vec _vec;
    Chunk _chk;
    int _idx;
    final long _firstLine;
    public FVecDataIn(Chunk chk){
      _chk = chk;
      _idx = _chk.cidx();
      _firstLine = _chk._start;
      _vec = chk._vec;
    }
    @Override public byte[] getChunkData(int cidx) {
      if(cidx != _idx)
        _chk = cidx < _vec.nChunks()?_vec.chunkForChunkIdx(_idx = cidx):null;
      return (_chk == null)?null:_chk.getBytes();
    }
    @Override public int  getChunkDataStart(int cidx) { return -1; }
    @Override public void setChunkDataStart(int cidx, int offset) { }
  }

  // ------------------------------------------------------------------------
  // Log information about the dataset we just parsed.
  private static void logParseResults(ParseDataset2 job, Frame fr) {
    try {
      long numRows = fr.anyVec().length();
      Log.info("Parse result for " + job.dest() + " (" + Long.toString(numRows) + " rows):");

      Vec[] vecArr = fr.vecs();
      for (int i = 0; i < vecArr.length; i++) {
        Vec v = vecArr[i];
        boolean isCategorical = v.isEnum();
        boolean isConstant = v.isConst();
        String CStr = String.format("C%d:", i+1);
        String typeStr = String.format("%s", (isCategorical ? "categorical" : "numeric"));
        String minStr = String.format("min(%f)", v.min());
        String maxStr = String.format("max(%f)", v.max());
        long numNAs = v.naCnt();
        String naStr = (numNAs > 0) ? String.format("na(%d)", numNAs) : "";
        String isConstantStr = isConstant ? "constant" : "";
        String numLevelsStr = isCategorical ? String.format("numLevels(%d)", v.domain().length) : "";

        boolean printLogSeparatorToStdout = false;
        boolean printColumnToStdout;
        {
          // Print information to stdout for this many leading columns.
          final int MAX_HEAD_TO_PRINT_ON_STDOUT = 10;

          // Print information to stdout for this many trailing columns.
          final int MAX_TAIL_TO_PRINT_ON_STDOUT = 10;

          if (vecArr.length <= (MAX_HEAD_TO_PRINT_ON_STDOUT + MAX_TAIL_TO_PRINT_ON_STDOUT)) {
            // For small numbers of columns, print them all.
            printColumnToStdout = true;
          } else if (i < MAX_HEAD_TO_PRINT_ON_STDOUT) {
            printColumnToStdout = true;
          } else if (i == MAX_HEAD_TO_PRINT_ON_STDOUT) {
            printLogSeparatorToStdout = true;
            printColumnToStdout = false;
          } else if ((i + MAX_TAIL_TO_PRINT_ON_STDOUT) < vecArr.length) {
            printColumnToStdout = false;
          } else {
            printColumnToStdout = true;
          }
        }

        if (printLogSeparatorToStdout) {
          System.out.println("Additional column information only sent to log file...");
        }

        if (printColumnToStdout) {
          // Log to both stdout and log file.
          Log.info(String.format("    %-8s %15s %20s %20s %15s %11s %16s", CStr, typeStr, minStr, maxStr, naStr, isConstantStr, numLevelsStr));
        }
        else {
          // Log only to log file.
          Log.info_no_stdout(String.format("    %-8s %15s %20s %20s %15s %11s %16s", CStr, typeStr, minStr, maxStr, naStr, isConstantStr, numLevelsStr));
        }
      }
    }
    catch(Exception ignore) {}   // Don't fail due to logging issues.  Just ignore them.
  }
}

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
import water.nbhm.NonBlockingSetInt;
import water.util.ArrayUtils;
import water.util.FrameUtils;
import water.util.PrettyPrint;
import water.util.Log;

public final class ParseDataset2 extends Job<Frame> {
  private MultiFileParseTask _mfpt; // Access to partially built vectors for cleanup after parser crash

  // Keys are limited to ByteVec Keys and Frames-of-1-ByteVec Keys
  public static Frame parse(Key okey, Key... keys) { return parse(okey,keys,true, false,0/*guess header*/); }

  // Guess setup from inspecting the first Key only, then parse.
  // Suitable for e.g. testing setups, where the data is known to be sane.
  // NOT suitable for random user input!
  public static Frame parse(Key okey, Key[] keys, boolean delete_on_done, boolean singleQuote, int checkHeader) {
    return parse(okey,keys,delete_on_done,setup(keys[0],singleQuote,checkHeader));
  }
  public static Frame parse(Key okey, Key[] keys, boolean delete_on_done, ParseSetup globalSetup) {
    ParseDataset2 job = forkParseDataset(okey,keys,globalSetup,delete_on_done);
    try { return job.get(); }
    catch( Throwable ex ) {

      // Took a crash/NPE somewhere in the parser.  Attempt cleanup.
      Futures fs = new Futures();
      if( job != null ) {
        Keyed.remove(job._dest,fs);
        // Find & remove all partially-built output vecs & chunks
        if( job._mfpt != null ) job._mfpt.onExceptionCleanup(fs);
      }
      // Assume the input is corrupt - or already partially deleted after
      // parsing.  Nuke it all - no partial Vecs lying around.
      for( Key k : keys ) Keyed.remove(k,fs);
      fs.blockForPending();

      throw ex;
    } finally {
      assert DKV.<Job>getGet(job._key).isStopped();
    }
  }

  public static ParseDataset2 startParse2(Key okey, Key[] keys, boolean delete_on_done, ParseSetup globalSetup) {
    return forkParseDataset(okey,keys, globalSetup,delete_on_done); 
  }

  private static ParseSetup setup(Key k, boolean singleQuote, int checkHeader) {
    byte[] bits = ZipUtil.getFirstUnzippedBytes(getByteVec(k));
    ParseSetup globalSetup = ParseSetup.guessSetup(bits, singleQuote, checkHeader);
    if( globalSetup._ncols <= 0 ) throw new UnsupportedOperationException(globalSetup.toString());
    return globalSetup;
  }

  // Allow both ByteVec keys and Frame-of-1-ByteVec
  static ByteVec getByteVec(Key key) {
    Iced ice = DKV.get(key).get();
    return (ByteVec)(ice instanceof ByteVec ? ice : ((Frame)ice).vecs()[0]);
  }
  static String [] genericColumnNames(int ncols){
    String [] res = new String[ncols];
    for(int i = 0; i < res.length; ++i) res[i] = "C" + String.valueOf(i+1);
    return res;
  }

  // Same parse, as a backgroundable Job
  public static ParseDataset2 forkParseDataset(final Key dest, final Key[] keys, final ParseSetup setup, boolean delete_on_done) {
    HashSet<String> conflictingNames = setup.checkDupColumnNames();
    for( String x : conflictingNames )
      throw new IllegalArgumentException("Found duplicate column name "+x);
    // Some quick sanity checks: no overwriting your input key, and a resource check.
    long sum=0;
    for( int i=0; i<keys.length; i++ ) {
      Key k = keys[i];
      if( dest.equals(k) )
        throw new IllegalArgumentException("Destination key "+dest+" must be different from all sources");
      if( delete_on_done )
        for( int j=i+1; j<keys.length; j++ )
          if( k==keys[j] )
            throw new IllegalArgumentException("Source key "+k+" appears twice, delete_on_done must be false");
      sum += getByteVec(k).length(); // Sum of all input filesizes
    }
    long memsz = H2O.CLOUD.memsz();
    if( sum > memsz*4 )
      throw new IllegalArgumentException("Total input file size of "+PrettyPrint.bytes(sum)+" is much larger than total cluster memory of "+PrettyPrint.bytes(memsz)+", please use either a larger cluster or smaller data.");

    // Fire off the parse
    ParseDataset2 job = new ParseDataset2(dest);
    new Frame(job.dest(),new String[0],new Vec[0]).delete_and_lock(job._key); // Write-Lock BEFORE returning
    for( Key k : keys ) Lockable.read_lock(k,job._key); // Read-Lock BEFORE returning
    ParserFJTask fjt = new ParserFJTask(job, keys, setup, delete_on_done); // Fire off background parse
    job.start(fjt, sum);
    return job;
  }
  // Setup a private background parse job
  private ParseDataset2(Key dest) {
    super(dest,"Parse");
  }

  // -------------------------------
  // Simple internal class doing background parsing, with trackable Job status
  public static class ParserFJTask extends water.H2O.H2OCountedCompleter {
    final ParseDataset2 _job;
    final Key[] _keys;
    final ParseSetup _setup;
    final boolean _delete_on_done;

    public ParserFJTask( ParseDataset2 job, Key[] keys, ParseSetup setup, boolean delete_on_done) {
      _job = job;
      _keys = keys;
      _setup = setup;
      _delete_on_done = delete_on_done;
    }
    @Override public void compute2() {
      parse_impl(_job, _keys, _setup, _delete_on_done);
      tryComplete();
    }

    // Took a crash/NPE somewhere in the parser.  Attempt cleanup.
    @Override public boolean onExceptionalCompletion(Throwable ex, CountedCompleter caller){
      if( _job != null ) _job.cancel2(ex);
      return true;
    }
    @Override public void onCompletion(CountedCompleter caller) { _job.done(); }
  }

  private static class EnumMapping extends Iced {
    final int [][] map;
    public EnumMapping(int[][] map){this.map = map;}
  }
  // --------------------------------------------------------------------------
  // Top-level parser driver
  private static void parse_impl(ParseDataset2 job, Key[] fkeys, ParseSetup setup, boolean delete_on_done) {
    assert setup._ncols > 0;
    if( fkeys.length == 0) { job.cancel();  return;  }

    VectorGroup vg = getByteVec(fkeys[0]).group();
    MultiFileParseTask mfpt = job._mfpt = new MultiFileParseTask(vg,setup,job._key,fkeys,delete_on_done);
    mfpt.doAll(fkeys);
    EnumUpdateTask eut = null;
    // Calculate enum domain
    int n = 0;
    int [] ecols = new int[mfpt._dout._nCols];
    for( int i = 0; i < ecols.length; ++i )
      if(mfpt._dout._vecs[i].shouldBeEnum())
        ecols[n++] = i;
    ecols =  Arrays.copyOf(ecols, n);
    if( ecols.length > 0 ) {
      EnumFetchTask eft = new EnumFetchTask(mfpt._eKey, ecols).doAllNodes();
      Enum[] enums = eft._gEnums;
      ValueString[][] ds = new ValueString[ecols.length][];
      EnumMapping [] emaps = new EnumMapping[H2O.CLOUD.size()];
      int k = 0;
      for( int ei : ecols)
        mfpt._dout._vecs[ei].setDomain(ValueString.toString(ds[k++] = enums[ei].computeColumnDomain()));
      for(int nodeId = 0; nodeId < H2O.CLOUD.size(); ++nodeId) {
        if(eft._lEnums[nodeId] == null)continue;
        int[][] emap = new int[ecols.length][];
        for (int i = 0; i < ecols.length; ++i) {
          final Enum e = eft._lEnums[nodeId][ecols[i]];
          if(e == null) continue;
          emap[i] = MemoryManager.malloc4(e.maxId() + 1);
          Arrays.fill(emap[i], -1);
          for (int j = 0; j < ds[i].length; ++j) {
            ValueString vs = ds[i][j];
            if (e.containsKey(vs)) {
              assert e.getTokenId(vs) <= e.maxId() : "maxIdx = " + e.maxId() + ", got " + e.getTokenId(vs);
              emap[i][e.getTokenId(vs)] = j;
            }
          }
        }
        emaps[nodeId] = new EnumMapping(emap);
      }
      eut = new EnumUpdateTask(ds, emaps, mfpt._chunk2Enum);
    }
    Frame fr = new Frame(job.dest(),setup._columnNames != null?setup._columnNames:genericColumnNames(mfpt._dout._nCols),mfpt._dout.closeVecs());
    // SVMLight is sparse format, there may be missing chunks with all 0s, fill them in
    new SVFTask(fr).doAllNodes();
    // Update enums to the globally agreed numbering
    if( eut != null ) {
      Vec[] evecs = new Vec[ecols.length];
      for( int i = 0; i < evecs.length; ++i ) evecs[i] = fr.vecs()[ecols[i]];
      eut.doAll(evecs);
    }
    // unify any vecs with enums and strings to strings only
    new UnifyStrVecTask().doAll(fr);

    // Log any errors
    if( mfpt._errors != null )
      for( String err : mfpt._errors )
        Log.warn(err);
    logParseResults(job, fr);

    // Release the frame for overwriting
    fr.unlock(job._key);
    // Remove CSV files from H2O memory
    if( delete_on_done )
      for( Key k : fkeys )
        assert DKV.get(k) == null : "Input key "+k+" not deleted during parse";
  }

  // --------------------------------------------------------------------------
  /** Task to update enum values to match the global numbering scheme.
   *  Performs update in place so that values originally numbered using
   *  node-local unordered numbering will be numbered using global numbering.
   *  @author tomasnykodym
   */
  private static class EnumUpdateTask extends MRTask<EnumUpdateTask> {
    private final ValueString [][] _gDomain;
    private final EnumMapping [] _emaps;
    private final int  [] _chunk2Enum;
    private EnumUpdateTask(ValueString [][] gDomain, EnumMapping [] emaps, int [] chunk2Enum) {
      _gDomain = gDomain; _emaps = emaps; _chunk2Enum = chunk2Enum;
    }
    private int[][] emap(int nodeId) {return _emaps[nodeId].map;}
    @Override public void map(Chunk [] chks){
      int[][] emap = emap(_chunk2Enum[chks[0].cidx()]);
      final int cidx = chks[0].cidx();
      for(int i = 0; i < chks.length; ++i) {
        Chunk chk = chks[i];
        if(_gDomain[i] == null) // killed, replace with all NAs
          DKV.put(chk.vec().chunkKey(chk.cidx()),new C0DChunk(Double.NaN,chk._len));
        else if (!(chk instanceof CStrChunk)) {
          for( int j = 0; j < chk._len; ++j){
            if( chk.isNA0(j) )continue;
            long l = chk.at80(j);
            if (l < 0 || l >= emap[i].length)
              chk.reportBrokenEnum(i, j, l, emap, _gDomain[i].length);
            if(emap[i][(int)l] < 0)
              throw new RuntimeException(H2O.SELF.toString() + ": missing enum at col:" + i + ", line: " + (chk.start() + j) + ", val = " + l + ", chunk=" + chk.getClass().getSimpleName() + ", map = " + Arrays.toString(emap[i]));
            chk.set0(j, emap[i][(int)l]);
          }
        }
        chk.close(cidx, _fs);
      }
    }
  }

  // --------------------------------------------------------------------------
  private static class EnumFetchTask extends MRTask<EnumFetchTask> {
    private final Key _k;
    private final int[] _ecols;
    private Enum[] _gEnums;      // global enums per column
    public Enum[][] _lEnums;    // local enums per node per column
    private EnumFetchTask(Key k, int[] ecols){_k = k;_ecols = ecols;}
    @Override public void setupLocal() {
      _lEnums = new Enum[H2O.CLOUD.size()][];
      if( !MultiFileParseTask._enums.containsKey(_k) ) return;
      _lEnums[H2O.SELF.index()] = _gEnums = MultiFileParseTask._enums.get(_k);
      // Null out any empty Enum structs; no need to ship these around.
      for( int i=0; i<_gEnums.length; i++ )
        if( _gEnums[i].size()==0 ) _gEnums[i] = null;

      // if we are the original node (i.e. there will be no sending over wire),
      // we have to clone the enums not to share the same object (causes
      // problems when computing column domain and renumbering maps).
//      if( H2O.SELF.index() == _homeNode ) {
      // fixme: looks like need to clone even if not on home node in h2o-dev
        _gEnums = _gEnums.clone();
        for(int i = 0; i < _gEnums.length; ++i)
          if( _gEnums[i] != null ) _gEnums[i] = _gEnums[i].deepCopy();
//      }
      MultiFileParseTask._enums.remove(_k);
    }

    @Override public void reduce(EnumFetchTask etk) {
      if(_gEnums == null) {
        _gEnums = etk._gEnums;
        _lEnums = etk._lEnums;
      } else if (etk._gEnums != null) {
        for( int i : _ecols ) {
          if( _gEnums[i] == null ) _gEnums[i] = etk._gEnums[i];
          else if( etk._gEnums[i] != null ) _gEnums[i].merge(etk._gEnums[i]);
        }
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
            nlines = ((Chunk)val.get())._len;
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
  // Run once on all nodes; switch enum chunks over to string chunks
  private static class UnifyStrVecTask extends MRTask<UnifyStrVecTask> {
    private UnifyStrVecTask() {}

    @Override public void map(Chunk[] chunks) {
      for (Chunk c : chunks) {
        Vec v = c.vec();
        if (v.isString() && c instanceof C4Chunk) {
          Key k = v.chunkKey(c.cidx());
          NewChunk nc = new NewChunk(v, c.cidx());
          for (int j = 0; j < c._len; ++j)
            if (c.isNA0(j)) nc.addNA();
            else nc.addStr(new ValueString(v.domain()[(int) c.at80(j)]));

          H2O.putIfMatch(k, new Value(k, nc.new_close()), H2O.get(k));
        }
      }
    }
  }

  // --------------------------------------------------------------------------
  // We want to do a standard MRTask with a collection of file-keys (so the
  // files are parsed in parallel across the cluster), but we want to throttle
  // the parallelism on each node.
  private static class MultiFileParseTask extends MRTask<MultiFileParseTask> {
    private final ParseSetup _setup; // The expected column layout
    private final VectorGroup _vg;    // vector group of the target dataset
    private final int _vecIdStart;    // Start of available vector keys
    // Shared against all concurrent unrelated parses, a map to the node-local
    // Enum lists for each concurrent parse.
    private static NonBlockingHashMap<Key, Enum[]> _enums = new NonBlockingHashMap<>();
    // The Key used to sort out *this* parse's Enum[]
    private final Key _eKey = Key.make();
    // Eagerly delete Big Data
    private final boolean _delete_on_done;
    // Mapping from Chunk# to cluster-node-number holding the enum mapping.
    // It is either self for all the non-parallel parses, or the Chunk-home for parallel parses.
    private int[] _chunk2Enum;
    // Job Key, to unlock & remove raw parsed data; to report progress
    private final Key _job_key;
    // A mapping of Key+ByteVec to rolling total Chunk counts.
    private final int[]  _fileChunkOffsets;

    // OUTPUT fields:
    FVecDataOut _dout;
    String[] _errors;

    MultiFileParseTask(VectorGroup vg,  ParseSetup setup, Key job_key, Key[] fkeys, boolean delete_on_done ) {
      _vg = vg; _setup = setup; 
      _vecIdStart = _vg.reserveKeys(_setup._pType == ParserType.SVMLight ? 100000000 : setup._ncols);
      _delete_on_done = delete_on_done;
      _job_key = job_key;

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
    private static Enum[] enums(Key eKey, int ncols) {
      Enum[] enums = _enums.get(eKey);
      if( enums != null ) return enums;
      enums = new Enum[ncols];
      for( int i = 0; i < enums.length; ++i ) enums[i] = new Enum();
      _enums.putIfAbsent(eKey, enums);
      return _enums.get(eKey); // Re-get incase lost insertion race
    }

    // Flag all chunk enums as being on local (self)
    private void chunksAreLocal( Vec vec, int chunkStartIdx, Key key ) {
      for(int i = 0; i < vec.nChunks(); ++i)  
        _chunk2Enum[chunkStartIdx + i] = H2O.SELF.index();
      // For Big Data, must delete data as eagerly as possible.
      Iced ice = DKV.get(key).get();
      if( ice==vec ) {
        if( _delete_on_done ) vec.remove();
      } else {
        Frame fr = (Frame)ice;
        if( _delete_on_done ) fr.delete(_job_key,new Futures()).blockForPending();
        else if( fr._key != null ) fr.unlock(_job_key);
      }
    }

    // Called once per file
    @Override public void map( Key key ) {
      // Get parser setup info for this chunk
      ByteVec vec = getByteVec(key);
      final int chunkStartIdx = _fileChunkOffsets[_lo];
      byte[] zips = vec.getFirstBytes();
      ZipUtil.Compression cpr = ZipUtil.guessCompressionMethod(zips);
      byte[] bits = ZipUtil.unzipBytes(zips,cpr);
      ParseSetup localSetup = _setup.guessSetup(bits,0/*guess header in each file*/);
      if( !localSetup._isValid ) {
        _errors = localSetup._errors;
        chunksAreLocal(vec,chunkStartIdx,key);
        return;
      }
      // Parse the file
      try {
        switch( cpr ) {
        case NONE:
          if( localSetup._pType._parallelParseSupported ) {
            DParse dp = new DParse(_vg, localSetup, _vecIdStart, chunkStartIdx,this,key);
            addToPendingCount(1);
            dp.setCompleter(this);
            dp.asyncExec(vec);
            for( int i = 0; i < vec.nChunks(); ++i )
              _chunk2Enum[chunkStartIdx + i] = vec.chunkKey(i).home_node().index();
          } else {
            InputStream bvs = vec.openStream(_job_key);
            _dout = streamParse(bvs, localSetup, _vecIdStart, chunkStartIdx, bvs);
            chunksAreLocal(vec,chunkStartIdx,key);
          }
          break;
        case ZIP: {
          // Zipped file; no parallel decompression;
          InputStream bvs = vec.openStream(_job_key);
          ZipInputStream zis = new ZipInputStream(bvs);
          ZipEntry ze = zis.getNextEntry(); // Get the *FIRST* entry
          // There is at least one entry in zip file and it is not a directory.
          if( ze != null && !ze.isDirectory() ) 
            _dout = streamParse(zis,localSetup, _vecIdStart, chunkStartIdx, bvs);
          else zis.close();       // Confused: which zipped file to decompress
          chunksAreLocal(vec,chunkStartIdx,key);
          break;
        }
        case GZIP: {
          InputStream bvs = vec.openStream(_job_key);
          // Zipped file; no parallel decompression;
          _dout = streamParse(new GZIPInputStream(bvs),localSetup,_vecIdStart, chunkStartIdx,bvs);
          // set this node as the one which processed all the chunks
          chunksAreLocal(vec,chunkStartIdx,key);
          break;
        }
        }
      } catch( IOException ioe ) {
        throw new RuntimeException(ioe);
      }
    }

    // Reduce: combine errors from across files.
    // Roll-up other meta data
    @Override public void reduce( MultiFileParseTask mfpt ) {
      assert this != mfpt;
      // Collect & combine columns across files
      if( _dout == null ) _dout = mfpt._dout;
      else _dout.reduce(mfpt._dout);
      if( _chunk2Enum == null ) _chunk2Enum = mfpt._chunk2Enum;
      else if(_chunk2Enum != mfpt._chunk2Enum) { // we're sharing global array!
        for( int i = 0; i < _chunk2Enum.length; ++i ) {
          if( _chunk2Enum[i] == -1 ) _chunk2Enum[i] = mfpt._chunk2Enum[i];
          else assert mfpt._chunk2Enum[i] == -1 : Arrays.toString(_chunk2Enum) + " :: " + Arrays.toString(mfpt._chunk2Enum);
        }
      }
      _errors = ArrayUtils.append(_errors,mfpt._errors);
    }

    // ------------------------------------------------------------------------
    // Zipped file; no parallel decompression; decompress into local chunks,
    // parse local chunks; distribute chunks later.
    private FVecDataOut streamParse( final InputStream is, final ParseSetup localSetup, int vecIdStart, int chunkStartIdx, InputStream bvs) throws IOException {
      // All output into a fresh pile of NewChunks, one per column
      FVecDataOut dout = new FVecDataOut(_vg, chunkStartIdx, localSetup._ncols, vecIdStart, enums(_eKey,_setup._ncols), null);
      Parser p = localSetup.parser();
      // assume 2x inflation rate
      if( localSetup._pType._parallelParseSupported ) p.streamParseZip(is, dout, bvs);
      else                                            p.streamParse   (is, dout);
      // Parse all internal "chunks", until we drain the zip-stream dry.  Not
      // real chunks, just flipping between 32K buffers.  Fills up the single
      // very large NewChunk.
      dout.close(_fs);
      return dout;
    }

    // ------------------------------------------------------------------------
    private static class DParse extends MRTask<DParse> {
      private final ParseSetup _setup;
      private final int _vecIdStart;
      private final int _startChunkIdx; // for multifile parse, offset of the first chunk in the final dataset
      private final VectorGroup _vg;
      private FVecDataOut _dout;
      private final Key _eKey;  // Parse-local-Enums key
      private final Key _job_key;
      private transient final MultiFileParseTask _outerMFPT;
      private transient final Key _srckey; // Source/text file to delete on done
      private transient NonBlockingSetInt _visited;

      DParse(VectorGroup vg, ParseSetup setup, int vecIdstart, int startChunkIdx, MultiFileParseTask mfpt, Key srckey) {
        super(mfpt);
        _vg = vg;
        _setup = setup;
        _vecIdStart = vecIdstart;
        _startChunkIdx = startChunkIdx;
        _outerMFPT = mfpt;
        _eKey = mfpt._eKey;
        _job_key = mfpt._job_key;
        _srckey = srckey;
      }
      @Override public void setupLocal(){
        super.setupLocal();
        _visited = new NonBlockingSetInt();
      }
      @Override public void map( Chunk in ) {
        Enum [] enums = enums(_eKey,_setup._ncols);
        // Break out the input & output vectors before the parse loop
        FVecDataIn din = new FVecDataIn(in);
        FVecDataOut dout;
        Parser p;
        switch(_setup._pType) {
        case CSV:
          p = new CsvParser(_setup);
          dout = new FVecDataOut(_vg,_startChunkIdx + in.cidx(),_setup._ncols,_vecIdStart,enums, null);
          break;
        case ARFF:
          p = new CsvParser(_setup);
          dout = new FVecDataOut(_vg,_startChunkIdx + in.cidx(),_setup._ncols,_vecIdStart,enums, _setup._ctypes); //TODO: use _setup._domains instead of enums
          break;
        case SVMLight:
          p = new SVMLightParser(_setup);
          dout = new SVMLightFVecDataOut(_vg, _startChunkIdx + in.cidx(), enums);
          break;
        default:
          throw H2O.unimpl();
        }
        p.parallelParse(in.cidx(),din,dout);
        (_dout = dout).close(_fs);
        Job.update(in._len,_job_key); // Record bytes parsed

        // remove parsed data right away (each chunk is used by 2)
        freeMem(in,0);
        freeMem(in,1);
      }
      private void freeMem(Chunk in, int off) {
        final int cidx = in.cidx()+off;
        if( _visited.add(cidx) ) return; // First visit; expect a 2nd so no freeing yet
        Value v = H2O.get(in.vec().chunkKey(cidx));
        if( v == null || !v.isPersisted() ) return; // Not found, or not on disk somewhere
        v.freePOJO();           // Eagerly toss from memory
        v.freeMem();
      }
      @Override public void reduce(DParse dp) { _dout.reduce(dp._dout); }
      @Override public void postGlobal() {
        super.postGlobal();
        _outerMFPT._dout = _dout;
        _dout = null;           // Reclaim GC eagerly
        // For Big Data, must delete data as eagerly as possible.
        Value val = DKV.get(_srckey);
        if( val == null ) return;
        Iced ice = val.get();
        if( ice instanceof ByteVec ) {
          if( _outerMFPT._delete_on_done ) ((ByteVec)ice).remove();
        } else {
          Frame fr = (Frame)ice;
          if( _outerMFPT._delete_on_done ) fr.delete(_outerMFPT._job_key,new Futures()).blockForPending();
          else if( fr._key != null ) fr.unlock(_outerMFPT._job_key);
        }
      }
    }

    // Find & remove all partially built output chunks & vecs
    private Futures onExceptionCleanup(Futures fs) {
      int nchunks = _chunk2Enum.length;
      int ncols = _setup._ncols;
      for( int i = 0; i < ncols; ++i ) {
        Key vkey = _vg.vecKey(_vecIdStart + i);
        Keyed.remove(vkey,fs);
        for( int c = 0; c < nchunks; ++c )
          DKV.remove(Vec.chunkKey(vkey,c),fs);
      }
      cancel(true);
      return fs;
    }
  }

  // ------------------------------------------------------------------------
  /** Parsed data output specialized for fluid vecs.
   * @author tomasnykodym
   */
  static class FVecDataOut extends Iced implements Parser.StreamDataOut {
    protected transient NewChunk [] _nvs;
    protected AppendableVec []_vecs;
    protected final Enum [] _enums;
    protected transient byte [] _ctypes;
    long _nLines;
    int _nCols;
    int _col = -1;
    final int _cidx;
    final int _vecIdStart;
    boolean _closedVecs = false;
    private final VectorGroup _vg;

    static final byte UCOL = 0; // unknown col type
    static final byte NCOL = 1; // numeric col type
    static final byte ECOL = 2; // enum    col type
    static final byte TCOL = 3; // time    col typ
    static final byte ICOL = 4; // UUID    col typ
    static final byte SCOL = 5; // String  col typ

    private FVecDataOut(VectorGroup vg, int cidx, int ncols, int vecIdStart, Enum[] enums, byte[] ctypes){
      _ctypes = ctypes == null ? MemoryManager.malloc1(ncols) : ctypes;
      _vecs = new AppendableVec[ncols];
      _nvs = new NewChunk[ncols];
      _enums = enums;
      _nCols = ncols;
      _cidx = cidx;
      _vg = vg;
      _vecIdStart = vecIdStart;
      for(int i = 0; i < ncols; ++i)
        _nvs[i] = (_vecs[i] = new AppendableVec(vg.vecKey(vecIdStart + i))).chunkForChunkIdx(_cidx);
    }

    @Override public FVecDataOut reduce(Parser.StreamDataOut sdout){
      FVecDataOut dout = (FVecDataOut)sdout;
      if( dout == null ) return this;
      _nCols = Math.max(_nCols,dout._nCols);
      if(dout._vecs.length > _vecs.length){
        AppendableVec [] v = _vecs;
        _vecs = dout._vecs;
        dout._vecs = v;
      }
      for(int i = 0; i < dout._vecs.length; ++i) {
        // unify string and enum chunks
        if (_vecs[i].isString() && !dout._vecs[i].isString())
          dout.enumCol2StrCol(i);
        else if (!_vecs[i].isString() && dout._vecs[i].isString()) {
          enumCol2StrCol(i);
          _ctypes[i] = SCOL;
        }

        _vecs[i].reduce(dout._vecs[i]);
      }
      return this;
    }
    @Override public FVecDataOut close(){
      Futures fs = new Futures();
      close(fs);
      fs.blockForPending();
      return this;
    }
    @Override public FVecDataOut close(Futures fs){
      if( _nvs == null ) return this; // Might call close twice
      for(NewChunk nv:_nvs) nv.close(_cidx, fs);
      _nvs = null;  // Free for GC
      return this;
    }
    @Override public FVecDataOut nextChunk(){
      return  new FVecDataOut(_vg, _cidx+1, _nCols, _vecIdStart, _enums, null);
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
    @Override public void addNumCol(int colIdx, long number, int exp) {
      if( colIdx < _nCols ) {
        _nvs[_col = colIdx].addNum(number, exp);
        if(_ctypes[colIdx] == UCOL ) _ctypes[colIdx] = NCOL;
      }
    }

    @Override public final void addInvalidCol(int colIdx) {
      if(colIdx < _nCols) _nvs[_col = colIdx].addNA();
    }
    @Override public final boolean isString(int colIdx) { return (colIdx < _nCols) &&  (_ctypes[colIdx]==ECOL || _ctypes[colIdx]==SCOL);}

    @Override public final void addStrCol(int colIdx, ValueString str) {
      if(colIdx < _nvs.length){
        if(_ctypes[colIdx] == NCOL){ // support enforced types
          addInvalidCol(colIdx);
          return;
        }
        if(_ctypes[colIdx] == UCOL && ParseTime.attemptTimeParse(str) > 0)
          _ctypes[colIdx] = TCOL;
        if( _ctypes[colIdx] == UCOL ) { // Attempt UUID parse
          int old = str.get_off();
          ParseTime.attemptUUIDParse0(str);
          ParseTime.attemptUUIDParse1(str);
          if( str.get_off() != -1 ) _ctypes[colIdx] = ICOL;
          str.setOff(old);
        }

        if( _ctypes[colIdx] == TCOL ) {
          long l = ParseTime.attemptTimeParse(str);
          if( l == Long.MIN_VALUE ) addInvalidCol(colIdx);
          else {
            int time_pat = ParseTime.decodePat(l); // Get time pattern
            l = ParseTime.decodeTime(l);           // Get time
            addNumCol(colIdx, l, 0);               // Record time in msec
            _nvs[_col]._timCnt[time_pat]++; // Count histo of time parse patterns
          }
        } else if( _ctypes[colIdx] == ICOL ) { // UUID column?  Only allow UUID parses
          long lo = ParseTime.attemptUUIDParse0(str);
          long hi = ParseTime.attemptUUIDParse1(str);
          if( str.get_off() == -1 )  { lo = C16Chunk._LO_NA; hi = C16Chunk._HI_NA; }
          if( colIdx < _nCols ) _nvs[_col = colIdx].addUUID(lo, hi);
        } else if( _ctypes[colIdx] == SCOL ) {
          _nvs[_col = colIdx].addStr(str);
        } else {
          if(!_enums[colIdx].isMapFull()) {
            int id = _enums[_col = colIdx].addKey(str);
            if (_ctypes[colIdx] == UCOL && id > 1) _ctypes[colIdx] = ECOL;
            _nvs[colIdx].addEnum(id);
          } else { // maxed out enum map, convert col to string chunk
            _ctypes[_col = colIdx] = SCOL;
            enumCol2StrCol(colIdx);
            _nvs[colIdx].addStr(str);
          }
        }
      }
    }

    private void enumCol2StrCol(int colIdx) {
      //build local value2key map for enums
      Enum enums = _enums[colIdx].deepCopy();
      ValueString emap[] = new ValueString[enums.maxId()+1];
      ValueString keys[] = enums._map.keySet().toArray(new ValueString[enums.size()]);
      for (ValueString str:keys)
        // adjust for enum ids using 1-based indexing
        emap[enums._map.get(str)-1] = str;

      //swap in string NewChunk in place of enum NewChunk
      _nvs[colIdx] = _nvs[colIdx].convertEnum2Str(emap);
      //Log.info("enumCol2StrCol");
    }

    /** Adds double value to the column. */
    @Override public void addNumCol(int colIdx, double value) {
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
    @Override public void setColumnNames(String [] names){}
    @Override public final void rollbackLine() {}
    @Override public void invalidLine(String err) { newLine(); }
  }

  // --------------------------------------------------------
  private static class SVMLightFVecDataOut extends FVecDataOut {
    protected final VectorGroup _vg;
    private SVMLightFVecDataOut(VectorGroup vg, int cidx, Enum [] enums){
      super(vg,cidx,0,vg.reserveKeys(10000000),enums, null);
      _nvs = new NewChunk[0];
      _vg = vg;
      _col = 0;
    }
    
    private void addColumns(int ncols){
      if(ncols > _nCols){
        _nvs   = Arrays.copyOf(_nvs   , ncols);
        _vecs  = Arrays.copyOf(_vecs  , ncols);
        _ctypes= Arrays.copyOf(_ctypes, ncols);
        for(int i = _nCols; i < ncols; ++i){
          _vecs[i] = new AppendableVec(_vg.vecKey(_vecIdStart + i + 1));
          _nvs[i] = new NewChunk(_vecs[i], _cidx);
          for(int j = 0; j < _nLines; ++j)
            _nvs[i].addNum(0, 0);
        }
        _nCols = ncols;
      }
    }
    @Override public void addNumCol(int colIdx, long number, int exp) {
      assert colIdx >= _col;
      addColumns(colIdx+1);
      for(int i = _col; i < colIdx; ++i)
        super.addNumCol(i, 0, 0);
      super.addNumCol(colIdx, number, exp);
      _col = colIdx+1;
    }
    @Override public void newLine() {
      if(_col < _nCols)addNumCol(_nCols-1, 0,0);
      super.newLine();
      _col = 0;
    }
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
      _firstLine = chk.start();
      _vec = chk.vec();
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
      Futures fs = new Futures();
      Vec[] vecArr = fr.vecs();
      for(Vec v:vecArr)  v.startRollupStats(fs);
      fs.blockForPending();
      int namelen = 0;
      for (String s : fr.names()) namelen = Math.max(namelen, s.length());
      String format = " %"+namelen+"s %11s %12s %12s %11s %8s %6s";
      Log.info(String.format(format, "Col", "type", "min", "max", "NAs", "constant", "numLevels"));
      // get all rollups started in parallell, otherwise this takes ages!

      for( int i = 0; i < vecArr.length; i++ ) {
        Vec v = vecArr[i];
        boolean isCategorical = v.isEnum();
        boolean isConstant = v.isConst();
        boolean isString = v.isString();
        String CStr = String.format("%"+namelen+"s:", fr.names()[i]);
        String typeStr = String.format("%s", (v.isUUID() ? "UUID" : (isCategorical ? "categorical" : (isString ? "string" : "numeric"))));
        String minStr = isString ? "" : String.format("%g", v.min());
        String maxStr = isString ? "" : String.format("%g", v.max());
        long numNAs = v.naCnt();
        String naStr = (numNAs > 0) ? String.format("%d", numNAs) : "";
        String isConstantStr = isConstant ? "constant" : "";
        String numLevelsStr = isCategorical ? String.format("%d", v.domain().length) : (isString ? String.format("%d", v.nzCnt()) : "");

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

        String s = String.format(format, CStr, typeStr, minStr, maxStr, naStr, isConstantStr, numLevelsStr);
        if( printColumnToStdout ) Log.info          (s);
        else                      Log.info_no_stdout(s);
      }
      Log.info(FrameUtils.chunkSummary(fr).toString());
    }
    catch(Exception ignore) {}   // Don't fail due to logging issues.  Just ignore them.
  }
}

package water.parser;

import jsr166y.CountedCompleter;
import jsr166y.ForkJoinTask;
import jsr166y.RecursiveAction;
import water.*;
import water.H2O.H2OCountedCompleter;
import water.exceptions.H2OIllegalArgumentException;
import water.exceptions.H2OParseException;
import water.fvec.*;
import water.fvec.Vec.VectorGroup;
import water.nbhm.NonBlockingHashMap;
import water.nbhm.NonBlockingSetInt;
import water.util.*;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class ParseDataset extends Job<Frame> {
  private MultiFileParseTask _mfpt; // Access to partially built vectors for cleanup after parser crash

  // Keys are limited to ByteVec Keys and Frames-of-1-ByteVec Keys
  public static Frame parse(Key okey, Key... keys) { return parse(okey,keys,true, false, ParseSetup.GUESS_HEADER); }

  // Guess setup from inspecting the first Key only, then parse.
  // Suitable for e.g. testing setups, where the data is known to be sane.
  // NOT suitable for random user input!
  public static Frame parse(Key okey, Key[] keys, boolean deleteOnDone, boolean singleQuote, int checkHeader) {
    return parse(okey,keys,deleteOnDone,ParseSetup.guessSetup(keys, singleQuote, checkHeader));
  }
  public static Frame parse(Key okey, Key[] keys, boolean deleteOnDone, ParseSetup globalSetup) {
    return parse(okey,keys,deleteOnDone,globalSetup,true).get();
  }
  public static ParseDataset parse(Key okey, Key[] keys, boolean deleteOnDone, ParseSetup globalSetup, boolean blocking) {
    ParseDataset job = forkParseDataset(okey, keys, globalSetup, deleteOnDone);
    if( blocking )
      job.get();
    return job;
  }

  // Allow both ByteVec keys and Frame-of-1-ByteVec
  static ByteVec getByteVec(Key key) {
    Iced ice = DKV.getGet(key);
    if(ice == null)
      throw new H2OIllegalArgumentException("Missing data","Did not find any data under key " + key);
    return (ByteVec)(ice instanceof ByteVec ? ice : ((Frame)ice).vecs()[0]);
  }
  static String [] getColumnNames(int ncols, String[] colNames) {
    if(colNames == null) { // no names, generate
      colNames = new String[ncols];
      for(int i=0; i < ncols; i++ )
        colNames[i] = "C" + String.valueOf(i+1);
    } else { // some or all names exist, fill in blanks
      HashSet<String> nameSet = new HashSet<>(Arrays.asList(colNames));
      colNames = Arrays.copyOf(colNames, ncols);
      for(int i=0; i < ncols; i++ ) {
        if (colNames[i] == null || colNames[i].equals("")) {
          String tmp = "C" + String.valueOf(i + 1);
          while (nameSet.contains(tmp)) // keep building name until unique
            tmp = tmp + tmp;
          colNames[i] = tmp;
        }
      }
    }
    return colNames;
  }

  // Same parse, as a backgroundable Job
  public static ParseDataset forkParseDataset(final Key dest, final Key[] keys, final ParseSetup setup, boolean deleteOnDone) {
    HashSet<String> conflictingNames = setup.checkDupColumnNames();
    for( String x : conflictingNames )
    if ( !x.equals(""))
      throw new IllegalArgumentException("Found duplicate column name "+x);
    // Some quick sanity checks: no overwriting your input key, and a resource check.
    long totalParseSize=0;
    for( int i=0; i<keys.length; i++ ) {
      Key k = keys[i];
      if( dest.equals(k) )
        throw new IllegalArgumentException("Destination key "+dest+" must be different from all sources");
      if( deleteOnDone )
        for( int j=i+1; j<keys.length; j++ )
          if( k==keys[j] )
            throw new IllegalArgumentException("Source key "+k+" appears twice, deleteOnDone must be false");

      // estimate total size in bytes
      totalParseSize += getByteVec(k).length();
    }

    // set the parse chunk size for files
    for( int i = 0; i < keys.length; ++i ) {
      Iced ice = DKV.getGet(keys[i]);
      if(ice instanceof FileVec) {
        ((FileVec) ice).setChunkSize(setup._chunk_size);
        Log.info("Parse chunk size " + setup._chunk_size);
      } else if(ice instanceof Frame && ((Frame)ice).vec(0) instanceof FileVec) {
        ((FileVec) ((Frame) ice).vec(0)).setChunkSize((Frame) ice, setup._chunk_size);
        Log.info("Parse chunk size " + setup._chunk_size);
      }
    }

    long memsz = H2O.CLOUD.memsz();
    if( totalParseSize > memsz*4 )
      throw new IllegalArgumentException("Total input file size of "+PrettyPrint.bytes(totalParseSize)+" is much larger than total cluster memory of "+PrettyPrint.bytes(memsz)+", please use either a larger cluster or smaller data.");

    if (H2O.GA != null)
      GAUtils.logParse(totalParseSize, keys.length, setup._number_columns);

    // Fire off the parse
    ParseDataset job = new ParseDataset(dest);
    new Frame(job.dest(),new String[0],new Vec[0]).delete_and_lock(job._key); // Write-Lock BEFORE returning
    for( Key k : keys ) Lockable.read_lock(k,job._key); // Read-Lock BEFORE returning
    ParserFJTask fjt = new ParserFJTask(job, keys, setup, deleteOnDone); // Fire off background parse
    job.start(fjt, totalParseSize);
    return job;
  }

  // Setup a private background parse job
  private ParseDataset(Key dest) {
    super(dest,"Parse");
  }

  // -------------------------------
  // Simple internal class doing background parsing, with trackable Job status
  public static class ParserFJTask extends water.H2O.H2OCountedCompleter {
    final ParseDataset _job;
    final Key[] _keys;
    final ParseSetup _setup;
    final boolean _deleteOnDone;

    public ParserFJTask( ParseDataset job, Key[] keys, ParseSetup setup, boolean deleteOnDone) {
      _job = job;
      _keys = keys;
      _setup = setup;
      _deleteOnDone = deleteOnDone;
    }
    @Override public void compute2() {
      parseAllKeys(_job, _keys, _setup, _deleteOnDone);
      tryComplete();
    }

    // Took a crash/NPE somewhere in the parser.  Attempt cleanup.
    @Override public boolean onExceptionalCompletion(Throwable ex, CountedCompleter caller){
      if( _job != null ) {
        _job.cancel();
        parseCleanup();
        _job._mfpt = null;
        if (ex instanceof H2OParseException) throw (H2OParseException) ex;
        else _job.failed(ex);
      }
      return true;
    }

    @Override public void onCompletion(CountedCompleter caller) {
      if (_job.isCancelledOrCrashed())
        parseCleanup();
      else
        _job.done();
      _job._mfpt = null;
    }

    private void parseCleanup() {
      if( _job != null ) {
        Futures fs = new Futures();
        assert DKV.<Job>getGet(_job._key).isStopped();
        // Find & remove all partially-built output vecs & chunks
        if (_job._mfpt != null) _job._mfpt.onExceptionCleanup(fs);
        // Assume the input is corrupt - or already partially deleted after
        // parsing.  Nuke it all - no partial Vecs lying around.
        for (Key k : _keys) Keyed.remove(k, fs);
        Keyed.remove(_job._dest,fs);
        _job._mfpt = null;
        fs.blockForPending();
        DKV.put(_job._key, _job);
      }
    }
  }

  private static class EnumMapping extends Iced {
    final int [][] map;
    public EnumMapping(int[][] map){this.map = map;}
  }
  // --------------------------------------------------------------------------
  // Top-level parser driver
  private static void parseAllKeys(ParseDataset job, Key[] fkeys, ParseSetup setup, boolean deleteOnDone) {
    assert setup._number_columns > 0;
    if( setup._column_names != null &&
        ( (setup._column_names.length == 0) ||
          (setup._column_names.length == 1 && setup._column_names[0].isEmpty())) )
      setup._column_names = null; // // FIXME: annoyingly front end sends column names as String[] {""} even if setup returned null
    if(setup._na_strings != null && setup._na_strings.length != setup._number_columns) setup._na_strings = null;
    if( fkeys.length == 0) { job.cancel();  return;  }

    //create a list of keys that are greater than 0-bytes
    List<Key> keyList = new ArrayList<>(fkeys.length);
    for (int i=0; i < fkeys.length; i++)
      if (getByteVec(fkeys[i]).length() > 0)
        keyList.add(fkeys[i]);
    fkeys = keyList.toArray(new Key[keyList.size()]);

    job.update(0, "Ingesting files.");
    VectorGroup vg = getByteVec(fkeys[0]).group();
    MultiFileParseTask mfpt = job._mfpt = new MultiFileParseTask(vg,setup,job._key,fkeys,deleteOnDone);
    mfpt.doAll(fkeys);
    Log.trace("Done ingesting files.");
    if ( job.isCancelledOrCrashed()) return;

    final AppendableVec [] avs = mfpt.vecs();
    setup._column_names = getColumnNames(avs.length, setup._column_names);

    Frame fr = null;
    // Calculate enum domain
    // Filter down to columns with some enums
    int n = 0;
    int[] ecols2 = new int[avs.length];
    for( int i = 0; i < ecols2.length; ++i )
      if( avs[i].shouldBeEnum()  )
        ecols2[n++] = i;
    final int[] ecols = Arrays.copyOf(ecols2, n);
    // If we have any, go gather unified enum domains
    if( n > 0 ) {
      EnumFetchTask eft = new EnumFetchTask(mfpt._eKey, ecols).doAllNodes();
      final Categorical[] enums = eft._gEnums;
      final ValueString[][] ds = new ValueString[ecols.length][];
      EnumMapping [] emaps = new EnumMapping[H2O.CLOUD.size()];
      H2OCountedCompleter[] domtasks = new H2OCountedCompleter[ecols.length];
      // In parallel, compute enum column domains.  Includes expensive sort.
      for( int k = 0; k<ecols.length; k++ ) {
        final int fk = k;
        H2O.submitTask(domtasks[k] = new H2OCountedCompleter() {
            @Override public void compute2() {
              int ei = ecols[fk];
              avs[ei].setDomain(ValueString.toString(ds[fk] = enums[ei].computeColumnDomain()));
              tryComplete();
            }
          });
      }
      for( int k = 0; k<ecols.length; k++ ) domtasks[k].join();

      for(int nodeId = 0; nodeId < H2O.CLOUD.size(); ++nodeId) {
        if(eft._lEnums[nodeId] == null)continue;
        int[][] emap = new int[ecols.length][];
        for (int i = 0; i < ecols.length; ++i) {
          final Categorical e = eft._lEnums[nodeId][ecols[i]];
          if(e == null) continue;
          int maxid = e.maxId();
          emap[i] = MemoryManager.malloc4(maxid + 1);
          Arrays.fill(emap[i], -1);
          for (int j = 0; j < ds[i].length; ++j) {
            ValueString vs = ds[i][j];
            if (e.containsKey(vs)) {
              int tokid = e.getTokenId(vs);
              assert tokid <= maxid : "maxIdx = " + maxid + ", got " + tokid;
              emap[i][tokid] = j;
            }
          }
        }
        emaps[nodeId] = new EnumMapping(emap);
      }
      // Check for job cancellation
      if ( job.isCancelledOrCrashed()) return;

      job.update(0,"Compressing data.");
      fr = new Frame(job.dest(), setup._column_names,AppendableVec.closeAll(avs));
      Log.trace("Done closing all Vecs.");

      // Check for job cancellation
      if ( job.isCancelledOrCrashed()) return;
      // Some cols with enums lose their enum status (because they have more
      // number chunks than enum chunks); these no longer need (or want) enum
      // updating.
      job.update(0,"Unifying categoricals across nodes.");
      Vec[] vecs = fr.vecs();
      int j=0;
      for( int i=0; i<ecols.length; i++ ) {
        if( vecs[ecols[i]].isEnum() ) {
          ecols[j] = ecols[i];
          ds[j] = ds[i];
          for( int l=0; l<emaps.length; l++ ) 
            if( emaps[l] != null ) 
              emaps[l].map[j] = emaps[l].map[i];
          j++;
        }
      }
      // Update enums to the globally agreed numbering
      Vec[] evecs = new Vec[j];
      for( int i = 0; i < evecs.length; ++i ) evecs[i] = fr.vecs()[ecols[i]];
      new EnumUpdateTask(ds, emaps, mfpt._chunk2Enum).doAll(evecs);
      Log.trace("Done unifying categoricals across nodes.");

    } else {                    // No enums case
      job.update(0,"Compressing data.");
      fr = new Frame(job.dest(), setup._column_names,AppendableVec.closeAll(avs));
      Log.trace("Done closing all Vecs.");
    }
    // Check for job cancellation
    if ( job.isCancelledOrCrashed()) return;

    // SVMLight is sparse format, there may be missing chunks with all 0s, fill them in
    if (setup._parse_type == ParserType.SVMLight)
      new SVFTask(fr).doAllNodes();

    // Log any errors
    if( mfpt._errors != null )
      for( String err : mfpt._errors )
        Log.warn(err);
    job.update(0,"Calculating data summary.");
    logParseResults(job, fr);
    // Release the frame for overwriting
    fr.update(job._key);
    Frame fr2 = DKV.getGet(fr._key);
    assert fr2._names.length == fr2.numCols();
    fr.unlock(job._key);
    // Remove CSV files from H2O memory
    if( deleteOnDone )
      for( Key k : fkeys )
        assert DKV.get(k) == null : "Input key "+k+" not deleted during parse";
  }

  // --------------------------------------------------------------------------
  /** Task to update enum (categorical) values to match the global numbering scheme.
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
            if( chk.isNA(j) )continue;
            long l = chk.at8(j);
            if (l < 0 || l >= emap[i].length)
              chk.reportBrokenEnum(i, j, l, emap, _gDomain[i].length);
            if(emap[i][(int)l] < 0)
              throw new RuntimeException(H2O.SELF.toString() + ": missing enum at col:" + i + ", line: " + (chk.start() + j) + ", val = " + l + ", chunk=" + chk.getClass().getSimpleName() + ", map = " + Arrays.toString(emap[i]));
            chk.set(j, emap[i][(int) l]);
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
    private Categorical[] _gEnums;      // global enums per column
    public Categorical[][] _lEnums;    // local enums per node per column
    private EnumFetchTask(Key k, int[] ecols){_k = k;_ecols = ecols;}
    @Override public void setupLocal() {
      _lEnums = new Categorical[H2O.CLOUD.size()][];
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
          else if( etk._gEnums[i] != null ) {
            _gEnums[i].merge(etk._gEnums[i]);
            if (_gEnums[i].isMapFull())
              throw new H2OParseException("Column contains over "+Categorical.MAX_ENUM_SIZE
              +" unique values and exceeds limits.  Consider parsing this column as string values.");
          }
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
    @Override public void setupLocal() {
      if( _f.numCols() == 0 ) return;
      Vec v0 = _f.anyVec();
      ArrayList<RecursiveAction> rs = new ArrayList<RecursiveAction>();
      for( int i = 0; i < v0.nChunks(); ++i ) {
        if( !v0.chunkKey(i).home() ) continue;
        final int fi = i;
        rs.add(new RecursiveAction() {
          @Override
          protected void compute() {
            // First find the nrows as the # rows of non-missing chunks; done on
            // locally-homed chunks only - to keep the data distribution.
            int nlines = 0;
            for( Vec vec : _f.vecs() ) {
              Value val = H2O.get(vec.chunkKey(fi)); // Local-get only
              if( val != null ) {
                nlines = ((Chunk)val.get())._len;
                break;
              }
            }
            final int fnlines = nlines;
            // Now fill in appropriate-sized zero chunks
            for(int j = 0; j < _f.numCols(); ++j) {
              Vec vec = _f.vec(j);
              Key k = vec.chunkKey(fi);
              Value val = H2O.get(k);   // Local-get only
              if( val == null )         // Missing?  Fill in w/zero chunk
                H2O.putIfMatch(k, new Value(k, new C0LChunk(0, fnlines)), null);
            }
          }
        });
      }
      ForkJoinTask.invokeAll(rs);
    }
    @Override public void reduce( SVFTask drt ) {}
  }

  // --------------------------------------------------------------------------
  // We want to do a standard MRTask with a collection of file-keys (so the
  // files are parsed in parallel across the cluster), but we want to throttle
  // the parallelism on each node.
  private static class MultiFileParseTask extends MRTask<MultiFileParseTask> {
    private final ParseSetup _parseSetup; // The expected column layout
    private final VectorGroup _vg;    // vector group of the target dataset
    private final int _vecIdStart;    // Start of available vector keys
    // Shared against all concurrent unrelated parses, a map to the node-local
    // Enum lists for each concurrent parse.
    private static NonBlockingHashMap<Key, Categorical[]> _enums = new NonBlockingHashMap<>();
    // The Key used to sort out *this* parse's Categorical[]
    private final Key _eKey = Key.make();
    // Eagerly delete Big Data
    private final boolean _deleteOnDone;
    // Mapping from Chunk# to cluster-node-number holding the enum mapping.
    // It is either self for all the non-parallel parses, or the Chunk-home for parallel parses.
    private int[] _chunk2Enum;
    // Job Key, to unlock & remove raw parsed data; to report progress
    private final Key _jobKey;
    // A mapping of Key+ByteVec to rolling total Chunk counts.
    private final int[]  _fileChunkOffsets;

    // OUTPUT fields:
    FVecParseWriter[] _dout;
    String[] _errors;

    int _reservedKeys;
    MultiFileParseTask(VectorGroup vg,  ParseSetup setup, Key jobKey, Key[] fkeys, boolean deleteOnDone ) {
      _vg = vg; _parseSetup = setup;
      _vecIdStart = _vg.reserveKeys(_reservedKeys = _parseSetup._parse_type == ParserType.SVMLight ? 100000000 : setup._number_columns);
      _deleteOnDone = deleteOnDone;
      _jobKey = jobKey;

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

    private AppendableVec [] _vecs;

    @Override public void postGlobal(){
      Log.trace("Begin file parse cleanup.");
      int n = _dout.length-1;
      while(_dout[n] == null && n != 0)--n;
      for(int i = 0; i <= n; ++i) {
        if (_dout[i] == null) {
          _dout[i] = _dout[n];
          n--;
          while (n > i && _dout[n] == null) n--;
        }
      }
      if(n < _dout.length-1)
        _dout = Arrays.copyOf(_dout,n+1);
      if(_dout.length == 1) {
        _vecs = _dout[0]._vecs;
        return;
      }
      int nCols = 0;
      for(FVecParseWriter dout:_dout)
        nCols = Math.max(dout._vecs.length,nCols);
      AppendableVec [] res = new AppendableVec[nCols];
      int nchunks = 0;
      for(FVecParseWriter dout:_dout)
        nchunks += dout.nChunks();
      long [] espc = MemoryManager.malloc8(nchunks);
      for(int i = 0; i < res.length; ++i) {
        res[i] = new AppendableVec(_vg.vecKey(_vecIdStart + i), espc, 0);
        res[i].setTypes(MemoryManager.malloc1(nchunks));
      }
      for(int i = 0; i < _dout.length; ++i)
        for(int j = 0; j < _dout[i]._vecs.length; ++j)
          res[j].setSubRange(_dout[i]._vecs[j]);
      if((res.length + _vecIdStart) < _reservedKeys) {
        Future f = _vg.tryReturnKeys(_vecIdStart + _reservedKeys, _vecIdStart + res.length);
        if (f != null) try { f.get(); } catch (InterruptedException e) { } catch (ExecutionException e) {}
      }
      _vecs = res;
      Log.trace("Finished file parse cleanup.");
    }
    private AppendableVec[] vecs(){ return _vecs; }

    @Override public void setupLocal() {
      _dout = new FVecParseWriter[_keys.length];
    }

    // Fetch out the node-local Categorical[] using _eKey and _enums hashtable
    private static Categorical[] enums(Key eKey, int ncols) {
      Categorical[] enums = _enums.get(eKey);
      if( enums != null ) return enums;
      enums = new Categorical[ncols];
      for( int i = 0; i < enums.length; ++i ) enums[i] = new Categorical();
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
        if(_deleteOnDone) vec.remove();
      } else {
        Frame fr = (Frame)ice;
        if(_deleteOnDone) fr.delete(_jobKey,new Futures()).blockForPending();
        else if( fr._key != null ) fr.unlock(_jobKey);
      }
    }

    private FVecParseWriter makeDout(ParseSetup localSetup, int chunkOff, int nchunks) {
      AppendableVec [] avs = new AppendableVec[localSetup._number_columns];
      long [] espc = MemoryManager.malloc8(nchunks);
      for(int i = 0; i < avs.length; ++i)
        avs[i] = new AppendableVec(_vg.vecKey(i + _vecIdStart), espc, chunkOff);
      return localSetup._parse_type == ParserType.SVMLight
        ?new SVMLightFVecParseWriter(_vg, _vecIdStart,chunkOff, _parseSetup._chunk_size, avs)
        :new FVecParseWriter(_vg, chunkOff, enums(_eKey,localSetup._number_columns), localSetup._column_types, _parseSetup._chunk_size, avs);
    }

    // Called once per file
    @Override public void map( Key key ) {
      if (((Job)DKV.getGet(_jobKey)).isCancelledOrCrashed()) return;
      ParseSetup localSetup = new ParseSetup(_parseSetup);
      ByteVec vec = getByteVec(key);
      final int chunkStartIdx = _fileChunkOffsets[_lo];
      Log.trace("Begin a map stage of a file parse with start index "+chunkStartIdx+".");

      byte[] zips = vec.getFirstBytes();
      ZipUtil.Compression cpr = ZipUtil.guessCompressionMethod(zips);

      if (localSetup._check_header == ParseSetup.HAS_HEADER) //check for header on local file
        localSetup._check_header = localSetup.parser(_jobKey).fileHasHeader(ZipUtil.unzipBytes(zips,cpr, localSetup._chunk_size), localSetup);

      // Parse the file
      try {
        switch( cpr ) {
        case NONE:
          if( _parseSetup._parse_type._parallelParseSupported ) {
            DistributedParse dp = new DistributedParse(_vg, localSetup, _vecIdStart, chunkStartIdx, this, key, vec.nChunks());
            addToPendingCount(1);
            dp.setCompleter(this);
            dp.asyncExec(vec);
            for( int i = 0; i < vec.nChunks(); ++i )
              _chunk2Enum[chunkStartIdx + i] = vec.chunkKey(i).home_node().index();
          } else {
            InputStream bvs = vec.openStream(_jobKey);
            _dout[_lo] = streamParse(bvs, localSetup, makeDout(localSetup,chunkStartIdx,vec.nChunks()), bvs);
            chunksAreLocal(vec,chunkStartIdx,key);
          }
          break;
        case ZIP: {
          // Zipped file; no parallel decompression;
          InputStream bvs = vec.openStream(_jobKey);
          ZipInputStream zis = new ZipInputStream(bvs);
          ZipEntry ze = zis.getNextEntry(); // Get the *FIRST* entry
          // There is at least one entry in zip file and it is not a directory.
          if( ze != null && !ze.isDirectory() )
            _dout[_lo] = streamParse(zis,localSetup,makeDout(localSetup,chunkStartIdx,vec.nChunks()), bvs);
            // check for more files in archive
            ZipEntry ze2 = zis.getNextEntry();
            if (ze2 != null && !ze.isDirectory()) {
              Log.warn("Only single file zip archives are currently supported, only file: "+ze.getName()+" has been parsed.  Remaining files have been ignored.");
            }
          else zis.close();       // Confused: which zipped file to decompress
          chunksAreLocal(vec,chunkStartIdx,key);
          break;
        }
        case GZIP: {
          InputStream bvs = vec.openStream(_jobKey);
          // Zipped file; no parallel decompression;
          _dout[_lo] = streamParse(new GZIPInputStream(bvs),localSetup,makeDout(localSetup,chunkStartIdx,vec.nChunks()),bvs);
          // set this node as the one which processed all the chunks
          chunksAreLocal(vec,chunkStartIdx,key);
          break;
        }
        }
        Log.trace("Finished a map stage of a file parse with start index "+chunkStartIdx+".");
      } catch( IOException ioe ) {
        throw new RuntimeException(ioe);
      } catch (H2OParseException pe) {
        throw new H2OParseException(key,pe);
      }
    }

    // Reduce: combine errors from across files.
    // Roll-up other meta data
    @Override public void reduce( MultiFileParseTask mfpt ) {
      assert this != mfpt;
      Log.trace("Begin a reduce stage of a file parse.");

      // Collect & combine columns across files
      if( _dout == null ) _dout = mfpt._dout;
      else if(_dout != mfpt._dout) _dout = ArrayUtils.append(_dout,mfpt._dout);
      if( _chunk2Enum == null ) _chunk2Enum = mfpt._chunk2Enum;
      else if(_chunk2Enum != mfpt._chunk2Enum) { // we're sharing global array!
        for( int i = 0; i < _chunk2Enum.length; ++i ) {
          if( _chunk2Enum[i] == -1 ) _chunk2Enum[i] = mfpt._chunk2Enum[i];
          else assert mfpt._chunk2Enum[i] == -1 : Arrays.toString(_chunk2Enum) + " :: " + Arrays.toString(mfpt._chunk2Enum);
        }
      }
      _errors = ArrayUtils.append(_errors,mfpt._errors);
      Log.trace("Finished a reduce stage of a file parse.");
    }

    // ------------------------------------------------------------------------
    // Zipped file; no parallel decompression; decompress into local chunks,
    // parse local chunks; distribute chunks later.
    private FVecParseWriter streamParse( final InputStream is, final ParseSetup localSetup, FVecParseWriter dout, InputStream bvs) throws IOException {
      // All output into a fresh pile of NewChunks, one per column
      Parser p = localSetup.parser(_jobKey);
      // assume 2x inflation rate
      if( localSetup._parse_type._parallelParseSupported ) p.streamParseZip(is, dout, bvs);
      else                                            p.streamParse   (is, dout);
      // Parse all internal "chunks", until we drain the zip-stream dry.  Not
      // real chunks, just flipping between 32K buffers.  Fills up the single
      // very large NewChunk.
      dout.close(_fs);
      return dout;
    }

    // ------------------------------------------------------------------------
    private static class DistributedParse extends MRTask<DistributedParse> {
      private final ParseSetup _setup;
      private final int _vecIdStart;
      private final int _startChunkIdx; // for multifile parse, offset of the first chunk in the final dataset
      private final VectorGroup _vg;
      private FVecParseWriter _dout;
      private final Key _eKey;  // Parse-local-Enums key
      private final Key _jobKey;
      private transient final MultiFileParseTask _outerMFPT;
      private transient final Key _srckey; // Source/text file to delete on done
      private transient NonBlockingSetInt _visited;
      private transient long [] _espc;
      final int _nchunks;

      DistributedParse(VectorGroup vg, ParseSetup setup, int vecIdstart, int startChunkIdx, MultiFileParseTask mfpt, Key srckey, int nchunks) {
        super(mfpt);
        _vg = vg;
        _setup = setup;
        _vecIdStart = vecIdstart;
        _startChunkIdx = startChunkIdx;
        _outerMFPT = mfpt;
        _eKey = mfpt._eKey;
        _jobKey = mfpt._jobKey;
        _srckey = srckey;
        _nchunks = nchunks;
      }
      @Override public void setupLocal(){
        super.setupLocal();
        _visited = new NonBlockingSetInt();
        _espc = MemoryManager.malloc8(_nchunks);
      }
      @Override public void map( Chunk in ) {
        if (((Job)DKV.getGet(_jobKey)).isCancelledOrCrashed()) return;
        AppendableVec [] avs = new AppendableVec[_setup._number_columns];
        for(int i = 0; i < avs.length; ++i)
          avs[i] = new AppendableVec(_vg.vecKey(_vecIdStart + i), _espc, _startChunkIdx);
        // Break out the input & output vectors before the parse loop
        FVecParseReader din = new FVecParseReader(in);
        FVecParseWriter dout;
        Parser p;
        switch(_setup._parse_type) {
          case ARFF:
          case CSV:
            Categorical [] enums = enums(_eKey,_setup._number_columns);
            p = new CsvParser(_setup, _jobKey);
            dout = new FVecParseWriter(_vg,_startChunkIdx + in.cidx(), enums, _setup._column_types, _setup._chunk_size, avs); //TODO: use _setup._domains instead of enums
          break;
        case SVMLight:
          p = new SVMLightParser(_setup, _jobKey);
          dout = new SVMLightFVecParseWriter(_vg, _vecIdStart, in.cidx() + _startChunkIdx, _setup._chunk_size, avs);
          break;
        default:
          throw H2O.unimpl();
        }
        p.parseChunk(in.cidx(), din, dout);
        (_dout = dout).close(_fs);
        Job.update(in._len, _jobKey); // Record bytes parsed

        // remove parsed data right away
        freeMem(in);
      }

      /**
       * This marks parsed byteVec chunks as ready to be freed. If this is the second
       * time a chunk has been marked, it is freed. The reason two marks are required
       * is that each chunk parse typically needs to read the remaining bytes of the
       * current row from the next chunk.  Thus each task typically touches two chunks.
       *
       * @param in - chunk to be marked and possibly freed
       */
      private void freeMem(Chunk in) {
        int cidx = in.cidx();
        for(int i=0; i < 2; i++) {  // iterate over this chunk and the next one
          cidx += i;
          if (!_visited.add(cidx)) { // Second visit
            Value v = H2O.get(in.vec().chunkKey(cidx));
            if (v == null || !v.isPersisted()) return; // Not found, or not on disk somewhere
            v.freePOJO();           // Eagerly toss from memory
            v.freeMem();
          }
        }
      }
      @Override public void reduce(DistributedParse dp) { _dout.reduce(dp._dout); }

      @Override public void postGlobal() {
        super.postGlobal();
        _outerMFPT._dout[_outerMFPT._lo] = _dout;
        _dout = null;           // Reclaim GC eagerly
        // For Big Data, must delete data as eagerly as possible.
        Value val = DKV.get(_srckey);
        if( val == null ) return;
        Iced ice = val.get();
        if( ice instanceof ByteVec ) {
          if( _outerMFPT._deleteOnDone) ((ByteVec)ice).remove();
        } else {
          Frame fr = (Frame)ice;
          if( _outerMFPT._deleteOnDone) fr.delete(_outerMFPT._jobKey,new Futures()).blockForPending();
          else if( fr._key != null ) fr.unlock(_outerMFPT._jobKey);
        }
      }
    }

    // Find & remove all partially built output chunks & vecs
    private Futures onExceptionCleanup(Futures fs) {
      int nchunks = _chunk2Enum.length;
      int ncols = _parseSetup._number_columns;
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
  // Log information about the dataset we just parsed.
  private static void logParseResults(ParseDataset job, Frame fr) {
    try {
      long numRows = fr.anyVec().length();
      Log.info("Parse result for " + job.dest() + " (" + Long.toString(numRows) + " rows):");
      // get all rollups started in parallell, otherwise this takes ages!
      Futures fs = new Futures();
      Vec[] vecArr = fr.vecs();
      for(Vec v:vecArr)  v.startRollupStats(fs);
      fs.blockForPending();

      int namelen = 0;
      for (String s : fr.names()) namelen = Math.max(namelen, s.length());
      String format = " %"+namelen+"s %7s %12.12s %12.12s %11s %8s %6s";
      Log.info(String.format(format, "ColV2", "type", "min", "max", "NAs", "constant", "numLevels"));
      SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

      for( int i = 0; i < vecArr.length; i++ ) {
        Vec v = vecArr[i];
        boolean isCategorical = v.isEnum();
        boolean isConstant = v.isConst();
        String CStr = String.format("%"+namelen+"s:", fr.names()[i]);
        String typeStr;
        String minStr;
        String maxStr;

        switch( v.get_type() ) {
        case Vec.T_BAD:   typeStr = "all_NA" ;  minStr = "";  maxStr = "";  break;
        case Vec.T_UUID:  typeStr = "UUID"   ;  minStr = "";  maxStr = "";  break;
        case Vec.T_STR :  typeStr = "string" ;  minStr = "";  maxStr = "";  break;
        case Vec.T_NUM :  typeStr = "numeric";  minStr = String.format("%g", v.min());  maxStr = String.format("%g", v.max());  break;
        case Vec.T_ENUM:  typeStr = "factor" ;  minStr = v.factor(0);  maxStr = v.factor(v.cardinality()-1); break;
        case Vec.T_TIME:  typeStr = "time"   ;  minStr = sdf.format(v.min());  maxStr = sdf.format(v.max());  break;
        default: throw H2O.unimpl();
        }

        long numNAs = v.naCnt();
        String naStr = (numNAs > 0) ? String.format("%d", numNAs) : "";
        String isConstantStr = isConstant ? "constant" : "";
        String numLevelsStr = isCategorical ? String.format("%d", v.domain().length) : "";

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

        if (printLogSeparatorToStdout)
          Log.info("Additional column information only sent to log file...");

        String s = String.format(format, CStr, typeStr, minStr, maxStr, naStr, isConstantStr, numLevelsStr);
        Log.info(s,printColumnToStdout);
      }
      Log.info(FrameUtils.chunkSummary(fr).toString());
    }
    catch(Exception ignore) {}   // Don't fail due to logging issues.  Just ignore them.
  }
}

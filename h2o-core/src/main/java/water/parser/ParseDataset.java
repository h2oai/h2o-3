package water.parser;

import jsr166y.CountedCompleter;
import jsr166y.ForkJoinTask;
import jsr166y.RecursiveAction;
import water.*;
import water.H2O.H2OCountedCompleter;
import water.exceptions.H2OIllegalArgumentException;
import water.exceptions.H2OIllegalValueException;
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
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.google.common.base.Charsets;

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
    Log.info("Total file size: "+ PrettyPrint.bytes(totalParseSize));

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
    job.start(fjt, totalParseSize, true);
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
        _job.failed(ex);
        parseCleanup();
        _job._mfpt = null;
      }
      return true;
    }

    @Override public void onCompletion(CountedCompleter caller) {
      if (_job.isCancelledOrCrashed()) {
        parseCleanup();
        _job._mfpt = null;
      } else {
        _job._mfpt = null;
        _job.done();
      }
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
        fs.blockForPending();
        DKV.put(_job._key, _job);
      }
    }
  }

  private static class CategoricalUpdateMap extends Iced {
    final int [][] map;
    public CategoricalUpdateMap(int[][] map){this.map = map;}
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

    job.update(0, "Ingesting files.");
    VectorGroup vg = getByteVec(fkeys[0]).group();
    MultiFileParseTask mfpt = job._mfpt = new MultiFileParseTask(vg,setup,job._key,fkeys,deleteOnDone);
    mfpt.doAll(fkeys);
    Log.trace("Done ingesting files.");
    if ( job.isCancelledOrCrashed()) return;

    final AppendableVec [] avs = mfpt.vecs();
    setup._column_names = getColumnNames(avs.length, setup._column_names);

    Frame fr = null;
    // Calculate categorical domain
    // Filter down to columns with some categoricals
    int n = 0;
    int[] ecols2 = new int[avs.length];
    for( int i = 0; i < avs.length; ++i )
      if( avs[i].get_type()==Vec.T_CAT  ) // Intended type is categorical (even though no domain has been set)?
        ecols2[n++] = i;
    final int[] ecols = Arrays.copyOf(ecols2, n);
    // If we have any, go gather unified categorical domains
    if( n > 0 ) {
      job.update(0, "Collecting categorical domains across nodes.");
      {
        GatherCategoricalDomainsTask gcdt = new GatherCategoricalDomainsTask(mfpt._cKey, ecols).doAllNodes();
        //Test domains for excessive length.
        List<String> offendingColNames = new ArrayList<>();
        for (int i = 0; i < ecols.length; i++) {
          if (gcdt.getDomainLength(i) < Categorical.MAX_CATEGORICAL_COUNT) {
            if( gcdt.getDomainLength(i)==0 ) avs[ecols[i]].setBad(); // The all-NA column
            else avs[ecols[i]].setDomain(gcdt.getDomain(i));
          } else
            offendingColNames.add(setup._column_names[ecols[i]]);
        }
        if (offendingColNames.size() > 0)
          throw new H2OParseException("Exceeded categorical limit on columns "+ offendingColNames+".   Consider reparsing these columns as a string.");
      }
      Log.trace("Done collecting categorical domains across nodes.");

      job.update(0, "Compressing data.");
      fr = new Frame(job.dest(), setup._column_names,AppendableVec.closeAll(avs));
      fr.update(job._key);
      // Update categoricals to the globally agreed numbering
      Vec[] evecs = new Vec[ecols.length];
      for( int i = 0; i < evecs.length; ++i ) evecs[i] = fr.vecs()[ecols[i]];
      Log.trace("Done compressing data.");

      job.update(0, "Unifying categorical domains across nodes.");
      {
        // new CreateParse2GlobalCategoricalMaps(mfpt._cKey).doAll(evecs);
        // Using Dtask since it starts and returns faster than an MRTask
        CreateParse2GlobalCategoricalMaps[] fcdt = new CreateParse2GlobalCategoricalMaps[H2O.CLOUD.size()];
        RPC[] rpcs = new RPC[H2O.CLOUD.size()];
        for (int i = 0; i < fcdt.length; i++){
          H2ONode[] nodes = H2O.CLOUD.members();
          fcdt[i] = new CreateParse2GlobalCategoricalMaps(mfpt._cKey, fr._key, ecols.length);
          rpcs[i] = new RPC<>(nodes[i], fcdt[i]).call();
        }
        for (RPC rpc : rpcs)
          rpc.get();

        new UpdateCategoricalChunksTask(mfpt._cKey, mfpt._chunk2ParseNodeMap).doAll(evecs);
        MultiFileParseTask._categoricals.remove(mfpt._cKey);
      }
      Log.trace("Done unifying categoricals across nodes.");
    } else {                    // No categoricals case
      job.update(0,"Compressing data.");
      fr = new Frame(job.dest(), setup._column_names,AppendableVec.closeAll(avs));
      Log.trace("Done closing all Vecs.");
    }
    // Check for job cancellation
    if ( job.isCancelledOrCrashed()) return;

    // SVMLight is sparse format, there may be missing chunks with all 0s, fill them in
    if (setup._parse_type == ParserType.SVMLight)
      new SVFTask(fr).doAllNodes();

    // Check for job cancellation
    if ( job.isCancelledOrCrashed()) return;

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
  private static class CreateParse2GlobalCategoricalMaps extends DTask<CreateParse2GlobalCategoricalMaps> {
    private final Key _parseCatMapsKey;
    private final Key _frKey;
    private final byte _priority;
    private final int _eColCnt;

    private CreateParse2GlobalCategoricalMaps(Key parseCatMapsKey, Key key, int eColCnt) {
      _parseCatMapsKey = parseCatMapsKey;
      _frKey = key;
      _eColCnt = eColCnt;
      _priority = nextThrPriority();
    }

    @Override public byte priority() {return _priority;}
    @Override public void compute2() {
      Frame _fr = DKV.getGet(_frKey);
      // get the node local category->ordinal maps for each column from initial parse pass
      if( !MultiFileParseTask._categoricals.containsKey(_parseCatMapsKey) ) {
        tryComplete();
        return;
      }
        final Categorical[] parseCatMaps = MultiFileParseTask._categoricals.get(_parseCatMapsKey);
        int[][] _nodeOrdMaps = new int[_eColCnt][];

        // create old_ordinal->new_ordinal map for each cat column
        int catColIdx = 0;
        for (int colIdx = 0; colIdx < parseCatMaps.length; colIdx++) {
          if (parseCatMaps[colIdx].size() != 0) {
            _nodeOrdMaps[catColIdx] = MemoryManager.malloc4(parseCatMaps[colIdx].maxId() + 1);
            Arrays.fill(_nodeOrdMaps[catColIdx], -1);
            //Bulk String->BufferedString conversion is slightly faster, but consumes memory
            final BufferedString[] unifiedDomain = BufferedString.toBufferedString(_fr.vec(colIdx).domain());
            //final String[] unifiedDomain = _fr.vec(colIdx).domain();
            for (int i = 0; i < unifiedDomain.length; i++) {
              //final BufferedString cat = new BufferedString(unifiedDomain[i]);
              if (parseCatMaps[colIdx].containsKey(unifiedDomain[i])) {
                _nodeOrdMaps[catColIdx][parseCatMaps[colIdx].getTokenId(unifiedDomain[i])] = i;
              }
            }
            catColIdx++;
          }
        }

        // Store the local->global ordinal maps in DKV by node parse categorical key and node index
        DKV.put(Key.make(_parseCatMapsKey.toString() + "parseCatMapNode" + H2O.SELF.index()), new CategoricalUpdateMap(_nodeOrdMaps));
      tryComplete();
    }
  }

  // --------------------------------------------------------------------------
  /** Task to update categorical (categorical) values to match the global numbering scheme.
   *  Performs update in place so that values originally numbered using
   *  node-local unordered numbering will be numbered using global numbering.
   *  @author tomasnykodym
   */
  private static class UpdateCategoricalChunksTask extends MRTask<UpdateCategoricalChunksTask> {
    private final Key _parseCatMapsKey;
    private final int  [] _chunk2ParseNodeMap;

    private UpdateCategoricalChunksTask(Key parseCatMapsKey, int[] chunk2ParseNodeMap) {
      _parseCatMapsKey = parseCatMapsKey;
      _chunk2ParseNodeMap = chunk2ParseNodeMap;
    }

    @Override public void map(Chunk [] chks){
      CategoricalUpdateMap temp = DKV.getGet(Key.make(_parseCatMapsKey.toString() + "parseCatMapNode" + _chunk2ParseNodeMap[chks[0].cidx()]));
      if ( temp == null || temp.map == null)
        throw new H2OIllegalValueException("Missing categorical update map",this);
      int[][] _parse2GlobalCatMaps = temp.map;

      //update the chunk with the new map
      final int cidx = chks[0].cidx();
      for(int i = 0; i < chks.length; ++i) {
        Chunk chk = chks[i];
        if (!(chk instanceof CStrChunk)) {
          for( int j = 0; j < chk._len; ++j){
            if( chk.isNA(j) )continue;
            final int old = (int) chk.at8(j);
            if (old < 0 || old >= _parse2GlobalCatMaps[i].length)
              chk.reportBrokenCategorical(i, j, old, _parse2GlobalCatMaps[i], _fr.vec(i).domain().length);
            if(_parse2GlobalCatMaps[i][old] < 0)
              throw new H2OParseException("Error in unifying categorical values. This is typically "
                  +"caused by unrecognized characters in the data.\n The problem categorical value "
                  +"occurred in the " + PrettyPrint.withOrdinalIndicator(i+1)+ " categorical col, "
                  +PrettyPrint.withOrdinalIndicator(chk.start() + j) +" row.");
            chk.set(j, _parse2GlobalCatMaps[i][old]);
          }
          Log.trace("Updated domains for "+PrettyPrint.withOrdinalIndicator(i+1)+ " categorical column.");
        }
        chk.close(cidx, _fs);
      }
    }
    @Override public void postGlobal() {
      for (int i=0; i < H2O.CLOUD.size(); i++)
        DKV.remove(Key.make(_parseCatMapsKey.toString() + "parseCatMapNode" + i));
    }
  }
  private static class GatherCategoricalDomainsTask extends MRTask<GatherCategoricalDomainsTask> {
    private final Key _k;
    private final int[] _catColIdxs;
    private byte[][] _packedDomains;

    private GatherCategoricalDomainsTask(Key k, int[] ccols) {
      _k = k;
      _catColIdxs = ccols;
    }

    @Override
    public void setupLocal() {
      if (!MultiFileParseTask._categoricals.containsKey(_k)) return;
      _packedDomains = new byte[_catColIdxs.length][];
      final BufferedString[][] _perColDomains = new BufferedString[_catColIdxs.length][];
      final Categorical[] _colCats = MultiFileParseTask._categoricals.get(_k);
      int i = 0;
      for (int col : _catColIdxs) {
        _colCats[col].convertToUTF8(col + 1);
        _perColDomains[i] = _colCats[col].getColumnDomain();
        Arrays.sort(_perColDomains[i]);
        _packedDomains[i] = packDomain(_perColDomains[i]);
        i++;
      }
      Log.trace("Done locally collecting domains on each node.");
    }

    @Override
    public void reduce(final GatherCategoricalDomainsTask other) {
      if (_packedDomains == null) {
        _packedDomains = other._packedDomains;
      } else if (other._packedDomains != null) { // merge two packed domains
        H2OCountedCompleter[] domtasks = new H2OCountedCompleter[_catColIdxs.length];
        for (int i = 0; i < _catColIdxs.length; i++) {
          final int fi = i;
          final GatherCategoricalDomainsTask fOther = other;
          H2O.submitTask(domtasks[i] = new H2OCountedCompleter() {
            @Override
            public void compute2() {
              // merge sorted packed domains with duplicate removal
              final byte[] thisDom = _packedDomains[fi];
              final byte[] otherDom = fOther._packedDomains[fi];
              final int tLen = UnsafeUtils.get4(thisDom, 0), oLen = UnsafeUtils.get4(otherDom, 0);
              int tDomLen = UnsafeUtils.get4(thisDom, 4);
              int oDomLen = UnsafeUtils.get4(otherDom, 4);
              BufferedString tCat = new BufferedString(thisDom, 8, tDomLen);
              BufferedString oCat = new BufferedString(otherDom, 8, oDomLen);
              int ti = 0, oi = 0, tbi = 8, obi = 8, mbi = 4, mergeLen = 0;
              byte[] mergedDom = new byte[thisDom.length + otherDom.length];
              // merge
              while (ti < tLen && oi < oLen) {
                // compare thisDom to otherDom
                int x = tCat.compareTo(oCat);
                // this < or equal to other
                if (x <= 0) {
                  UnsafeUtils.set4(mergedDom, mbi, tDomLen); //Store str len
                  mbi += 4;
                  for (int j = 0; j < tDomLen; j++)
                    mergedDom[mbi++] = thisDom[tbi++];
                  tDomLen = UnsafeUtils.get4(thisDom, tbi);
                  tbi += 4;
                  tCat.set(thisDom, tbi, tDomLen);
                  ti++;
                  if (x == 0) { // this == other
                    obi += oDomLen;
                    oDomLen = UnsafeUtils.get4(otherDom, obi);
                    obi += 4;
                    oCat.set(otherDom, obi, oDomLen);
                    oi++;
                  }
                } else { // other < this
                  UnsafeUtils.set4(mergedDom, mbi, oDomLen); //Store str len
                  mbi += 4;
                  for (int j = 0; j < oDomLen; j++)
                    mergedDom[mbi++] = otherDom[obi++];
                  oDomLen = UnsafeUtils.get4(otherDom, obi);
                  obi += 4;
                  oCat.set(otherDom, obi, oDomLen);
                  oi++;
                }
                mergeLen++;
              }
              // merge remainder of longer list
              if (ti < tLen) {
                tbi -= 4;
                int remainder = thisDom.length - tbi;
                System.arraycopy(thisDom,tbi,mergedDom,mbi,remainder);
                mbi += remainder;
                mergeLen += tLen - ti;
              } else { //oi < oLen
                obi -= 4;
                int remainder = otherDom.length - obi;
                System.arraycopy(otherDom,obi,mergedDom,mbi,remainder);
                mbi += remainder;
                mergeLen += oLen - oi;
              }
              _packedDomains[fi]  = Arrays.copyOf(mergedDom, mbi);// reduce size
              UnsafeUtils.set4(_packedDomains[fi], 0, mergeLen);
              Log.trace("Merged domain length is "+mergeLen+" for the "
                  +PrettyPrint.withOrdinalIndicator(fi+1)+ " categorical column.");;
              tryComplete();
            }
          });
        }
        for (int i = 0; i < _catColIdxs.length; i++) if (domtasks[i] != null) domtasks[i].join();
      }
      Log.trace("Done merging domains.");
    }

    private byte[] packDomain(BufferedString[] domain) {
      int totStrLen =0;
      for(BufferedString dom : domain)
        totStrLen += dom.length();
      final byte[] packedDom = MemoryManager.malloc1(4 + (domain.length << 2) + totStrLen, false);
      UnsafeUtils.set4(packedDom, 0, domain.length); //Store domain size
      int i = 4;
      for(BufferedString dom : domain) {
        UnsafeUtils.set4(packedDom, i, dom.length()); //Store str len
        i += 4;
        byte[] buf = dom.getBuffer();
        for(int j=0; j < buf.length; j++) //Store str chars
          packedDom[i++] = buf[j];
      }
      return packedDom;
    }
    public int getDomainLength(int colIdx) {
      if (_packedDomains == null) return 0;
      else return UnsafeUtils.get4(_packedDomains[colIdx], 0);
    }

    public String[] getDomain(int colIdx) {
      if (_packedDomains == null) return null;
      final int strCnt = UnsafeUtils.get4(_packedDomains[colIdx], 0);
      final String[] res = new String[strCnt];
      int j = 4;
      for (int i=0; i < strCnt; i++) {
        final int strLen = UnsafeUtils.get4(_packedDomains[colIdx], j);
        j += 4;
        res[i] = new String(_packedDomains[colIdx], j, strLen, Charsets.UTF_8);
        j += strLen;
      }
      return res;
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
    // categorical lists for each concurrent parse.
    private static NonBlockingHashMap<Key, Categorical[]> _categoricals = new NonBlockingHashMap<>();
    // The Key used to sort out *this* parse's Categorical[]
    private final Key _cKey = Key.make();
    // Eagerly delete Big Data
    private final boolean _deleteOnDone;
    // Mapping from Chunk# to node index holding the initial category mappings.
    // It is either self for all the non-parallel parses, or the Chunk-home for parallel parses.
    private int[] _chunk2ParseNodeMap;
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
      _chunk2ParseNodeMap = MemoryManager.malloc4(len);
      Arrays.fill(_chunk2ParseNodeMap, -1);
    }

    private AppendableVec [] _vecs;

    @Override public void postGlobal(){
      Log.trace("Begin file parse cleanup.");
      // Compress nulls out of _dout array
      int n=0;
      for( int i=0; i<_dout.length; i++ )
        if( _dout[i] != null ) _dout[n++] = _dout[i];
      if( n < _dout.length )  _dout = Arrays.copyOf(_dout,n);
      // Fast path: only one Vec result, so never needs to have his Chunks renumbered
      if(_dout.length == 1) {
        _vecs = _dout[0]._vecs;
        return;
      }
      int nchunks = 0;          // Count chunks across all Vecs
      int nCols = 0;            // SVMLight special: find max columns
      for( FVecParseWriter dout : _dout ) {
        nchunks += dout._vecs[0]._tmp_espc.length;
        nCols = Math.max(dout._vecs.length,nCols);
      }
      // One Big Happy Shared ESPC
      long[] espc = MemoryManager.malloc8(nchunks);
      // AppendableVecs that are sized across the sum of all files.
      // Preallocated a bunch of Keys, but if we didn't get enough (for very
      // wide SVMLight) we need to get more here.
      if( nCols > _reservedKeys ) throw H2O.unimpl();
      AppendableVec[] res = new AppendableVec[nCols];
      for(int i = 0; i < res.length; ++i)
        res[i] = new AppendableVec(_vg.vecKey(_vecIdStart + i), espc, _parseSetup._column_types[i], 0);

      // Load the global ESPC from the file-local ESPCs
      for( FVecParseWriter fvpw : _dout ) {
        AppendableVec[] avs = fvpw._vecs;
        long[] file_local_espc = avs[0]._tmp_espc;
        // Quick assert that all partial AVs in each DOUT are sharing a common chunkOff, and common Vec Keys
        for( int j = 0; j < avs.length; ++j ) {
          assert res[j]._key.equals(avs[j]._key);
          assert avs[0]._chunkOff == avs[j]._chunkOff;
          assert file_local_espc == avs[j]._tmp_espc || Arrays.equals(file_local_espc,avs[j]._tmp_espc);
        }
        System.arraycopy(file_local_espc, 0, espc, avs[0]._chunkOff, file_local_espc.length);
      }

      _vecs = res;
      Log.trace("Finished file parse cleanup.");
    }
    private AppendableVec[] vecs(){ return _vecs; }

    @Override public void setupLocal() {
      _dout = new FVecParseWriter[_keys.length];
    }

    // Fetch out the node-local Categorical[] using _cKey and _categoricals hashtable
    private static Categorical[] categoricals(Key cKey, int ncols) {
      Categorical[] categoricals = _categoricals.get(cKey);
      if( categoricals != null ) return categoricals;
      categoricals = new Categorical[ncols];
      for( int i = 0; i < categoricals.length; ++i ) categoricals[i] = new Categorical();
      _categoricals.putIfAbsent(cKey, categoricals);
      return _categoricals.get(cKey); // Re-get incase lost insertion race
    }

    // Flag all chunk categoricals as being on local (self)
    private void chunksAreLocal( Vec vec, int chunkStartIdx, Key key ) {
      for(int i = 0; i < vec.nChunks(); ++i)
        _chunk2ParseNodeMap[chunkStartIdx + i] = H2O.SELF.index();
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
        avs[i] = new AppendableVec(_vg.vecKey(i + _vecIdStart), espc, localSetup._column_types[i], chunkOff);
      return localSetup._parse_type == ParserType.SVMLight
        ? new SVMLightFVecParseWriter(_vg, _vecIdStart,chunkOff, _parseSetup._chunk_size, avs)
        : new FVecParseWriter(_vg, chunkOff, categoricals(_cKey, localSetup._number_columns), localSetup._column_types, _parseSetup._chunk_size, avs);
    }

    // Called once per file
    @Override public void map( Key key ) {
      if (((Job)DKV.getGet(_jobKey)).isCancelledOrCrashed()) return;
      ParseSetup localSetup = new ParseSetup(_parseSetup);
      ByteVec vec = getByteVec(key);
      final int chunkStartIdx = _fileChunkOffsets[_lo];
      Log.trace("Begin a map stage of a file parse with start index " + chunkStartIdx + ".");

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
              _chunk2ParseNodeMap[chunkStartIdx + i] = vec.chunkKey(i).home_node().index();
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
      if( _chunk2ParseNodeMap == null ) _chunk2ParseNodeMap = mfpt._chunk2ParseNodeMap;
      else if(_chunk2ParseNodeMap != mfpt._chunk2ParseNodeMap) { // we're sharing global array!
        for( int i = 0; i < _chunk2ParseNodeMap.length; ++i ) {
          if( _chunk2ParseNodeMap[i] == -1 ) _chunk2ParseNodeMap[i] = mfpt._chunk2ParseNodeMap[i];
          else assert mfpt._chunk2ParseNodeMap[i] == -1 : Arrays.toString(_chunk2ParseNodeMap) + " :: " + Arrays.toString(mfpt._chunk2ParseNodeMap);
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
      private final Key _cKey;  // Parse-local-categoricals key
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
        _cKey = mfpt._cKey;
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
          if (_setup._column_types == null) // SVMLight
            avs[i] = new AppendableVec(_vg.vecKey(_vecIdStart + i), _espc, Vec.T_NUM, _startChunkIdx);
          else
           avs[i] = new AppendableVec(_vg.vecKey(_vecIdStart + i), _espc, _setup._column_types[i], _startChunkIdx);
        // Break out the input & output vectors before the parse loop
        FVecParseReader din = new FVecParseReader(in);
        FVecParseWriter dout;
        Parser p;
        switch(_setup._parse_type) {
        case ARFF:
        case CSV:
          Categorical [] categoricals = categoricals(_cKey, _setup._number_columns);
          p = new CsvParser(_setup, _jobKey);
          dout = new FVecParseWriter(_vg,_startChunkIdx + in.cidx(), categoricals, _setup._column_types, _setup._chunk_size, avs); //TODO: use _setup._domains instead of categoricals
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
      int nchunks = _chunk2ParseNodeMap.length;
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
    long numRows = fr.anyVec().length();
    Log.info("Parse result for " + job.dest() + " (" + Long.toString(numRows) + " rows):");
    // get all rollups started in parallell, otherwise this takes ages!
    Futures fs = new Futures();
    Vec[] vecArr = fr.vecs();
    for(Vec v:vecArr)  v.startRollupStats(fs);
    fs.blockForPending();

    int namelen = 0;
    for (String s : fr.names()) namelen = Math.max(namelen, s.length());
    String format = " %"+namelen+"s %7s %12.12s %12.12s %12.12s %12.12s %11s %8s %6s";
    Log.info(String.format(format, "ColV2", "type", "min", "max", "mean", "sigma", "NAs", "constant", "cardinality"));
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    for( int i = 0; i < vecArr.length; i++ ) {
      Vec v = vecArr[i];
      boolean isCategorical = v.isCategorical();
      boolean isConstant = v.isConst();
      String CStr = String.format("%"+namelen+"s:", fr.names()[i]);
      String typeStr;
      String minStr;
      String maxStr;
      String meanStr="";
      String sigmaStr="";

      switch( v.get_type() ) {
        case Vec.T_BAD :   typeStr = "all_NA" ;  minStr = "";  maxStr = "";  break;
        case Vec.T_UUID:  typeStr = "UUID"   ;  minStr = "";  maxStr = "";  break;
        case Vec.T_STR :  typeStr = "string" ;  minStr = "";  maxStr = "";  break;
        case Vec.T_NUM :  typeStr = "numeric";
          minStr = String.format("%g", v.min());
          maxStr = String.format("%g", v.max());
          meanStr = String.format("%g", v.mean());
          sigmaStr = String.format("%g", v.sigma());
          break;
        case Vec.T_CAT :  typeStr = "factor" ;  minStr = v.factor(0);  maxStr = v.factor(v.cardinality()-1); break;
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

      String s = String.format(format, CStr, typeStr, minStr, maxStr, meanStr, sigmaStr, naStr, isConstantStr, numLevelsStr);
      Log.info(s,printColumnToStdout);
    }
    Log.info(FrameUtils.chunkSummary(fr).toString());
  }
}

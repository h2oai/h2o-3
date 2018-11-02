package water.parser;

import jsr166y.CountedCompleter;
import jsr166y.ForkJoinTask;
import jsr166y.RecursiveAction;
import water.*;
import water.H2O.H2OCountedCompleter;
import water.exceptions.H2OIllegalArgumentException;
import water.exceptions.H2OIllegalValueException;
import water.fvec.*;
import water.fvec.Vec.VectorGroup;
import water.nbhm.NonBlockingHashMap;
import water.nbhm.NonBlockingSetInt;
import water.util.ArrayUtils;
import water.util.FrameUtils;
import water.util.Log;
import water.util.PrettyPrint;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static water.parser.DefaultParserProviders.SVMLight_INFO;

public final class ParseDataset {
  public Job<Frame> _job;
  private MultiFileParseTask _mfpt; // Access to partially built vectors for cleanup after parser crash

  // Keys are limited to ByteVec Keys and Frames-of-1-ByteVec Keys
  public static Frame parse(Key okey, Key... keys) {
    return parse(null, okey, keys);
  }
  public static Frame parse(int[] skippedColumns, Key okey, Key... keys) {
    return parse(okey,keys,true, false, ParseSetup.GUESS_HEADER,skippedColumns); }

  public static Frame parse(Key okey, Key[] keys, boolean deleteOnDone, boolean singleQuote, int checkHeader) {
    return parse(okey, keys, deleteOnDone, singleQuote, checkHeader, null);
  }
  // Guess setup from inspecting the first Key only, then parse.
  // Suitable for e.g. testing setups, where the data is known to be sane.
  // NOT suitable for random user input!
  public static Frame parse(Key okey, Key[] keys, boolean deleteOnDone, boolean singleQuote, int checkHeader, int[] skippedColumns) {
    ParseSetup guessParseSetup = ParseSetup.guessSetup(keys, singleQuote, checkHeader);
    if (skippedColumns!=null) {
      guessParseSetup.setSkippedColumns(skippedColumns);
      guessParseSetup.setParseColumnIndices(guessParseSetup.getNumberColumns(), skippedColumns);
    }
    return parse(okey,keys,deleteOnDone,guessParseSetup);
  }
  public static Frame parse(Key okey, Key[] keys, boolean deleteOnDone, ParseSetup globalSetup) {
    return parse(okey,keys,deleteOnDone,globalSetup,true)._job.get();
  }
  public static ParseDataset parse(Key okey, Key[] keys, boolean deleteOnDone, ParseSetup globalSetup, boolean blocking) {
    ParseDataset pds = forkParseDataset(okey, keys, globalSetup, deleteOnDone);
    if( blocking )
      pds._job.get();
    return pds;
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
        colNames[i] = "C" + Integer.toString(i+1);
    } else { // some or all names exist, fill in blanks
      HashSet<String> nameSet = new HashSet<>(Arrays.asList(colNames));
      colNames = Arrays.copyOf(colNames, ncols);
      for(int i=0; i < ncols; i++ ) {
        if (colNames[i] == null || colNames[i].equals("")) {
          String tmp = "C" + Integer.toString(i+1);
          while (nameSet.contains(tmp)) // keep building name until unique
            tmp = tmp + tmp;
          colNames[i] = tmp;
        }
      }
    }
    return colNames;
  }

  public static Job forkParseSVMLight(final Key<Frame> dest, final Key [] keys, final ParseSetup setup) {
    int nchunks = 0;
    Vec v = null;
    // set the parse chunk size for files
    for( int i = 0; i < keys.length; ++i ) {
      Iced ice = DKV.getGet(keys[i]);
      if(ice instanceof FileVec) {
        if(i == 0) v = ((FileVec) ice);
        ((FileVec) ice).setChunkSize(setup._chunk_size);
        nchunks += ((FileVec) ice).nChunks();
        Log.info("Parse chunk size " + setup._chunk_size);
      } else if(ice instanceof Frame && ((Frame)ice).vec(0) instanceof FileVec) {
        if(i == 0) v = ((Frame)ice).vec(0);
        ((FileVec) ((Frame) ice).vec(0)).setChunkSize((Frame) ice, setup._chunk_size);
        nchunks += (((Frame) ice).vec(0)).nChunks();
        Log.info("Parse chunk size " + setup._chunk_size);
      }
    }
    final VectorGroup vg = v.group();
    final ParseDataset pds = new ParseDataset(dest);
    new Frame(pds._job._result,new String[0],new Vec[0]).delete_and_lock(pds._job); // Write-Lock BEFORE returning
    return pds._job.start(new H2OCountedCompleter() {
      @Override
      public void compute2() {
        ParseDataset.parseAllKeys(pds,keys,setup,true);
        tryComplete();
      }
    },nchunks);
  }

  /**
   * The entry-point for data set parsing.
   *
   * @param dest  name for destination key
   * @param keys  input keys
   * @param parseSetup  a generic parser setup
   * @param deleteOnDone  delete input data when finished
   * @return a new parse job
   */
  public static ParseDataset forkParseDataset(final Key<Frame> dest, final Key[] keys, final ParseSetup parseSetup, boolean deleteOnDone) {
    // Get a parser specific setup
    // FIXME: ParseSetup should be separated into two classes - one for using via Rest API as user setup
    //        and another as an internal parser setup to drive parsing.
    final ParseSetup setup = parseSetup.getFinalSetup(keys, parseSetup);

    HashSet<String> conflictingNames = setup.checkDupColumnNames();
    for( String x : conflictingNames )
    if ( x != null && !x.equals(""))
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


    // no need to set this for ORC, it is already done:
    if (!setup.getParseType().name().contains("ORC")) {
      for( int i = 0; i < keys.length; ++i ) {
        Iced ice = DKV.getGet(keys[i]);

        // set the parse chunk size for files
        if (ice instanceof FileVec) {
          ((FileVec) ice).setChunkSize(setup._chunk_size);
          Log.info("Parse chunk size " + setup._chunk_size);
        } else if (ice instanceof Frame && ((Frame) ice).vec(0) instanceof FileVec) {
          ((FileVec) ((Frame) ice).vec(0)).setChunkSize((Frame) ice, setup._chunk_size);
          Log.info("Parse chunk size " + setup._chunk_size);
        }
      }
    } else Log.info("Orc Parse chunk sizes may be different across files");

    long memsz = H2O.CLOUD.free_mem();
    if( totalParseSize > memsz*4 )
      throw new IllegalArgumentException("Total input file size of "+PrettyPrint.bytes(totalParseSize)+" is much larger than total cluster memory of "+PrettyPrint.bytes(memsz)+", please use either a larger cluster or smaller data.");

    // Fire off the parse
    ParseDataset pds = new ParseDataset(dest);
    new Frame(pds._job._result,new String[0],new Vec[0]).delete_and_lock(pds._job); // Write-Lock BEFORE returning
    for( Key k : keys ) Lockable.read_lock(k,pds._job); // Read-Lock BEFORE returning
    ParserFJTask fjt = new ParserFJTask(pds, keys, setup, deleteOnDone); // Fire off background parse
    pds._job.start(fjt, totalParseSize);
    return pds;
  }

  // Setup a private background parse job
  private ParseDataset(Key<Frame> dest) {
    _job = new Job(dest,Frame.class.getName(), "Parse");
  }

  // -------------------------------
  // Simple internal class doing background parsing, with trackable Job status
  public static class ParserFJTask extends water.H2O.H2OCountedCompleter {
    final ParseDataset _pds;
    final Key[] _keys;
    final ParseSetup _setup;
    final boolean _deleteOnDone;

    public ParserFJTask( ParseDataset pds, Key[] keys, ParseSetup setup, boolean deleteOnDone) {
      _pds = pds;
      _keys = keys;
      _setup = setup;
      _deleteOnDone = deleteOnDone;
    }
    @Override public void compute2() {
      parseAllKeys(_pds, _keys, _setup, _deleteOnDone);
      tryComplete();
    }

    // Took a crash/NPE somewhere in the parser.  Attempt cleanup.
    @Override public boolean onExceptionalCompletion(Throwable ex, CountedCompleter caller){
      parseCleanup();           // Can get called many tims
      return true;
    }

    @Override public void onCompletion(CountedCompleter caller) {
      if( _pds._job.stop_requested() )
        parseCleanup();
      _pds._mfpt = null;
    }

    private void parseCleanup() {
      assert !_pds._job.isStopped(); // Job still running till job.onExCompletion returns
      Futures fs = new Futures();
      // Find & remove all partially-built output vecs & chunks.
      // Since this is racily called, perhaps multiple times, read _mfpt only exactly once.
      MultiFileParseTask mfpt = _pds._mfpt;
      _pds._mfpt = null;        // Read once, test for null once.
      if (mfpt != null) mfpt.onExceptionCleanup(fs);
      // Assume the input is corrupt - or already partially deleted after
      // parsing.  Nuke it all - no partial Vecs lying around.
      for (Key k : _keys) Keyed.remove(k, fs);
      Keyed.remove(_pds._job._result,fs);
      fs.blockForPending();
    }
  }

  private static class CategoricalUpdateMap extends Iced {
    final int [][] map;
    public CategoricalUpdateMap(int[][] map){this.map = map;}
  }
  // --------------------------------------------------------------------------
  // Top-level parser driver
  private static ParseDataset parseAllKeys(ParseDataset pds, Key[] fkeys, ParseSetup setup, boolean deleteOnDone) {
    final Job<Frame> job = pds._job;
    assert setup._number_columns > 0;
    if( setup._column_names != null &&
        ( (setup._column_names.length == 0) ||
          (setup._column_names.length == 1 && setup._column_names[0].isEmpty())) )
      setup._column_names = null; // // FIXME: annoyingly front end sends column names as String[] {""} even if setup returned null
    if(setup._na_strings != null && setup._na_strings.length != setup._number_columns) setup._na_strings = null;
    if( fkeys.length == 0) { job.stop();  return pds;  }

    job.update(0, "Ingesting files.");
    VectorGroup vg = getByteVec(fkeys[0]).group();
    MultiFileParseTask mfpt = pds._mfpt = new MultiFileParseTask(vg,setup,job._key,fkeys,deleteOnDone);
    mfpt.doAll(fkeys);
    Log.trace("Done ingesting files.");
    if( job.stop_requested() ) return pds;

    final AppendableVec [] avs = mfpt.vecs(); // with skipped_columns excluded
    Frame fr = null;
    // Calculate categorical domain
    // Filter down to columns with some categoricals
    int n = 0;
    int parseCols = setup._parse_columns_indices.length;
    boolean sameParseColumns = setup._number_columns == parseCols; // _number_columns represent number of columns parsed

    String[] parse_column_names;
    byte[] parse_column_types;
    if (setup._column_names == null)
      setup._column_names = getColumnNames(setup._column_types.length, null);

    boolean typesSameParseColumns = setup._column_types.length==parseCols;
    boolean namesSameparseColumns = setup._column_names.length==parseCols;

    if (sameParseColumns) {
      parse_column_names = setup._column_names;
    } else {
      parse_column_names = new String[parseCols];
      parse_column_types = new byte[parseCols];


      for (int cindex = 0; cindex < parseCols; cindex++) {
        parse_column_names[cindex] = namesSameparseColumns?setup._column_names[cindex]:
                setup._column_names[setup._parse_columns_indices[cindex]];
        parse_column_types[cindex] = typesSameParseColumns?setup._column_types[cindex]:
                setup._column_types[setup._parse_columns_indices[cindex]];
      }

      setup._column_types=parse_column_types;
    }
    setup._column_names = getColumnNames(avs.length, parse_column_names);

    int[] ecols2 = new int[parseCols];
    for (int i = 0; i < parseCols; i++) {
      if (avs[i].get_type() == Vec.T_CAT) // Intended type is categorical (even though no domain has been set)?
        ecols2[n++] = i;
    }
    final int[] ecols = Arrays.copyOf(ecols2, n); // skipped columns are excluded already
    // If we have any, go gather unified categorical domains
    if( n > 0 ) {
      if (!setup.getParseType().isDomainProvided) { // Domains are not provided via setup we need to collect them
        job.update(0, "Collecting categorical domains across nodes.");
        {
          GatherCategoricalDomainsTask gcdt = new GatherCategoricalDomainsTask(mfpt._cKey, ecols,
                  mfpt._parseSetup._parse_columns_indices).doAllNodes();
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
            throw new H2OParseException("Exceeded categorical limit on columns "+ offendingColNames+".   " +
                    "Consider reparsing these columns as a string or skip parsing the offending columns by setting" +
                    " the skipped_columns list in Python/R/Java APIs.");
        }
        Log.trace("Done collecting categorical domains across nodes.");
      } else {
        // Ignore offending domains
        for (int i = 0; i < ecols.length; i++) {
          avs[ecols[i]].setDomain(setup._domains[ecols[i]]);
        }
      }

      job.update(0, "Compressing data.");

      fr = new Frame(job._result, setup._column_names, AppendableVec.closeAll(avs));
      fr.update(job);
      Log.trace("Done compressing data.");
      if (!setup.getParseType().isDomainProvided) {
        // Update categoricals to the globally agreed numbering
        Vec[] evecs = new Vec[ecols.length];
        for( int i = 0; i < evecs.length; ++i ) evecs[i] = fr.vecs()[ecols[i]];
        job.update(0, "Unifying categorical domains across nodes.");
        {
          // new CreateParse2GlobalCategoricalMaps(mfpt._cKey).doAll(evecs);
          // Using Dtask since it starts and returns faster than an MRTask
          CreateParse2GlobalCategoricalMaps[] fcdt = new CreateParse2GlobalCategoricalMaps[H2O.CLOUD.size()];
          RPC[] rpcs = new RPC[H2O.CLOUD.size()];
          for (int i = 0; i < fcdt.length; i++){
            H2ONode[] nodes = H2O.CLOUD.members();
            fcdt[i] = new CreateParse2GlobalCategoricalMaps(mfpt._cKey, fr._key, ecols, mfpt._parseSetup._parse_columns_indices);
            rpcs[i] = new RPC<>(nodes[i], fcdt[i]).call();
          }
          for (RPC rpc : rpcs)
            rpc.get();

          new UpdateCategoricalChunksTask(mfpt._cKey, mfpt._chunk2ParseNodeMap).doAll(evecs);
          MultiFileParseTask._categoricals.remove(mfpt._cKey);
        }
        Log.trace("Done unifying categoricals across nodes.");
      }
    } else {                    // No categoricals case
      job.update(0,"Compressing data.");
      fr = new Frame(job._result, setup._column_names,AppendableVec.closeAll(avs));
      Log.trace("Done closing all Vecs.");
    }
    // Check for job cancellation
    if ( job.stop_requested() ) return pds;

    // SVMLight is sparse format, there may be missing chunks with all 0s, fill them in
    if (setup._parse_type.equals(SVMLight_INFO))
      new SVFTask(fr).doAllNodes();

    // Check for job cancellation
    if ( job.stop_requested() ) return pds;

    ParseWriter.ParseErr [] errs = ArrayUtils.append(setup.errs(),mfpt._errors);
    if(errs.length > 0) {
      // compute global line numbers for warnings/errs
      HashMap<String, Integer> fileChunkOffsets = new HashMap<>();
      for (int i = 0; i < mfpt._fileChunkOffsets.length; ++i)
        fileChunkOffsets.put(fkeys[i].toString(), mfpt._fileChunkOffsets[i]);
      long[] espc = fr.anyVec().espc();
      for (int i = 0; i < errs.length; ++i) {
        if(fileChunkOffsets.containsKey(errs[i]._file)) {
          int espcOff = fileChunkOffsets.get(errs[i]._file);
          errs[i]._gLineNum = espc[espcOff + errs[i]._cidx] + errs[i]._lineNum;
          errs[i]._lineNum = errs[i]._gLineNum - espc[espcOff];
        }
      }
      SortedSet<ParseWriter.ParseErr> s = new TreeSet<>(new Comparator<ParseWriter.ParseErr>() {
        @Override
        public int compare(ParseWriter.ParseErr o1, ParseWriter.ParseErr o2) {
          long res = o1._gLineNum - o2._gLineNum;
          if (res == 0) res = o1._byteOffset - o2._byteOffset;
          if (res == 0) return o1._err.compareTo(o2._err);
          return (int) res < 0 ? -1 : 1;
        }
      });
      Collections.addAll(s, errs);
      String[] warns = new String[s.size()];
      int i = 0;
      for (ParseWriter.ParseErr err : s)
        Log.warn(warns[i++] = err.toString());
      job.setWarnings(warns);
    }
    job.update(0,"Calculating data summary.");
    logParseResults(fr);
    // Release the frame for overwriting
    fr.update(job);
    Frame fr2 = DKV.getGet(fr._key);
    assert fr2._names.length == fr2.numCols();
    fr.unlock(job);
    // Remove CSV files from H2O memory
    if( deleteOnDone )
      for( Key k : fkeys ) {
        DKV.remove(k);
        assert DKV.get(k) == null : "Input key " + k + " not deleted during parse";
      }
    return pds;
  }
  private static class CreateParse2GlobalCategoricalMaps extends DTask<CreateParse2GlobalCategoricalMaps> {
    private final Key   _parseCatMapsKey;
    private final Key   _frKey;
    private final int[] _ecol;
    private final int[] _parseColumns;

    private CreateParse2GlobalCategoricalMaps(Key parseCatMapsKey, Key key, int[] ecol, int[] parseColumns) {
      _parseCatMapsKey = parseCatMapsKey;
      _frKey = key;
      _ecol = ecol; // contains the categoricals column indices only
      _parseColumns = parseColumns;
    }

    @Override public void compute2() {
      Frame _fr = DKV.getGet(_frKey); // does not contain skipped columns
      // get the node local category->ordinal maps for each column from initial parse pass
      if( !MultiFileParseTask._categoricals.containsKey(_parseCatMapsKey) ) {
        tryComplete();
        return;
      }
        final Categorical[] parseCatMaps = MultiFileParseTask._categoricals.get(_parseCatMapsKey); // include skipped columns
        int[][] _nodeOrdMaps = new int[_ecol.length][];

        // create old_ordinal->new_ordinal map for each cat column
        for (int eColIdx = 0; eColIdx < _ecol.length; eColIdx++) {
          int colIdx = _parseColumns[_ecol[eColIdx]];
          if (parseCatMaps[colIdx].size() != 0) {
            _nodeOrdMaps[eColIdx] = MemoryManager.malloc4(parseCatMaps[colIdx].maxId() + 1);
            Arrays.fill(_nodeOrdMaps[eColIdx], -1);
            //Bulk String->BufferedString conversion is slightly faster, but consumes memory
            final BufferedString[] unifiedDomain = _fr.vec(_ecol[eColIdx]).isCategorical()?
                    BufferedString.toBufferedString(_fr.vec(_ecol[eColIdx]).domain()):new BufferedString[0];
            //final String[] unifiedDomain = _fr.vec(colIdx).domain();
            for (int i = 0; i < unifiedDomain.length; i++) {
              //final BufferedString cat = new BufferedString(unifiedDomain[i]);
              if (parseCatMaps[colIdx].containsKey(unifiedDomain[i])) {
                _nodeOrdMaps[eColIdx][parseCatMaps[colIdx].getTokenId(unifiedDomain[i])] = i;
              }
            }
          } else {
            Log.debug("Column " + colIdx + " was marked as categorical but categorical map is empty!");
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
            if (old < 0 || (_parse2GlobalCatMaps[i] != null && old >= _parse2GlobalCatMaps[i].length))
              chk.reportBrokenCategorical(i, j, old, _parse2GlobalCatMaps[i], _fr.vec(i).domain().length);
            if(_parse2GlobalCatMaps[i] != null && _parse2GlobalCatMaps[i][old] < 0)
              throw new H2OParseException("Error in unifying categorical values. This is typically "
                  +"caused by unrecognized characters in the data.\n The problem categorical value "
                  +"occurred in the " + PrettyPrint.withOrdinalIndicator(i+1)+ " categorical col, "
                  +PrettyPrint.withOrdinalIndicator(chk.start() + j) +" row.");
            if (_parse2GlobalCatMaps[i] != null)
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
    private final int[] _parseColumns;

    private GatherCategoricalDomainsTask(Key k, int[] ccols, int[] parseColumns) {
      _k = k;
      _catColIdxs = ccols;
      _parseColumns = parseColumns;
    }

    @Override
    public void setupLocal() {
      if (!MultiFileParseTask._categoricals.containsKey(_k)) return;
      _packedDomains = new byte[_catColIdxs.length][];
      final BufferedString[][] _perColDomains = new BufferedString[_catColIdxs.length][];
      final Categorical[] _colCats = MultiFileParseTask._categoricals.get(_k); // still refer to all columns
      int i = 0;
      for (int col : _catColIdxs) {
        _colCats[_parseColumns[col]].convertToUTF8(_parseColumns[col] + 1);
        _perColDomains[i] = _colCats[_parseColumns[col]].getColumnDomain();
        Arrays.sort(_perColDomains[i]);
        _packedDomains[i] = PackedDomains.pack(_perColDomains[i]);
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
          domtasks[i] = new H2OCountedCompleter(currThrPriority()) {
            @Override
            public void compute2() {
              _packedDomains[fi] = PackedDomains.merge(_packedDomains[fi], other._packedDomains[fi]);
              tryComplete();
            }
          };
        }
        ForkJoinTask.invokeAll(domtasks);
      }
      Log.trace("Done merging domains.");
    }

    public int getDomainLength(int colIdx) {
      return _packedDomains == null ? 0 : PackedDomains.sizeOf(_packedDomains[colIdx]);
    }

    public String[] getDomain(int colIdx) {
      return _packedDomains == null ? null : PackedDomains.unpackToStrings(_packedDomains[colIdx]);
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
              Value val = Value.STORE_get(vec.chunkKey(fi)); // Local-get only
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
              Value val = Value.STORE_get(k);   // Local-get only
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
    private final Key<Job> _jobKey;
    // A mapping of Key+ByteVec to rolling total Chunk counts.
    private final int[]  _fileChunkOffsets;

    // OUTPUT fields:
    FVecParseWriter[] _dout;

    int _reservedKeys;
    private ParseWriter.ParseErr[] _errors = new ParseWriter.ParseErr[0];

    MultiFileParseTask(VectorGroup vg,  ParseSetup setup, Key<Job> jobKey, Key[] fkeys, boolean deleteOnDone ) {
      _vg = vg; _parseSetup = setup;
      _vecIdStart = _vg.reserveKeys(_reservedKeys = _parseSetup._parse_type.equals(SVMLight_INFO) ? 100000000 : setup._number_columns);
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
      if(_parseSetup._parse_type.equals(SVMLight_INFO)) {
        _parseSetup._number_columns = res.length;
        _parseSetup._column_types = new byte[res.length];
        Arrays.fill(_parseSetup._column_types,Vec.T_NUM);
      }
      boolean columnsSkipped = nCols<_parseSetup._number_columns;
      for(int i = 0; i < res.length; ++i) {
        byte columnTypes = columnsSkipped ? _parseSetup._column_types[_parseSetup._parse_columns_indices[i]] : _parseSetup._column_types[i];
        int vecIDStartPI = columnsSkipped?(_vecIdStart+_parseSetup._parse_columns_indices[i]):_vecIdStart + i;
        res[i] = new AppendableVec(_vg.vecKey(vecIDStartPI), espc, columnTypes, 0);
      }
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
      final long [] espc = MemoryManager.malloc8(nchunks);
      final byte[] ctypes = localSetup._column_types; // SVMLight only uses numeric types, sparsely represented as a null
      for(int i = 0; i < avs.length; ++i)
        avs[i] = new AppendableVec(_vg.vecKey(i + _vecIdStart), espc, ctypes==null ? /*SVMLight*/Vec.T_NUM :
                ctypes[i], chunkOff);
      return localSetup._parse_type.equals(SVMLight_INFO)
        ? new SVMLightFVecParseWriter(_vg, _vecIdStart,chunkOff, _parseSetup._chunk_size, avs,
              _parseSetup._parse_columns_indices)
        : new FVecParseWriter(_vg, chunkOff, categoricals(_cKey, localSetup._number_columns),
              localSetup._column_types, _parseSetup._chunk_size, avs, _parseSetup._parse_columns_indices);
    }

    // Called once per file
    @Override public void map( Key key ) {
      if( _jobKey.get().stop_requested() ) return;
      // FIXME: refactor parser setup to be configurable via parser object
      ParseSetup localSetup = (ParseSetup) _parseSetup.clone();
      ByteVec vec = getByteVec(key);
      final int chunkStartIdx = _fileChunkOffsets[_lo];
      Log.trace("Begin a map stage of a file parse with start index " + chunkStartIdx + ".");

      DecryptionTool decryptionTool = _parseSetup.getDecryptionTool();
      byte[] zips = vec.getFirstBytes();
      ZipUtil.Compression cpr = ZipUtil.guessCompressionMethod(zips);
      if (localSetup._check_header == ParseSetup.HAS_HEADER) { //check for header on local file
        byte[] bits = decryptionTool.decryptFirstBytes(ZipUtil.unzipBytes(zips, cpr, localSetup._chunk_size));
        localSetup._check_header = localSetup.parser(_jobKey).fileHasHeader(bits, localSetup);
      }
      // Parse the file
      try {
        switch( cpr ) {
        case NONE:
          ParserInfo.ParseMethod pm = _parseSetup.parseMethod(_keys.length, vec);
          Log.debug("Key " + key + " will be parsed using method " + pm + ".");

          if(pm == ParserInfo.ParseMethod.DistributedParse) {
            new DistributedParse(_vg, localSetup, _vecIdStart, chunkStartIdx, this, key, vec.nChunks()).dfork(vec).getResult(false);
            for( int i = 0; i < vec.nChunks(); ++i )
              _chunk2ParseNodeMap[chunkStartIdx + i] = vec.chunkKey(i).home_node().index();
          } else if(pm == ParserInfo.ParseMethod.StreamParse || pm == ParserInfo.ParseMethod.SequentialParse){
            localSetup = ParserService.INSTANCE.getByInfo(localSetup._parse_type).setupLocal(vec,localSetup);
            Parser p = localSetup.parser(_jobKey);

            final FVecParseWriter writer = makeDout(localSetup,chunkStartIdx,vec.nChunks());
            final ParseWriter dout;
            if (pm == ParserInfo.ParseMethod.StreamParse) {
              try (InputStream bvs = vec.openStream(_jobKey)) {
                dout = p.streamParse(decryptionTool.decryptInputStream(bvs), writer);
              }
            } else { // pm == ParserInfo.ParseMethod.SequentialParse
              dout = p.sequentialParse(vec, writer);
            }
            _dout[_lo] = ((FVecParseWriter) dout).close(_fs);
            _errors = _dout[_lo].removeErrors();

            chunksAreLocal(vec,chunkStartIdx,key);
          } else throw H2O.unimpl();
          break;
        case ZIP: {
          localSetup = ParserService.INSTANCE.getByInfo(localSetup._parse_type).setupLocal(vec,localSetup);
          // Zipped file; no parallel decompression;
          InputStream bvs = vec.openStream(_jobKey);
          ZipInputStream zis = new ZipInputStream(bvs);

          if (ZipUtil.isZipDirectory(key)) {  // file is a zip if multiple files
            zis.getNextEntry();          // first ZipEntry describes the directory
          }
          ZipEntry ze = zis.getNextEntry(); // Get the *FIRST* entry
          InputStream dec = decryptionTool.decryptInputStream(zis);
          // There is at least one entry in zip file and it is not a directory.
          if( ze != null && !ze.isDirectory() )
            _dout[_lo] = streamParse(dec,localSetup, makeDout(localSetup,chunkStartIdx,vec.nChunks()), bvs);
            _errors = _dout[_lo].removeErrors();
          dec.close();       // Confused: which zipped file to decompress
          chunksAreLocal(vec,chunkStartIdx,key);
          break;
        }
        case GZIP: {
          localSetup = ParserService.INSTANCE.getByInfo(localSetup._parse_type).setupLocal(vec,localSetup);
          InputStream bvs = vec.openStream(_jobKey);
          // Zipped file; no parallel decompression;
          _dout[_lo] = streamParse(decryptionTool.decryptInputStream(new GZIPInputStream(bvs)),
                  localSetup, makeDout(localSetup,chunkStartIdx,vec.nChunks()),bvs);
          _errors = _dout[_lo].removeErrors();
          // set this node as the one which processed all the chunks
          chunksAreLocal(vec,chunkStartIdx,key);
          break;
        }
        }
        Log.trace("Finished a map stage of a file parse with start index "+chunkStartIdx+".");
      } catch( IOException ioe ) {
        throw new RuntimeException(ioe);
      } catch (H2OParseException pe0) {
        // Rebuild identical exception and stack trace, but add key to msg
        throw pe0.resetMsg(pe0.getMessage()+" for "+key);
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
      if(_errors == null)
        _errors = mfpt._errors;
      else if(_errors.length < 20) {
        _errors = ArrayUtils.append(_errors, mfpt._errors);
        if(_errors.length > 20)
          _errors = Arrays.copyOf(_errors,20);
      }
      Log.trace("Finished a reduce stage of a file parse.");
    }

    // ------------------------------------------------------------------------
    // Zipped file; no parallel decompression; decompress into local chunks,
    // parse local chunks; distribute chunks later.
    private FVecParseWriter streamParse(final InputStream is, final ParseSetup localSetup,FVecParseWriter dout, InputStream bvs) throws IOException {
      // All output into a fresh pile of NewChunks, one per column
      Parser p = localSetup.parser(_jobKey);
      // assume 2x inflation rate
      if(localSetup._parse_type.isParallelParseSupported())
        p.streamParseZip(is, dout, bvs);
      else
        p.streamParse(is,dout);
      // Parse all internal "chunks", until we drain the zip-stream dry.  Not
      // real chunks, just flipping between 32K buffers.  Fills up the single
      // very large NewChunk.
      dout.close(_fs);
      return dout;
    }

    // ------------------------------------------------------------------------
    private static class DistributedParse extends MRTask<DistributedParse> {
      private ParseSetup _setup;
      private final int _vecIdStart;
      private final int _startChunkIdx; // for multifile parse, offset of the first chunk in the final dataset
      private final VectorGroup _vg;
      private FVecParseWriter _dout;
      private final Key _cKey;  // Parse-local-categoricals key
      private final Key<Job> _jobKey;
      private transient final MultiFileParseTask _outerMFPT;
      private transient final Key _srckey; // Source/text file to delete on done
      private transient NonBlockingSetInt _visited;
      private transient long [] _espc;
      final int _nchunks;

      DistributedParse(VectorGroup vg, ParseSetup setup, int vecIdstart, int startChunkIdx, MultiFileParseTask mfpt, Key srckey, int nchunks) {
        super(null);
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
        _setup = ParserService.INSTANCE.getByInfo(_setup._parse_type).setupLocal(_fr.anyVec(),_setup);
      }
      @Override public void map( Chunk in ) {
        if( _jobKey.get().stop_requested() ) throw new Job.JobCancelledException();
        AppendableVec [] avs = new AppendableVec[_setup._parse_columns_indices.length];
        boolean notShrunkColumns = _setup._parse_columns_indices.length==_setup._number_columns;
          for (int i = 0; i < avs.length; ++i)
            if (_setup._column_types == null) // SVMLight which does not support skip columns anyway
              avs[i] = new AppendableVec(_vg.vecKey(_vecIdStart + i), _espc, Vec.T_NUM, _startChunkIdx);
            else
              avs[i] = notShrunkColumns?
                      new AppendableVec(_vg.vecKey(_vecIdStart + i), _espc, _setup._column_types[i], _startChunkIdx)
                      :new AppendableVec(_vg.vecKey(_vecIdStart + _setup._parse_columns_indices[i]),
                      _espc, _setup._column_types[_setup._parse_columns_indices[i]], _startChunkIdx);

        // Break out the input & output vectors before the parse loop
        FVecParseReader din = new FVecParseReader(in);
        FVecParseWriter dout;
        // Get a parser
        Parser p = _setup.parser(_jobKey);
        switch(_setup._parse_type.name()) {
        case "ARFF":
        case "CSV":
        case "PARQUET":
          Categorical [] categoricals = categoricals(_cKey, _setup._number_columns);
          dout = new FVecParseWriter(_vg,_startChunkIdx + in.cidx(), categoricals, _setup._column_types,
                  _setup._chunk_size, avs, _setup._parse_columns_indices); //TODO: use _setup._domains instead of categoricals
          break;
        case "SVMLight":
          dout = new SVMLightFVecParseWriter(_vg, _vecIdStart, in.cidx() + _startChunkIdx, _setup._chunk_size,
                  avs, _setup._parse_columns_indices);
          break;
        case "ORC":  // setup special case for ORC
          Categorical [] orc_categoricals = categoricals(_cKey, _setup._number_columns);
          dout = new FVecParseWriter(_vg, in.cidx() + _startChunkIdx, orc_categoricals, _setup._column_types,
                  _setup._chunk_size, avs, _setup._parse_columns_indices);
          break;
        default: // FIXME: should not be default and creation strategy should be forwarded to ParserProvider
          dout = new FVecParseWriter(_vg, in.cidx() + _startChunkIdx, null, _setup._column_types,
                  _setup._chunk_size, avs, _setup._parse_columns_indices);
          break;
        }
        if ((_setup.getParseType().name().toLowerCase().equals("svmlight") ||
                (_setup.getParseType().name().toLowerCase().equals("avro") ))
                && ((_setup.getSkippedColumns() != null) && (_setup.getSkippedColumns().length >0)))
          throw new H2OIllegalArgumentException("Parser: skipped_columns are not supported for " +
                  "SVMlight or Avro parsers.");

        if (_setup.getSkippedColumns() !=null &&
                ((_setup.get_parse_columns_indices()==null) || (_setup.get_parse_columns_indices().length==0)))
          throw new H2OIllegalArgumentException("Parser:  all columns in the file are skipped and no H2OFrame" +
                  " can be returned."); // Need this to send error message to R

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
            Value v = Value.STORE_get(in.vec().chunkKey(cidx));
            if (v == null || !v.isPersisted()) return; // Not found, or not on disk somewhere
            v.freePOJO();           // Eagerly toss from memory
            v.freeMem();
          }
        }
      }
      @Override public void reduce(DistributedParse dp) {
        _dout.reduce(dp._dout);

      }

      @Override public void postGlobal() {
        super.postGlobal();
        _outerMFPT._dout[_outerMFPT._lo] = _dout;
        if(_dout.hasErrors()) {
          ParseWriter.ParseErr [] errs = _dout.removeErrors();
          for(ParseWriter.ParseErr err:errs)err._file = FileVec.getPathForKey(_srckey).toString();
          Arrays.sort(errs, new Comparator<ParseWriter.ParseErr>() {
            @Override
            public int compare(ParseWriter.ParseErr o1, ParseWriter.ParseErr o2) {
              return (int)(o1._byteOffset - o2._byteOffset);
            }
          });
          _outerMFPT._errors = errs;
        }
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
    Futures onExceptionCleanup(Futures fs) {
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
  public static void logParseResults(Frame fr) {
    long numRows = fr.anyVec().length();
    Log.info("Parse result for " + fr._key + " (" + Long.toString(numRows) + " rows, "+Integer.toString(fr.numCols())+" columns):");
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

      boolean launchedWithHadoopJar = H2O.ARGS.launchedWithHadoopJar();
      boolean printLogSeparatorToStdout = false;
      boolean printColumnToStdout;
      {
        // Print information to stdout for this many leading columns.
        final int MAX_HEAD_TO_PRINT_ON_STDOUT = 10;

        // Print information to stdout for this many trailing columns.
        final int MAX_TAIL_TO_PRINT_ON_STDOUT = 10;

        if (launchedWithHadoopJar) {
          printColumnToStdout = true;
        } else if (vecArr.length <= (MAX_HEAD_TO_PRINT_ON_STDOUT + MAX_TAIL_TO_PRINT_ON_STDOUT)) {
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
  public static class H2OParseException extends RuntimeException {
    public H2OParseException(String msg){super(msg);}
    public H2OParseException(String msg, Throwable cause){super(msg,cause);}
    public H2OParseException(Throwable cause){super(cause);}

    public H2OParseException resetMsg(String msg) {
      H2OParseException pe1 = new H2OParseException(msg,getCause());
      pe1.setStackTrace(getStackTrace());
      return pe1;
    }
  }
}




package water.parser.orc;

import org.apache.hadoop.hive.ql.io.orc.OrcFile;
import org.apache.hadoop.hive.ql.io.orc.Reader;
import org.apache.hadoop.hive.ql.io.orc.StripeInformation;
import org.apache.hadoop.hive.serde2.objectinspector.StructObjectInspector;
import water.DKV;
import water.H2O;
import water.Job;
import water.Key;
import water.fvec.*;
import water.parser.*;
import water.persist.VecFileSystem;

import java.io.IOException;
import java.util.List;


/**
 * Orc parser provider.
 */
public class OrcParserProvider extends ParserProvider {

  public static class OrcParserInfo extends ParserInfo {

    public OrcParserInfo() {
      super("ORC", DefaultParserProviders.MAX_CORE_PRIO + 20, true, true, false);
    }

    public ParseMethod parseMethod(int nfiles, int nchunks){
      int ncores_tot = H2O.NUMCPUS*H2O.CLOUD.size();
      // prefer StreamParse if we have enough files to keep cluster busy
      // ORC stream parse is more efficient
      return
          nfiles >= (ncores_tot >> 1)  // got enough files to keep cluster busy
              ?ParseMethod.StreamParse:ParseMethod.StreamParse;//ParseMethod.DistributedParse;
    }
  }
  /* Setup for this parser */
  static ParserInfo ORC_INFO = new OrcParserInfo();

  @Override
  public ParserInfo info() {
    return ORC_INFO;
  }

  @Override
  public Parser createParser(ParseSetup setup, Key<Job> jobKey) {
    return new OrcParser(setup, jobKey);
  }

  @Override
  public ParseSetup guessSetup(ByteVec bv, byte [] bits, byte sep, int ncols, boolean singleQuotes,
                               int checkHeader, String[] columnNames, byte[] columnTypes,
                               String[][] domains, String[][] naStrings) {
    if(bv instanceof FileVec)
      return readSetup((FileVec)bv, columnNames, columnTypes);
    throw new UnsupportedOperationException("ORC only works on Files");
  }

  /**
   * Use only the first file to setup everything.
   *
   * @param inputs  input keys
   * @param requiredSetup  user given parser setup
   * @return
   */
  @Override
  public ParseSetup createParserSetup(Key[] inputs, ParseSetup requiredSetup) {

    FileVec f;
    Object frameOrVec = DKV.getGet(inputs[0]);

    if (frameOrVec instanceof water.fvec.Frame)
      f = (FileVec) ((Frame) frameOrVec).vec(0);
    else
      f = (FileVec) frameOrVec;
    return readSetup(f, requiredSetup.getColumnNames(), requiredSetup.getColumnTypes());
  }

  private Reader getReader(FileVec f) throws IOException {
    return OrcFile.createReader(VecFileSystem.VEC_PATH.getFileSystem(VecFileSystem.makeConfiguration(f)), VecFileSystem.VEC_PATH);
  }

  /*
  public static final byte T_BAD  =  0; // No none-NA rows (triple negative! all NAs or zero rows)
  public static final byte T_UUID =  1; // UUID
  public static final byte T_STR  =  2; // String
  public static final byte T_NUM  =  3; // Numeric, but not categorical or time
  public static final byte T_CAT  =  4; // Integer, with a categorical/factor String mapping
  public static final byte T_TIME =  5; // Long msec since the Unix Epoch - with a variety of display/parse options
   */
  public static byte [][] supported_type_conversions = new byte[][]{
      {0,0,0,0,0,0}, // T_BAD
      {1,0,0,0,0,0}, // UUID
      {1,0,0,0,1,1}, // T_STR
      {1,0,0,0,0,0}, // T_NUM
      {1,0,1,0,0,0}, // T_CAT
      {1,0,0,0,0,0}, // T_TIME
  };
  /**
   * This method will create the readers and others info needed to parse an orc file.
   * In addition, it will not over-ride the columnNames, columnTypes that the user
   * may want to force upon it.  However, we only allow users to set column types to
   * enum at this point and ignore all the other requests.
   *
   * @param f
   * @param columnNames
   * @param columnTypes
   * @return
   */
  public ParseSetup readSetup(FileVec f, String[] columnNames, byte[] columnTypes) {
    try {
      Reader orcFileReader = getReader(f);
      StructObjectInspector insp = (StructObjectInspector) orcFileReader.getObjectInspector();
      OrcParser.OrcParseSetup stp = OrcParser.deriveParseSetup(orcFileReader, insp);

      // change back the columnNames and columnTypes if they are specified already
      if (!(columnNames == null) && (stp.getAllColNames().length == columnNames.length)) { // copy column name
        stp.setColumnNames(columnNames);
        stp.setAllColNames(columnNames);
      }

      if (columnTypes != null) { // copy enum type only

        byte[] old_columnTypes = stp.getColumnTypes();
        String[] old_columnTypeNames = stp.getColumnTypesString();
        for (int index = 0; index < columnTypes.length; index++) {
          if(columnTypes[index] != old_columnTypes[index]){
            if(supported_type_conversions[old_columnTypes[index]][columnTypes[index]] == 1){
              old_columnTypes[index] = columnTypes[index];
            } else {
              stp.addErrs(new ParseWriter.UnsupportedTypeOverride(f._key.toString(),Vec.TYPE_STR[old_columnTypes[index]], Vec.TYPE_STR[columnTypes[index]],columnNames[index]));
            }
          }
          if (columnTypes[index] == Vec.T_CAT || columnTypes[index] == Vec.T_BAD || columnTypes[index] == Vec.T_TIME)  // only copy the enum types
            old_columnTypes[index] = columnTypes[index];
        }
        stp.setColumnTypes(old_columnTypes);
        stp.setColumnTypeStrings(old_columnTypeNames);
      }

      List<StripeInformation> stripesInfo = orcFileReader.getStripes();
      if(stripesInfo.size() == 0) { // empty file
        f.setChunkSize(stp._chunk_size = (int)f.length());
        return stp;
      }
      f.setNChunks(stripesInfo.size());
      stp._chunk_size = f._chunkSize;
      assert f.nChunks() == stripesInfo.size(); // ORC parser needs one-to one mapping between chunk and strip (just ids, offsets do not matter)
      return stp;
    } catch(IOException ioe) {
      throw new RuntimeException(ioe);
    }
  }

  @Override
  public ParseSetup setupLocal(Vec v, ParseSetup setup){
    if(!(v instanceof FileVec)) throw H2O.unimpl("ORC only implemented for HDFS / NFS files");
    try {
      ((OrcParser.OrcParseSetup)setup).setOrcFileReader(getReader((FileVec)v));
      return setup;

    } catch (IOException e) {throw new RuntimeException(e);}
  }
}

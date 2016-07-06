package water.parser.orc;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;

import org.apache.hadoop.hive.ql.io.orc.OrcFile;
import org.apache.hadoop.hive.ql.io.orc.Reader;
import org.apache.hadoop.hive.serde2.objectinspector.StructObjectInspector;
import water.*;
import water.fvec.*;
import water.parser.*;
import water.persist.PersistHdfs;

import java.io.IOException;

import static water.fvec.FileVec.getPathForKey;


/**
 * Orc parser provider.
 */
public class OrcParserProvider extends ParserProvider {

  /* Setup for this parser */
  static ParserInfo ORC_INFO = new ParserInfo("ORC", DefaultParserProviders.MAX_CORE_PRIO + 20, true, true);

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
      return readSetup((FileVec)bv);
    throw new UnsupportedOperationException("ORC only works on Files");
  }

  @Override
  public ParseSetup createParserSetup(Key[] inputs, ParseSetup requiredSetup) {
    if(inputs.length != 1)
      throw H2O.unimpl("ORC only supports single file parse at the moment");
    FileVec f = DKV.getGet(inputs[0]);
    return readSetup(f);
  }
  private Reader getReader(FileVec f) throws IOException {
    String strPath = getPathForKey(f._key);
    Path path = new Path(strPath);
    if(f instanceof HDFSFileVec)
      return OrcFile.createReader(PersistHdfs.getFS(strPath), path);
    else
      return OrcFile.createReader(path, OrcFile.readerOptions(new Configuration()));
  }
  public ParseSetup readSetup(FileVec f) {
    try {
      Reader orcFileReader = getReader(f);
      StructObjectInspector insp = (StructObjectInspector) orcFileReader.getObjectInspector();
      OrcParser.OrcParseSetup stp = OrcParser.deriveParseSetup(orcFileReader, insp);
      if(stp.stripesInfo.length == 0) { // empty file
        f.setChunkSize(stp._chunk_size = (int)f.length());
        return stp;
      }
      stp._chunk_size = (int)(f.length()/(stp.stripesInfo.length));
      if((f.length()%stp.stripesInfo.length) != 0) // need  exact match between stripes and chunks
        stp._chunk_size = (int)((f.length()+stp.stripesInfo.length)/stp.stripesInfo.length);
      f.setChunkSize(stp._chunk_size);
      assert f.nChunks() == stp.stripesInfo.length; // ORC parser needs one-to one mapping between chunk and strip (just ids, offsets do not matter)
      return stp;
    } catch(IOException ioe) {
      throw new RuntimeException(ioe);
    }
  }
  @Override
  public void setupLocal(Vec v, ParseSetup setup){
    if(!(v instanceof FileVec)) throw H2O.unimpl("ORC only implemented for HDFS / NFS files");
    try {
      if(((OrcParser.OrcParseSetup)setup).getOrcFileReader() == null)
        ((OrcParser.OrcParseSetup)setup).setOrcFileReader(getReader((FileVec)v));
    } catch (IOException e) {throw new RuntimeException(e);}
  }
}

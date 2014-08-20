package water.api;

import water.*;
import water.api.DownloadDataHandler.DownloadData;
import water.fvec.Frame;


import java.io.InputStream;

public class DownloadDataHandler extends Handler<DownloadData, DownloadDataV1> { // TODO: recursive generics seem to prevent more specific types here
  @Override protected int min_ver() { return 1; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  protected static final class DownloadData extends Iced {
    //Input
    Key src_key;
    boolean hex_string;

    //Output
    InputStream csv;
    String filename;
  }

  public DownloadDataV1 fetch(int version, DownloadData server) {

    if (DKV.get(server.src_key) == null) throw new IllegalArgumentException(server.src_key.toString() + " not found.");
    Object value = DKV.get(server.src_key).get();
    server.csv = ((Frame) value).toCSV(true, server.hex_string);
    // Clean up Key name back to something resembling a file system name.  Hope
    // the user's browser actually asks for what to do with the suggested
    // filename.  Without this code, my FireFox would claim something silly
    // like "no helper app installed", then fail the download.
    String s = server.src_key.toString();
    int x = s.length()-1;
    boolean dot=false;
    for( ; x >= 0; x-- )
      if( !Character.isLetterOrDigit(s.charAt(x)) && s.charAt(x)!='_' )
        if( s.charAt(x)=='.' && !dot ) dot=true;
        else break;
    String suggested_fname = s.substring(x+1).replace(".hex", ".csv");
    if( !suggested_fname.endsWith(".csv") )
      suggested_fname = suggested_fname+".csv";
    server.filename = suggested_fname;
    return schema(version).fillFromImpl(server);
  }

  @Override protected void compute2() {
    throw H2O.unimpl();
  }
  @Override protected DownloadDataV1 schema(int version) { return new DownloadDataV1(); }
}

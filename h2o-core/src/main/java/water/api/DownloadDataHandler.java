package water.api;

import water.DKV;
import water.exceptions.H2OKeyNotFoundArgumentException;
import water.fvec.Frame;
import water.H2O; 

import java.io.InputStream;

public class DownloadDataHandler extends Handler { // TODO: recursive generics seem to prevent more specific types here

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public DownloadDataV3 fetch(int version, DownloadDataV3 server) {

    if (DKV.get(server.frame_id.key()) == null) throw new H2OKeyNotFoundArgumentException("key", server.frame_id.key());
    Frame value = server.frame_id.key().get();
    InputStream is = value.toCSV(true, server.hex_string);
    java.util.Scanner scanner = new java.util.Scanner(is).useDelimiter("\\A");
    server.csv = (scanner.hasNext() ? scanner.next() : "");

    // Clean up Key name back to something resembling a file system name.  Hope
    // the user's browser actually asks for what to do with the suggested
    // filename.  Without this code, my FireFox would claim something silly
    // like "no helper app installed", then fail the download.
    String s = server.frame_id.toString();
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
    return server;
  }

  @SuppressWarnings("unused")
  public DownloadDataV3 fetchStreaming(int version, DownloadDataV3 server) {
    throw H2O.fail("Function fetchStreaming should never be called.");
  }
}

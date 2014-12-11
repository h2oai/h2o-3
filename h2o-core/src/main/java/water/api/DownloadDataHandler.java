package water.api;

import water.DKV;
import water.fvec.Frame;

import java.io.InputStream;

public class DownloadDataHandler extends Handler { // TODO: recursive generics seem to prevent more specific types here
  @Override protected int min_ver() { return 1; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public DownloadDataV1 fetch(int version, DownloadDataV1 server) {

    if (DKV.get(server.key.key()) == null) throw new IllegalArgumentException(server.key.toString() + " not found.");
    Frame value = server.key.key().get();

    InputStream is = value.toCSV(true, server.hex_string);
    java.util.Scanner scanner = new java.util.Scanner(is).useDelimiter("\\A");
    server.csv = (scanner.hasNext() ? scanner.next() : "");

    // Clean up Key name back to something resembling a file system name.  Hope
    // the user's browser actually asks for what to do with the suggested
    // filename.  Without this code, my FireFox would claim something silly
    // like "no helper app installed", then fail the download.
    String s = server.key.toString();
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
}

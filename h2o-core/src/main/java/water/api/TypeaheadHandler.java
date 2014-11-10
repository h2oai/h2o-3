package water.api;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import water.Iced;
import water.api.TypeaheadHandler.Typeahead;
import water.persist.PersistHdfs;
import water.util.Log;

import java.io.File;
import java.lang.reflect.Type;
import java.util.*;

class TypeaheadHandler extends Handler<Typeahead,TypeaheadV2> {
  @Override protected int min_ver() { return 2; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  protected static final class Typeahead extends Iced {
    // Input
    String _src;
    int _limit;
    // Outputs
    String _matches[];
  }

  // Find files
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public Schema files(int version, Typeahead t) {
    if( t._src.startsWith("hdfs://" ) ) return serveHDFS(version, t);
    //else if( p2.startsWith("s3n://"  ) ) serveHdfs();
    //else if( p2.startsWith("maprfs:/") ) serveHdfs();
    //else if( p2.startsWith("s3://"   ) ) serveS3();
    //else if( p2.startsWith("http://" ) ) serveHttp();
    //else if( p2.startsWith("https://") ) serveHttp();
    // else
    else return serveLocalDisk(version, t);
  }


  private Schema serveLocalDisk(int version, Typeahead t) {
    File base = null;
    String filterPrefix = "";
    if( !t._src.isEmpty() ) {
      File file = new File(t._src);
      if( file.isDirectory() ) {
        base = file;
      } else {
        base = file.getParentFile();
        filterPrefix = file.getName().toLowerCase();
      }
    }
    if( base == null ) base = new File(".");

    ArrayList<String> array = new ArrayList<>();
    File[] files = base.listFiles();
    if( files != null ) {
      for (File file : files) {
        if (file.isHidden()) continue;
        if (file.getName().toLowerCase().startsWith(filterPrefix))
          array.add(file.getPath());
        if (array.size() == t._limit) break;
      }
      t._matches = array.toArray(new String[array.size()]);
    }
    return schema(version).fillFromImpl(t);
  }

  private Schema serveHDFS(int version, Typeahead t) {
    // Get HDFS configuration
    Configuration conf = PersistHdfs.CONF;
    String filter = t._src;
    // Output matches
    ArrayList<String> array = new ArrayList<>();
    try {
      Path p = new Path(filter);
      Path expand = p;
      if( !filter.endsWith("/") ) expand = p.getParent();
      FileSystem fs = FileSystem.get(p.toUri(), conf);
      for( FileStatus file : fs.listStatus(expand) ) {
        Path fp = file.getPath();
        if( fp.toString().startsWith(p.toString()) ) {
          array.add(fp.toString());
        }
        if( array.size() == t._limit) break;
      }
    } catch( Throwable xe ) {
      xe.printStackTrace();
      Log.debug(xe); /* ignore here */
    }
    // Fill resulting pojo
    t._matches = array.toArray(new String[array.size()]);
    // And return schema
    return schema(version).fillFromImpl(t);
  }

  @Override protected TypeaheadV2 schema(int version) { return new TypeaheadV2(); }
  // Running all in GET, no need for backgrounding on F/J threads
  @Override public void compute2() { throw water.H2O.unimpl(); }
}

package water.api;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import water.persist.PersistHdfs;
import water.util.Log;

import java.io.File;
import java.util.ArrayList;

class TypeaheadHandler extends Handler {
  @Override protected int min_ver() { return 2; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  // Find files
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public Schema files(int version, TypeaheadV2 t) {
    if( t.src.startsWith("hdfs://" ) ) return serveHDFS(version, t);
    //else if( p2.startsWith("s3n://"  ) ) serveHdfs();
    //else if( p2.startsWith("maprfs:/") ) serveHdfs();
    //else if( p2.startsWith("s3://"   ) ) serveS3();
    //else if( p2.startsWith("http://" ) ) serveHttp();
    //else if( p2.startsWith("https://") ) serveHttp();
    // else
    else return serveLocalDisk(version, t);
  }


  private Schema serveLocalDisk(int version, TypeaheadV2 t) {
    File base = null;
    String filterPrefix = "";
    if( !t.src.isEmpty() ) {
      File file = new File(t.src);
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
        if (array.size() == t.limit) break;
      }
      t.matches = array.toArray(new String[array.size()]);
    }
    return t;
  }

  private Schema serveHDFS(int version, TypeaheadV2 t) {
    // Get HDFS configuration
    Configuration conf = PersistHdfs.CONF;
    String filter = t.src;
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
        if( array.size() == t.limit) break;
      }
    } catch( Throwable xe ) {
      xe.printStackTrace();
      Log.debug(xe); /* ignore here */
    }
    // Fill resulting pojo
    t.matches = array.toArray(new String[array.size()]);
    // And return schema
    return t;
  }
}

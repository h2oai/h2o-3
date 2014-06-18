package water.api;

import java.io.File;
import java.util.*;

class TypeaheadHandler extends Handler<TypeaheadHandler,TypeaheadV2> {
  @Override protected int min_ver() { return 2; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }
  // Input
  String _src;
  int _limit;
  // Outputs
  String _matches[];

  // Find files
  public void files() {
    if( false ) ;
    //else if( p2.startsWith("hdfs://" ) ) serveHdfs();
    //else if( p2.startsWith("s3n://"  ) ) serveHdfs();
    //else if( p2.startsWith("maprfs:/") ) serveHdfs();
    //else if( p2.startsWith("s3://"   ) ) serveS3();
    //else if( p2.startsWith("http://" ) ) serveHttp();
    //else if( p2.startsWith("https://") ) serveHttp();
    else serveLocalDisk();
  }


  private void serveLocalDisk() {
    File base = null;
    String filterPrefix = "";
    if( !_src.isEmpty() ) {
      File file = new File(_src);
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
    if( files == null ) return;
    for( File file : files ) {
      if( file.isHidden() ) continue;
      if( file.getName().toLowerCase().startsWith(filterPrefix) )
        array.add(file.getPath());
      if( array.size() == _limit) break;
    }
    _matches = array.toArray(new String[array.size()]);
  }

  @Override protected TypeaheadV2 schema(int version) { return new TypeaheadV2(); }
  // Running all in GET, no need for backgrounding on F/J threads
  @Override public void compute2() { throw water.H2O.unimpl(); }
}

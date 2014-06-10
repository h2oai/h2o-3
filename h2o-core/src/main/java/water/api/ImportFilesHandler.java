package water.api;

//import com.amazonaws.services.s3.AmazonS3;
//import com.amazonaws.services.s3.model.ObjectListing;
//import com.amazonaws.services.s3.model.S3ObjectSummary;
//import org.apache.hadoop.fs.Path;
import java.io.File;
import java.util.*;
import water.H2O;
import water.schemas.ImportFilesV2;
import water.util.FileIntegrityChecker;

public class ImportFilesHandler extends Handler<ImportFilesHandler,ImportFilesV2> {
  @Override protected int min_ver() { return 2; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  // Input
  public String _path;

  // Outputs
  public String _files[], _keys[], _fails[], _dels[];

  // Running all in GET, no need for backgrounding on F/J threads
  @Override public void compute2() { 
    assert _path != null;
    String p2 = _path.toLowerCase();
    if( false ) ;
    //else if( p2.startsWith("hdfs://" ) ) serveHdfs();
    //else if( p2.startsWith("s3n://"  ) ) serveHdfs();
    //else if( p2.startsWith("maprfs:/") ) serveHdfs();
    //else if( p2.startsWith("s3://"   ) ) serveS3();
    //else if( p2.startsWith("http://" ) ) serveHttp();
    //else if( p2.startsWith("https://") ) serveHttp();
    else serveLocalDisk();
  }

//  protected void serveHdfs() throws IOException{
//    if (isBareS3NBucketWithoutTrailingSlash(path)) { path += "/"; }
//    Log.info("ImportHDFS processing (" + path + ")");
//    ArrayList<String> succ = new ArrayList<String>();
//    ArrayList<String> fail = new ArrayList<String>();
//    PersistHdfs.addFolder2(new Path(path), succ, fail);
//    keys = succ.toArray(new String[succ.size()]);
//    files = keys;
//    fails = fail.toArray(new String[fail.size()]);
//    DKV.write_barrier();
//  }
//
//
//  protected void serveS3(){
//    Futures fs = new Futures();
//    assert path.startsWith("s3://");
//    path = path.substring(5);
//    int bend = path.indexOf('/');
//    if(bend == -1)bend = path.length();
//    String bucket = path.substring(0,bend);
//    String prefix = bend < path.length()?path.substring(bend+1):"";
//    AmazonS3 s3 = PersistS3.getClient();
//    if( !s3.doesBucketExist(bucket) )
//      throw new IllegalArgumentException("S3 Bucket " + bucket + " not found!");;
//    ArrayList<String> succ = new ArrayList<String>();
//    ArrayList<String> fail = new ArrayList<String>();
//    ObjectListing currentList = s3.listObjects(bucket, prefix);
//    while(true){
//      for(S3ObjectSummary obj:currentList.getObjectSummaries())
//        try {
//          succ.add(S3FileVec.make(obj,fs).toString());
//        } catch( Throwable e ) {
//          fail.add(obj.getKey());
//          Log.err("Failed to loadfile from S3: path = " + obj.getKey() + ", error = " + e.getClass().getName() + ", msg = " + e.getMessage());
//        }
//      if(currentList.isTruncated())
//        currentList = s3.listNextBatchOfObjects(currentList);
//      else
//        break;
//    }
//    keys = succ.toArray(new String[succ.size()]);
//    files = keys;
//    fails = fail.toArray(new String[fail.size()]);
//  }

  private void serveLocalDisk() {
    File f = new File(_path);
    if( !f.exists() ) throw new IllegalArgumentException("File " + _path + " does not exist!");
    ArrayList<String> afiles = new ArrayList();
    ArrayList<String> akeys  = new ArrayList();
    ArrayList<String> afails = new ArrayList();
    ArrayList<String> adels  = new ArrayList();
    FileIntegrityChecker.check(f).syncDirectory(afiles,akeys,afails,adels);
    _files = afiles.toArray(new String[0]);
    _keys  = akeys .toArray(new String[0]);
    _fails = afails.toArray(new String[0]);
    _dels  = adels .toArray(new String[0]);
  }

//  protected void serveHttp() {
//    try {
//      java.net.URL url = new URL(path);
//      Key k = Key.make(path);
//      InputStream is = url.openStream();
//      if( is == null ) {
//        Log.err("Unable to open stream to URL " + path);
//      }
//
//      UploadFileVec.readPut(k, is);
//      fails = new String[0];
//      String[] filesArr = { path };
//      files = filesArr;
//      String[] keysArr = { k.toString() };
//      keys = keysArr;
//    }
//    catch( Throwable e) {
//      String[] arr = { path };
//      fails = arr;
//      files = new String[0];
//      keys = new String[0];
//    }
//  }
//
//  // HTML builder
//  @Override public boolean toHTML( StringBuilder sb ) {
//    if(files == null)return false;
//    if( files != null && files.length > 1 )
//      sb.append("<div class='alert'>")
//        .append(parseLink("*"+path+"*", "Parse all into hex format"))
//        .append(" </div>");
//
//    DocGen.HTML.title(sb,"files");
//    DocGen.HTML.arrayHead(sb);
//    for( int i=0; i<files.length; i++ )
//      sb.append("<tr><td><a href='"+parse()+"?source_key=").append(keys[i]).
//        append("'>").append(files[i]).append("</a></td></tr>");
//    DocGen.HTML.arrayTail(sb);
//
//    if( fails.length > 0 )
//      DocGen.HTML.array(DocGen.HTML.title(sb,"fails"),fails);
//    if( dels != null && dels.length > 0 )
//      DocGen.HTML.array(DocGen.HTML.title(sb,"Keys deleted before importing"),dels);
//    return true;
//  }
//
//  private boolean isBareS3NBucketWithoutTrailingSlash(String s) {
//    Pattern p = Pattern.compile("s3n://[^/]*");
//    Matcher m = p.matcher(s);
//    boolean b = m.matches();
//    return b;
//  }
//  protected String parseLink(String k, String txt) { return Parse2.link(k, txt); }
//  String parse() { return "Parse2.query"; }
//
  @Override protected ImportFilesV2 schema(int version) { return new ImportFilesV2(); }
}


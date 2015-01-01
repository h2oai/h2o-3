package water.api;

//import com.amazonaws.services.s3.AmazonS3;
//import com.amazonaws.services.s3.model.ObjectListing;
//import com.amazonaws.services.s3.model.S3ObjectSummary;
//import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.Path;
import water.exceptions.H2ONotFoundArgumentException;
import water.persist.PersistHdfs;
import water.util.FileIntegrityChecker;
import water.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The handler provides import capabilities.
 *
 * <p>
 *   Currently import from local filesystem, hdfs and s3 is supported.
 * </p>
 */
public class ImportFilesHandler extends Handler {
  @Override protected int min_ver() { return 2; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public ImportFilesV2 importFiles(int version, ImportFilesV2 importFiles) {
    assert importFiles.path != null;
    String path = importFiles.path.toLowerCase();
    if (path.startsWith("hdfs://") )
      return serveHDFS(version, importFiles);
    else if (path.startsWith("s3n://" ))
      return serveHDFS(version, importFiles);
    //else if( p2.startsWith("maprfs:/") ) serveHdfs();
    //else if( p2.startsWith("s3://"   ) ) serveS3();
    //else if( p2.startsWith("http://" ) ) serveHttp();
    //else if( p2.startsWith("https://") ) serveHttp();
    else
      return serveLocalDisk(version, importFiles);
  }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  private ImportFilesV2 serveHDFS(int version, ImportFilesV2 importFiles)  {
    // Fix for S3N kind of URL
    if (isBareS3NBucketWithoutTrailingSlash(importFiles.path)) {
      importFiles.path += "/";
    }
    Log.info("ImportHDFS processing (" + importFiles.path + ")");
    // List of processed files
    ArrayList<String> succ = new ArrayList<String>();
    ArrayList<String> fail = new ArrayList<String>();
    try {
      // Recursively import given file/folder
      PersistHdfs.addFolder(new Path(importFiles.path), succ, fail);
      // Save results into schema holder
      importFiles.keys = succ.toArray(new String[succ.size()]);
      importFiles.files = importFiles.keys;
      importFiles.fails = fail.toArray(new String[fail.size()]);
      // write barrier was here : DKV.write_barrier();
      return importFiles;
    } catch (IOException e) {
      throw new HDFSIOException(importFiles.path, PersistHdfs.CONF.toString(), e);
    }
  }
//
//
//  private void serveS3(){
//    Futures fs = new Futures();
//    assert path.startsWith("s3://");
//    path = path.substring(5);
//    int bend = path.indexOf('/');
//    if(bend == -1)bend = path.length();
//    String bucket = path.substring(0,bend);
//    String prefix = bend < path.length()?path.substring(bend+1):"";
//    AmazonS3 s3 = PersistS3.getClient();
//    if( !s3.doesBucketExist(bucket) )
//      throw new H2ONotFoundException("S3 Bucket " + bucket + " not found!");;
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

  @SuppressWarnings("unused") // called through reflection by RequestServer
  private ImportFilesV2 serveLocalDisk(int version, ImportFilesV2 importFiles) {
    File f = new File(importFiles.path);
    if( !f.exists() ) throw new H2ONotFoundArgumentException("File " + importFiles.path + " does not exist",
                                                             "File " + importFiles.path + " does not exist");
    ArrayList<String> afiles = new ArrayList();
    ArrayList<String> akeys  = new ArrayList();
    ArrayList<String> afails = new ArrayList();
    ArrayList<String> adels  = new ArrayList();
    FileIntegrityChecker.check(f).syncDirectory(afiles,akeys,afails,adels);
    importFiles.files = afiles.toArray(new String[afiles.size()]);
    importFiles.keys  = akeys .toArray(new String[akeys .size()]);
    importFiles.fails = afails.toArray(new String[afails.size()]);
    importFiles.dels  = adels .toArray(new String[adels .size()]);
    return importFiles;
  }

//  private void serveHttp() {
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
//  @Override protected boolean toHTML( StringBuilder sb ) {
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
//  private String parseLink(String k, String txt) { return Parse2.link(k, txt); }
//  String parse() { return "Parse2.query"; }
  private boolean isBareS3NBucketWithoutTrailingSlash(String s) {
    Pattern p = Pattern.compile("s3n://[^/]*");
    Matcher m = p.matcher(s);
    boolean b = m.matches();
    return b;
  }
}


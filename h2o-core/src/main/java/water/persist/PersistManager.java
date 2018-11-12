package water.persist;

import org.joda.time.Period;
import water.*;
import water.api.FSIOException;
import water.api.HDFSIOException;
import water.exceptions.H2OIllegalArgumentException;
import water.parser.BufferedString;
import water.util.FileUtils;
import water.util.Log;
import water.persist.Persist.PersistEntry;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import static water.H2O.OptArgs.SYSTEM_PROP_PREFIX;

/**
 * One true persistence manager which hides the implementations from H2O.
 * In particular, HDFS support or S3 support may or may not exist depending
 * on what is on the classpath.
 */
public class PersistManager {

  public static final int MAX_BACKENDS = 8;

  /** Property which enable HDFS as default fallback persistent layer. For example,
   * if swift fs is regirestered properly under HDFS and user specifies swift based URI, the persist
   * layer forwards the request through HDFS API. */
  private static final String PROP_ENABLE_HDFS_FALLBACK = SYSTEM_PROP_PREFIX + "persist.enable.hdfs.fallback";

  /** Persistence schemes; used as file prefixes eg "hdfs://some_hdfs_path/some_file" */
  public interface Schemes {
    String FILE = "file";
    String HDFS = "hdfs";
    String S3   = "s3";
    String S3N  = "s3n";
    String S3A  = "s3a";
    String GCS  = "gs";
    String NFS  = "nfs";
  }

  public static class PersistStatsEntry {
    public PersistStatsEntry() {
      store_count = new AtomicLong();
      store_bytes = new AtomicLong();
      delete_count = new AtomicLong();
      load_count = new AtomicLong();
      load_bytes = new AtomicLong();
    }

    public AtomicLong store_count;
    public AtomicLong store_bytes;
    public AtomicLong delete_count;
    public AtomicLong load_count;
    public AtomicLong load_bytes;
  }

  private Persist[] I;
  private PersistStatsEntry[] stats;
  public PersistStatsEntry[] getStats() { return stats; }

  public boolean isHdfsPath(String path) {
    String s = path.toLowerCase();
    if (s.startsWith("hdfs:")
        || s.startsWith("s3:")
        || s.startsWith("s3n:")
        || s.startsWith("s3a:")
        || s.startsWith("maprfs:")
        || useHdfsAsFallback() && I[Value.HDFS] != null && I[Value.HDFS].canHandle(path)) {
      return true;
    }
    return false;
  }

  private void validateHdfsConfigured() {
    if (I[Value.HDFS] == null) {
      throw new H2OIllegalArgumentException("HDFS, S3, S3N, and S3A support is not configured");
    }
  }

  public boolean isGcsPath(String path) {
    return path.toLowerCase().startsWith("gs://");
  }

  public PersistManager(URI iceRoot) {
    I = new Persist[MAX_BACKENDS];
    stats = new PersistStatsEntry[MAX_BACKENDS];
    for (int i = 0; i < stats.length; i++) {
      stats[i] = new PersistStatsEntry();
    }

    if (iceRoot == null) {
      Log.err("ice_root must be specified.  Exiting.");
      H2O.exit(1);
    }

    Persist ice = null;
    boolean windowsPath = iceRoot.toString().matches("^[a-zA-Z]:.*");

    if (windowsPath) {
      ice = new PersistFS(new File(iceRoot.toString()));
    }
    else if ((iceRoot.getScheme() == null) || Schemes.FILE.equals(iceRoot.getScheme())) {
      ice = new PersistFS(new File(iceRoot.getPath()));
    }
    else if( Schemes.HDFS.equals(iceRoot.getScheme()) ) {
      Log.err("HDFS ice_root not yet supported.  Exiting.");
      H2O.exit(1);

// I am not sure anyone actually ever does this.
// H2O on Hadoop launches use local disk for ice root.
// This has a chance to work, but turn if off until it gets tested.
//
//      try {
//        Class klass = Class.forName("water.persist.PersistHdfs");
//        java.lang.reflect.Constructor constructor = klass.getConstructor(new Class[]{URI.class});
//        ice = (Persist) constructor.newInstance(iceRoot);
//      } catch (Exception e) {
//        Log.err("Could not initialize HDFS");
//        throw new RuntimeException(e);
//      }
    }

    I[Value.ICE] = ice;
    I[Value.NFS] = new PersistNFS();

    try {
      Class klass = Class.forName("water.persist.PersistHTTP");
      java.lang.reflect.Constructor constructor = klass.getConstructor();
      I[Value.HTTP] = (Persist) constructor.newInstance();
      Log.info("Subsystem for distributed import from HTTP/HTTPS successfully initialized");
    }
    catch (Throwable ignore) {
      I[Value.HTTP] = new PersistEagerHTTP();
      Log.info("Distributed HTTP import not available (import from HTTP/HTTPS will be eager)");
    }

    try {
      Class klass = Class.forName("water.persist.PersistHdfs");
      java.lang.reflect.Constructor constructor = klass.getConstructor();
      I[Value.HDFS] = (Persist) constructor.newInstance();
      Log.info("HDFS subsystem successfully initialized");
    }
    catch (Throwable ignore) {
      Log.info("HDFS subsystem not available");
    }

    try {
      Class klass = Class.forName("water.persist.PersistS3");
      java.lang.reflect.Constructor constructor = klass.getConstructor();
      I[Value.S3] = (Persist) constructor.newInstance();
      Log.info("S3 subsystem successfully initialized");
    } catch (Throwable ignore) {
      Log.info("S3 subsystem not available");
    }

    try {
      Class klass = Class.forName("water.persist.PersistGcs");
      java.lang.reflect.Constructor constructor = klass.getConstructor();
      I[Value.GCS] = (Persist) constructor.newInstance();
      Log.info("GCS subsystem successfully initialized");
    } catch (Throwable ignore) {
      Log.info("GCS subsystem not available");
    }
  }

  public void store(int backend, Value v) throws IOException {
    stats[backend].store_count.incrementAndGet();
    I[backend].store(v);
  }

  public void delete(int backend, Value v) {
    stats[backend].delete_count.incrementAndGet();
    I[backend].delete(v);
  }

  public byte[] load(int backend, Value v) throws IOException {
    stats[backend].load_count.incrementAndGet();
    byte[] arr = I[backend].load(v);
    stats[backend].load_bytes.addAndGet(arr.length);
    return arr;
  }

  /** Get the current Persist flavor for user-mode swapping. */
  public Persist getIce() { return I[Value.ICE]; }

  /** Convert given URI into a specific H2O key representation.
   *
   * The representation depends on persistent backend, since it will
   * deduce file location from the key content.
   *
   * The method will look at scheme of URI and based on it, it will
   * ask a backend to provide a conversion to a key (i.e., URI with scheme
   * 'hdfs' will be forwared to HDFS backend).
   *
   * @param uri file location
   * @return a key encoding URI
   * @throws IOException in the case of uri conversion problem
   * @throws water.exceptions.H2OIllegalArgumentException in case of unsupported scheme
   */
  public final Key anyURIToKey(URI uri) throws IOException {
    Key ikey;
    String scheme = uri.getScheme();
    if ("s3".equals(scheme)) {
      ikey = I[Value.S3].uriToKey(uri);
    } else if ("hdfs".equals(scheme)) {
      ikey = I[Value.HDFS].uriToKey(uri);
    } else if ("s3".equals(scheme) || "s3n".equals(scheme) || "s3a".equals(scheme)) {
      ikey = I[Value.HDFS].uriToKey(uri);
    } else if ("gs".equals(scheme)) {
      ikey = I[Value.GCS].uriToKey(uri);
    } else if ("file".equals(scheme) || scheme == null) {
      ikey = I[Value.NFS].uriToKey(uri);
    } else if (useHdfsAsFallback() && I[Value.HDFS].canHandle(uri.toString())) {
      ikey = I[Value.HDFS].uriToKey(uri);
    } else {
      throw new H2OIllegalArgumentException("Unsupported schema '" + scheme + "' for given uri " + uri);
    }
    return ikey;
  }

  private static boolean httpUrlExists(String URLName){
    try {
      HttpURLConnection con = (HttpURLConnection) new URL(URLName).openConnection();
      con.setInstanceFollowRedirects(false);
      con.setRequestMethod("HEAD");
      return (con.getResponseCode() == HttpURLConnection.HTTP_OK);
    }
    catch (Exception e) {
      return false;
    }
  }

  /**
   * Calculate typeahead matches for src
   *
   * @param filter Source string to match for typeahead
   * @param limit Max number of entries to return
   * @return List of matches
   */
  public List<String> calcTypeaheadMatches(String filter, int limit) {
    String s = filter.toLowerCase();
    if (s.startsWith("http:") || s.startsWith("https:")) {
      if (httpUrlExists(filter)) {
        ArrayList<String> arrayList = new ArrayList<>();
        arrayList.add(filter);
        return arrayList;
      }
      else {
        return new ArrayList<>();
      }
    } else if(s.startsWith("s3://")) {
      return I[Value.S3].calcTypeaheadMatches(filter, limit);
    } else if(s.startsWith("gs://")) {
      return I[Value.GCS].calcTypeaheadMatches(filter, limit);
    } else if (s.startsWith("hdfs:")
               || s.startsWith("s3n:")
               || s.startsWith("s3a:")
               || s.startsWith("maprfs:")
               || useHdfsAsFallback() && I[Value.HDFS] != null && I[Value.HDFS].canHandle(s)) {
      if (I[Value.HDFS] == null) {
        throw new H2OIllegalArgumentException("HDFS, S3, S3N, and S3A support is not configured");
      }

      return I[Value.HDFS].calcTypeaheadMatches(filter, limit);
    }

    return I[Value.NFS].calcTypeaheadMatches(filter, limit);
  }

  public void importFiles(String[] paths, String pattern, ArrayList<String> files, ArrayList<String> keys, ArrayList<String> fails, ArrayList<String> dels) {
    if (paths.length == 1) {
      importFiles(paths[0], pattern, files, keys, fails, dels);
      return;
    }

    ImportFilesTask importFilesTask = new ImportFilesTask(paths, pattern);
    H2O.submitTask(new LocalMR(importFilesTask, paths.length)).join();

    ImportFilesTask.addAllTo(importFilesTask._pFiles, files);
    ImportFilesTask.addAllTo(importFilesTask._pKeys, keys);
    ImportFilesTask.addAllTo(importFilesTask._pFails, fails);
    ImportFilesTask.addAllTo(importFilesTask._pDels, dels);
  }

  private static class ImportFilesTask extends MrFun<ImportFilesTask> {

    private final String[] _paths;
    private final String _pattern;

    BufferedString[][] _pFiles;
    BufferedString[][] _pKeys;
    BufferedString[][] _pFails;
    BufferedString[][] _pDels;

    public ImportFilesTask(String[] paths, String pattern) {
      _paths = paths;
      _pattern = pattern;
      _pFiles = new BufferedString[paths.length][];
      _pKeys = new BufferedString[paths.length][];
      _pFails = new BufferedString[paths.length][];
      _pDels = new BufferedString[paths.length][];
    }

    @Override
    protected void map(int t) {
      ArrayList<String> pFiles = new ArrayList<>();
      ArrayList<String> pKeys = new ArrayList<>();
      ArrayList<String> pFails = new ArrayList<>();
      ArrayList<String> pDels = new ArrayList<>();

      H2O.getPM().importFiles(_paths[t], _pattern, pFiles, pKeys, pFails, pDels);

      _pFiles[t] = toArray(pFiles);
      _pKeys[t] = toArray(pKeys);
      _pFails[t] = toArray(pFails);
      _pDels[t] = toArray(pDels);
    }

    private static BufferedString[] toArray(List<String> ls) {
      BufferedString[] bss = new BufferedString[ls.size()];
      int i = 0;
      for (String s : ls) {
        bss[i++] = new BufferedString(s);
      }
      return bss;
    }

    private static void addAllTo(BufferedString[][] bssAry, ArrayList<String> target) {
      for (BufferedString[] bss : bssAry) {
        for (BufferedString bs : bss)
          target.add(bs.toString());
      }
    }

  }

  /**
   * From a path produce a list of files and keys for parsing.
   *
   * Use as follows:
   *
   * ArrayList<String> files = new ArrayList();
   * ArrayList<String> keys = new ArrayList();
   * ArrayList<String> fails = new ArrayList();
   * ArrayList<String> dels = new ArrayList();
   * importFiles(importFiles.path, files, keys, fails, dels);
   *
   * @param path  (Input) Path to import data from
   * @param pattern (Input) Regex pattern to match files by
   * @param files (Output) List of files found
   * @param keys  (Output) List of keys corresponding to files
   * @param fails (Output) List of failed files which mismatch among nodes
   * @param dels  (Output) I don't know what this is
   */
  public void importFiles(String path, String pattern, ArrayList<String> files, ArrayList<String> keys, ArrayList<String> fails, ArrayList<String> dels) {
    URI uri = FileUtils.getURI(path);
    String scheme = uri.getScheme();
    if (scheme == null || "file".equals(scheme)) {
      I[Value.NFS].importFiles(path, pattern, files, keys, fails, dels);
    } else if ("http".equals(scheme) || "https".equals(scheme)) {
      I[Value.HTTP].importFiles(path, pattern, files, keys, fails, dels);
    } else if ("s3".equals(scheme)) {
      if (I[Value.S3] == null) throw new H2OIllegalArgumentException("S3 support is not configured");
      I[Value.S3].importFiles(path, pattern, files, keys, fails, dels);
    } else if ("gs".equals(scheme)) {
      if (I[Value.GCS] == null) throw new H2OIllegalArgumentException("GCS support is not configured");
      I[Value.GCS].importFiles(path, pattern, files, keys, fails, dels);
    } else if ("hdfs".equals(scheme) ||
        "s3n:".equals(scheme) ||
        "s3a:".equals(scheme) ||
        "maprfs:".equals(scheme) ||
        (useHdfsAsFallback() && I[Value.HDFS] != null && I[Value.HDFS].canHandle(path))) {
      if (I[Value.HDFS] == null) throw new H2OIllegalArgumentException("HDFS, S3N, and S3A support is not configured");
      I[Value.HDFS].importFiles(path, pattern, files, keys, fails, dels);
    }

    if(pattern != null && !pattern.isEmpty()) {
      files.retainAll(matchPattern(path,files,pattern)); //New files ArrayList after matching pattern of choice
      keys.retainAll(matchPattern(path,keys,pattern)); //New keys ArrayList after matching pattern of choice
      //New fails ArrayList after matching pattern of choice. Only show failures that match pattern
      if(!fails.isEmpty()) {
        fails.retainAll(matchPattern(path, fails, pattern));
      }
    }

  }


  // -------------------------------
  // Node Persistent Storage helpers
  // -------------------------------

  // Reads

  public String getHdfsHomeDirectory() {
    if (I[Value.HDFS] == null) {
      return null;
    }

    return I[Value.HDFS].getHomeDirectory();
  }

  public PersistEntry[] list(String path) {
    if (isHdfsPath(path)) {
      validateHdfsConfigured();
      PersistEntry[] arr = I[Value.HDFS].list(path);
      return arr;
    } else if (isGcsPath(path)) {
      return I[Value.GCS].list(path);
    }

    File dir = new File(path);
    File[] files = dir.listFiles();
    if (files == null) {
      return new PersistEntry[0];
    }

    ArrayList<PersistEntry> arr = new ArrayList<>();
    for (File f : files) {
      PersistEntry entry = new PersistEntry(f.getName(), f.length(), f.lastModified());
      arr.add(entry);
    }

    return arr.toArray(new PersistEntry[arr.size()]);
  }

  public boolean exists(String path) {
    if (isHdfsPath(path)) {
      validateHdfsConfigured();
      boolean b = I[Value.HDFS].exists(path);
      return b;
    } else if (isGcsPath(path)) {
      return I[Value.GCS].exists(path);
    }

    File f = new File(path);
    return f.exists();
  }

  public boolean isDirectory(String path) {
    if (isHdfsPath(path)) {
      validateHdfsConfigured();
      boolean b = I[Value.HDFS].isDirectory(path);
      return b;
    }

    File f = new File(path);
    return f.isDirectory();
  }

  /**
   * Checks whether a given path is either an empty directory or it doesn't yet exist.
   * This is trivial if the filesystem where the path leads is distributed.
   * If we are working with a local filesystem we need to make sure that this property
   * is satisfied on all of the nodes.
   * @param path path we want to check
   * @return true the path is an empty or non-existent directory everywhere, false otherwise
   */
  public boolean isEmptyDirectoryAllNodes(String path) {
    if (isHdfsPath(path)) {
      validateHdfsConfigured();
      if (! I[Value.HDFS].exists(path)) return true;
      if (! I[Value.HDFS].isDirectory(path)) return false;
      PersistEntry[] content = I[Value.HDFS].list(path);
      return (content == null) || (content.length == 0);
    }

    return new CheckLocalDirTask(path).doAllNodes()._result;
  }

  /**
   * Check whether given path represents a writable directory. If such path does not exist
   * it will try to create the directory and if successful it is safe to assume that such
   * directory is writable.
   * @param path Path to check if a writable directory
   * @return true if given path is a writable directory, false otherwise
   */
  public boolean isWritableDirectory(String path) {
    URI pathAsUri = FileUtils.getURI(path);
    Persist persist = getPersistForURI(pathAsUri);
    String pathUriStr = pathAsUri.toString();
    if (persist.isDirectory(pathUriStr)) {
      return isDirectoryWritable(persist, path);
    } else if (persist.exists(pathUriStr)) {
      // exists but its not a directory
      return false;
    } else {
      String existingParent = getExistingParent(persist, pathUriStr);
      if (existingParent != null) {
        return isDirectoryWritable(persist, existingParent);
      } else {
        return false;
      }
    }
  }

  /*
  Check that a directory is writable by creating and deleting a file.
   */
  private boolean isDirectoryWritable(Persist persist, String path) {
    OutputStream os = null;
    try {
      String testFileUriStr = FileUtils.getURI(path + "/.h2oWriteCheck").toString();
      os = persist.create(testFileUriStr, true);
      os.close();
      persist.delete(testFileUriStr);
      return true;
    } catch (IOException | HDFSIOException | FSIOException e) {
      return false;
    } finally {
      FileUtils.close(os);
    }
  }

  private String getExistingParent(Persist persist, String path) {
    String parent = persist.getParent(path);
    if (parent == null) {
      return null;
    } else if (persist.exists(parent)) {
      return parent;
    } else {
      return getExistingParent(persist, parent);
    }
  }

  private static class CheckLocalDirTask extends MRTask<CheckLocalDirTask> {
    String _path;
    // OUT
    boolean _result;

    CheckLocalDirTask(String _path) { this._path = _path; }

    @Override public void reduce(CheckLocalDirTask mrt) {
      _result = _result && mrt._result;
    }
    @Override protected void setupLocal() {
      File f = new File(_path);
      if (! f.exists())
        _result = true;
      else if (f.isDirectory()) {
        File[] content = f.listFiles();
        _result = (content != null) && (content.length == 0);
      } else
        _result = false;
    }
  }

  public long length(String path) {
    if (isHdfsPath(path)) {
      validateHdfsConfigured();
      long l = I[Value.HDFS].length(path);
      return l;
    } else if (isGcsPath(path)) {
      return I[Value.GCS].length(path);
    }

    File f = new File(path);
    if (! f.exists()) {
      throw new IllegalArgumentException("File not found (" + path + ")");
    }

    return f.length();
  }

  public InputStream open(String path) {
    if (isHdfsPath(path)) {
      validateHdfsConfigured();
      InputStream os = I[Value.HDFS].open(path);
      return os;
    } else if (isGcsPath(path)) {
      return I[Value.GCS].open(path);
    }

    try {
      File f = new File(path);
      return new FileInputStream(f);
    }
    catch (FileNotFoundException e) {
      throw new IllegalArgumentException("File not found (" + path + ")");
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  // Writes

  public boolean mkdirs(String path) {
    if (isHdfsPath(path)) {
      validateHdfsConfigured();
      boolean b = I[Value.HDFS].mkdirs(path);
      return b;
    } else if (isGcsPath(path)){
      return I[Value.GCS].mkdirs(path);
    }

    File f = new File(path);
    boolean b = f.mkdirs();
    return b;
  }

  public boolean rename(String fromPath, String toPath) {
    if (isHdfsPath(fromPath) || isHdfsPath(toPath)) {
      validateHdfsConfigured();
      boolean b = I[Value.HDFS].rename(fromPath, toPath);
      return b;
    }
    if (isGcsPath(fromPath) || isGcsPath(toPath)) {
      return I[Value.GCS].rename(fromPath, toPath);
    }

    File f = new File(fromPath);
    File t = new File(toPath);
    boolean b = f.renameTo(t);
    return b;
  }

  public OutputStream create(String path, boolean overwrite) {
    if (isHdfsPath(path)) {
      validateHdfsConfigured();
      return I[Value.HDFS].create(path, overwrite);
    } else if (isGcsPath(path)) {
      return I[Value.GCS].create(path, overwrite);
    }

    try {
      if (! overwrite) {
        File f = new File(path);
        if (f.exists()) {
          throw new IllegalArgumentException("File already exists (" + path + ")");
        }
      }
      return new FileOutputStream(path);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public boolean delete(String path) {
    if (isHdfsPath(path)) {
      validateHdfsConfigured();
      boolean b = I[Value.HDFS].delete(path);
      return b;
    } else if (isGcsPath(path)) {
      return I[Value.GCS].delete(path);
    }

    File f = new File(path);
    boolean b = f.delete();
    return b;
  }

  public Persist getPersistForURI(URI uri) {
    String scheme = uri.getScheme();
    boolean windowsPath = scheme.matches("^[a-zA-Z]$");
    if (windowsPath) {
      return I[Value.ICE];
    }

    if (scheme != null) {
      switch (scheme) {
        case Schemes.FILE:
          return I[Value.ICE]; // Local FS
        case Schemes.HDFS:
        case Schemes.S3N:
        case Schemes.S3A:
          return I[Value.HDFS];
        case Schemes.S3:
          return I[Value.S3];
        case Schemes.GCS:
          return I[Value.GCS];
        default:
          if (useHdfsAsFallback() && I[Value.HDFS] != null && I[Value.HDFS].canHandle(uri.toString())) {
            return I[Value.HDFS];
          } else {
            throw new IllegalArgumentException("Cannot find persist manager for scheme " + scheme);
          }
      }
    } else {
      return I[Value.ICE];
    }
  }

  /**
   * Finds all entries in the list that matches the regex
   * @param prefix The substring to extract before pattern matching
   * @param fileList The list of strings to check
   * @param matchStr The regular expression to use on the string after prefix
   * @return list containing the matching entries
   */
  public ArrayList<String> matchPattern(String prefix, ArrayList<String> fileList, String matchStr){
    ArrayList<String> result = new ArrayList<String>();
    Pattern pattern = Pattern.compile(matchStr);
    if (matchStr != null) {
      for(String s : fileList){
        Matcher matcher = pattern.matcher(afterPrefix(s,prefix));
        if (matcher.find()) {
          result.add(s);
        }
      }
    }
    return result;
  }

  /**
   * Returns the part of the string that occurs after the first index of the substring
   * @param wholeString A string that needs to be subsetted
   * @param substring The substring to extract
   * @return string after substring
   */
  private static String afterPrefix(String wholeString , String substring) {
    // Returns a substring containing all characters after a string.
    int posSubstring = wholeString.lastIndexOf(substring);
    if (posSubstring == -1) {
      return "";
    }
    int adjustedPosSubstring = posSubstring + substring.length();
    if (adjustedPosSubstring >= wholeString.length()) {
      return "";
    }
    return wholeString.substring(adjustedPosSubstring);
  }

  /** Should HDFS persist layer be used as default persist layer
   * for unknown URL schema.
    * @return  true if HDFS should handle unknown URL schema.
   */
  static boolean useHdfsAsFallback() {
    return System.getProperty(PROP_ENABLE_HDFS_FALLBACK, "true").equals("true");
  }
}

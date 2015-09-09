package water.serial;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import water.H2O;
import water.Key;
import water.Keyed;
import water.persist.Persist;
import water.util.FileUtils;
import water.util.Log;

/**
 * Object tree serializer.
 *
 * It dumps a given list of keys (representing object graph in breadth-first order)
 * into a folder referenced by an URI.
 * It creates a metafile holding list of files which express load order
 * of given files and their deserialization.
 *
 * If save destination exists, the save will fail.
 * If load source does not or is not readable, the load will fail.
 * If list of keys to save contains null-Key, then loaded list will contain null-key as well.
 *
 * The caller is responsible for cleanup of destination directory in case of failure.
 *
 */
public class ObjectTreeBinarySerializer implements Serializer<List<Key>, URI> {
  /** Name of metafile containing information about exported object tree. */
  public static final String METAFILE = "__h2o_bin.mbin";
  /** Extension string used for exported objects */
  public static final String EXTENSION = ".bin";
  /** Null marker */
  public static final String NULL_PLACEHOLDER = "<NULL>";

  /** Do DKV after load put on loaded object if it has defined key. */
  final boolean dkvPutAfterLoad;
  /** Do DKV put after load even the object already exists in DKV */
  final boolean overrideInDkv;
  /* During save override the destination file. */
  final boolean overrideFile;

  public ObjectTreeBinarySerializer() {
    this(true, true, true);
  }
  public ObjectTreeBinarySerializer(boolean overrideFile) {
    this(true, true, overrideFile);
  }
  public ObjectTreeBinarySerializer(boolean dkvPutAfterLoad, boolean overrideInDkv, boolean overrideFile) {
    this.dkvPutAfterLoad = dkvPutAfterLoad;
    this.overrideInDkv = overrideInDkv;
    this.overrideFile = overrideFile;
  }

  @Override
  public void save(List<Key> objectTree, URI outputDir) throws IOException {
    assert outputDir.getQuery() == null : "Query parameters are not allowed in URI.";
    // Get persist manager for given output URI
    Persist persist = H2O.getPM().getPersistForURI(outputDir);
    // Create the destination folder
    if (!persist.mkdirs(outputDir.toString())) {
      if (overrideFile) {
        Log.warn("Directory " + outputDir + " already exists.");
      } else {
        throw new IllegalArgumentException("Directory " + outputDir + " already exists but "
                                           + "the flag for force overwrite is `false`.");
      }
    }
    // Step-by-step saves all files into folder
    List<String> savedFilenames = new ArrayList<>(objectTree.size());
    BinarySerializer<Keyed, URI> serial = getKeyedSerializer();
    // Serialize full object tree
    // FIXME: this should be in future distributed operation saving results to distributed FS in parallel.
    for(Key k : objectTree) {
      if (k != null) {
        String filename = FileUtils.keyToFileName(k) + EXTENSION;
        URI fileUri = URI.create(outputDir + "/" + filename); // We have URI
        // NOTE: this will fetch remote object to the caller node!
        serial.save(k.get(), fileUri);
        savedFilenames.add(filename);
      } else {
        savedFilenames.add(NULL_PLACEHOLDER);
      }
    }
    // last step dump a metafile with saved keys
    dumpMetaFile(persist, savedFilenames, outputDir);
  }

  @Override
  public List<Key> load(List<Key> l, URI inputDir) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<Key> load(URI inputDir) throws IOException {
    // Get persist manager for given output URI
    Persist persist = H2O.getPM().getPersistForURI(inputDir);
    // Look for metafile and load it, it returns list of filenames in the load order
    List<String> filenames = loadMetaFile(persist, inputDir);
    List<Key> objectTree = new ArrayList<>(filenames.size());
    // Load given object (side effect of load is save into DKV)
    BinarySerializer<Keyed, URI> serial = getKeyedSerializer();
    for (String filename : filenames) {
      if (!NULL_PLACEHOLDER.equals(filename)) {
        URI fileUri = URI.create(inputDir + "/" + filename);
        Keyed ok = serial.load(fileUri);
        objectTree.add(ok._key);
      } else {
        objectTree.add(null);
      }
    }
    Collections.reverse(objectTree);
    return objectTree;
  }

  /** Dump a metafile with a list of save files. */
  protected void dumpMetaFile(Persist persist, List<String> filenames, URI outputDir) throws IOException {
    URI metafileUri = URI.create(outputDir + "/" + METAFILE);
    PrintStream os = new PrintStream(persist.create(metafileUri.toString(), true));
    Collections.reverse(filenames);
    try {
      for (String fname : filenames)
        os.println(fname);
    } finally {
      FileUtils.close(os);
    }
  }

  protected List<String> loadMetaFile(Persist persist, URI inputDir) throws IOException {
    URI metafileUri = URI.create(inputDir + "/" + METAFILE);
    BufferedReader reader = new BufferedReader(new InputStreamReader(persist.open(metafileUri.toString())));
    List<String> filenames = new LinkedList<>();
    try {
      String fname = null;
      while ((fname = reader.readLine()) != null) {
        filenames.add(fname);
      }
    } finally {
      FileUtils.close(reader);
    }
    return filenames;
  }

  protected BinarySerializer<Keyed, URI> getKeyedSerializer() {
    return new KeyedBinarySerializer(dkvPutAfterLoad, overrideInDkv, overrideFile);
  }
}

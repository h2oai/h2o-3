package hex.tree.xgboost.util;

import java.io.IOException;
import java.util.LinkedList;

import ai.h2o.xgboost4j.java.INativeLibLoader;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import static hex.tree.xgboost.util.NativeLibrary.CompilationFlags.WITH_GPU;
import static hex.tree.xgboost.util.NativeLibrary.CompilationFlags.WITH_OMP;
import static hex.tree.xgboost.util.NativeLibrary.EMPTY_COMPILATION_FLAGS;
import static hex.tree.xgboost.util.NativeLibrary.nativeLibrary;

/**
 * A simple loader which tries to load all
 * specified libraries in a given order.
 */
public class NativeLibraryLoaderChain implements INativeLibLoader {

  private static final Log logger = LogFactory.getLog(NativeLibraryLoaderChain.class);

  private final NativeLibrary[] nativeLibs;

  private NativeLibrary successfullyLoaded = null;

  @SuppressWarnings("unused")
  public NativeLibraryLoaderChain() {
    this(
      // GPU & OpenMP support enabled - backend will be decided at runtime based on availability
      nativeLibrary("xgboost4j_gpu", new NativeLibrary.CompilationFlags[] {WITH_GPU, WITH_OMP}),
      // Minimum version of library - no gpu, no omp
      nativeLibrary("xgboost4j_minimal", EMPTY_COMPILATION_FLAGS)
    );
  }

  private NativeLibraryLoaderChain(NativeLibrary... libs) {
    assert libs != null : "Argument `libs` cannot be null.";
    nativeLibs = libs;
  }

  public NativeLibrary[] getNativeLibs() {
    return nativeLibs;
  }
  
  @Override
  public void loadNativeLibs() throws IOException {
    LinkedList<IOException> exs = new LinkedList<>();
    for (NativeLibrary lib : nativeLibs) {
      try {
        // Try to load
        if (lib.load()) {
          // It was successful load, so remember it
          successfullyLoaded = lib;
          break;
        }
      } catch (IOException e) {
        logger.info("Cannot load library: " + lib.toString());
        exs.add(e);
      }
    }
    if ((successfullyLoaded == null) && (! exs.isEmpty())) {
      throw new IOException(exs.getLast());
    }
  }

  @Override
  public String name() {
    return "NativeLibraryLoaderChain";
  }

  @Override
  public int priority() {
    return 1;
  }

  public NativeLibrary getLoadedLibrary() throws IOException {
    if (successfullyLoaded != null) {
      return successfullyLoaded;
    } else {
      throw new IOException("No binary library found!");
    }
  }

}

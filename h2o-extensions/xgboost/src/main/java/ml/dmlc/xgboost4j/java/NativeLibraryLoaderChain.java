/*
 Copyright (c) 2014 by Contributors

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */
package ml.dmlc.xgboost4j.java;

import java.io.IOException;
import java.util.LinkedList;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import static ml.dmlc.xgboost4j.java.NativeLibrary.CompilationFlags.WITH_GPU;
import static ml.dmlc.xgboost4j.java.NativeLibrary.CompilationFlags.WITH_OMP;
import static ml.dmlc.xgboost4j.java.NativeLibrary.EMPTY_COMPILATION_FLAGS;
import static ml.dmlc.xgboost4j.java.NativeLibrary.nativeLibrary;

/**
 * A simple loader which tries to load all
 * specified libraries in a given order.
 */
public class NativeLibraryLoaderChain implements INativeLibLoader {

  private static final Log logger = LogFactory.getLog(NativeLibraryLoaderChain.class);

  private final Loadable[] nativeLibs;

  private Loadable successfullyLoaded = null;

  @SuppressWarnings("unused")
  public NativeLibraryLoaderChain() {
    this(
      // GPU support enabled
      nativeLibrary("xgboost4j_gpu", new NativeLibrary.CompilationFlags[] {WITH_GPU, WITH_OMP}),
      // OMP support enabled
      nativeLibrary("xgboost4j_omp", new NativeLibrary.CompilationFlags[] {WITH_OMP}),
      // Minimum version of library - no gpu, no omp
      nativeLibrary("xgboost4j_minimal", EMPTY_COMPILATION_FLAGS)
    );
  }

  private NativeLibraryLoaderChain(Loadable... libs) {
    assert libs != null : "Argument `libs` cannot be null.";
    nativeLibs = libs;
  }

  @Override
  public void loadNativeLibs() throws IOException {
    LinkedList<IOException> exs = new LinkedList<>();
    for (Loadable lib : nativeLibs) {
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

  /**
   * Full name of loaded library.
   * @return  library name (e.g., 'xgboost4j_gpu`), never returns null.
   * @throws IOException  if no library was found or library loading fails
   */
  public String getLoadedLibraryName() throws IOException {
    if (successfullyLoaded != null) {
      return successfullyLoaded.getName();
    } else {
      throw new IOException("No binary library found!");
    }
  }

  /** Return compilation flags for loaded library or throws exception if no library was loaded
   *
   * @return  compilation flags of loaded XGBoost library
   * @throws IOException  if no XGBoost library was found
   */
  public NativeLibrary.CompilationFlags[] getLoadedLibraryCompilationFlags() throws IOException {
    if (successfullyLoaded != null) {
      return ((NativeLibrary) successfullyLoaded).getCompilationFlags();
    } else {
      throw new IOException("No binary library found!");
    }
  }


}
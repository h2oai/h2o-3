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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Representation of native library.
 */
public class NativeLibrary {

  private static final Log logger = LogFactory.getLog(NativeLibrary.class);

  public static CompilationFlags[] EMPTY_COMPILATION_FLAGS = new CompilationFlags[0];

  /* Library compilation flags */
  public enum CompilationFlags {
    WITH_GPU, WITH_OMP
  }

  private final String name;
  private final ClassLoader classLoader;
  private final Platform platform;
  private final CompilationFlags[] flags;

  // Is this library loaded already
  private boolean loaded = false;

  static NativeLibrary nativeLibrary(String name, CompilationFlags[] flags) {
    return new NativeLibrary(name, flags);
  }

  static NativeLibrary nativeLibrary(String name, CompilationFlags[] flags,
                                     ClassLoader classLoader) {
    return new NativeLibrary(name, flags, classLoader);
  }

  private NativeLibrary(String name, CompilationFlags[] flags) {
    this(name, flags, NativeLibrary.class.getClassLoader());
  }

  private NativeLibrary(String name, CompilationFlags[] flags, ClassLoader classLoader) {
    this.name = name;
    this.classLoader = classLoader;
    this.platform = Platform.geOSType();
    this.flags = flags;
  }

  synchronized boolean load() throws IOException {
    if (!loaded) {
      doLoad();
      loaded = true;
    }
    return loaded;
  }

  /**
   * Load order:
   *
   */
  private void doLoad() throws IOException {
    final String libName = getName();
    try {
      System.loadLibrary(libName);
    } catch (UnsatisfiedLinkError e) {
      try {
        extractAndLoad(getPlatformLibraryPath(), getSimpleLibraryPath());
      } catch (IOException ioe) {
        logger.warn("Failed to load library from both native path and jar!");
        throw ioe;
      }
    }
  }

  private String getPlatformLibraryPath() {
    return String.format("%s/%s/%s", getResourcePrefix(),
                         platform.getPlatform(),
                         platform.getPlatformLibName(getName()));
  }

  private String getSimpleLibraryPath() {
    return String.format("%s/%s", getResourcePrefix(),
                         platform.getPlatformLibName(getName()));
  }

  private String getResourcePrefix() {
    return "lib";
  }

  private ClassLoader getClassLoader() {
    return classLoader;
  }

  private void extractAndLoad(String ...libPaths) throws IOException {
    Throwable lastException = null;
    for (String libPath : libPaths) {
      try {
        lastException = null;
        File temp = extract(libPath, getClassLoader());
        // Finally, load the library
        System.load(temp.getAbsolutePath());
        // Perfect loaded, break the cycle
        logger.info("Loaded library from " + libPath + " (" + temp.getAbsolutePath() + ")");
        break;
      } catch (IOException | UnsatisfiedLinkError e) {
        logger.warn("Cannot load library from path " + libPath);
        lastException = e;
      }
    }
    if (lastException != null) throw new IOException(lastException);
  }

  private static File extract(String libPath, ClassLoader classLoader)
      throws IOException, IllegalArgumentException {

    // Split filename to prefix and suffix (extension)
    String filename = libPath.substring(libPath.lastIndexOf('/') + 1);
    int lastDotIdx = filename.lastIndexOf('.');
    String prefix = "";
    String suffix = null;
    if (lastDotIdx >= 0 && lastDotIdx < filename.length() - 1) {
      prefix = filename.substring(0, lastDotIdx);
      suffix = filename.substring(lastDotIdx);
    }

    // Prepare temporary file
    File temp = File.createTempFile(prefix, suffix);
    temp.deleteOnExit();

    // Open and check input stream
    InputStream is = classLoader.getResourceAsStream(libPath);
    if (is == null) {
      throw new FileNotFoundException("File " + libPath + " was not found inside JAR.");
    }

    // Open output stream and copy data between source file in JAR and the temporary file
    try {
      Files.copy(is, temp.toPath(), StandardCopyOption.REPLACE_EXISTING);
    } finally {
      is.close();
    }

    return temp;
  }

  public String getName() {
    return name;
  }

  public boolean hasCompilationFlag(CompilationFlags flag) {
    for (CompilationFlags f : getCompilationFlags()) {
      if (flag == f) return true;
    }
    return false;
  }

  public CompilationFlags[] getCompilationFlags() {
    return flags;
  }

  @Override
  public String toString() {
    return String.format("%s (%s)", getName(), getPlatformLibraryPath());
  }
}

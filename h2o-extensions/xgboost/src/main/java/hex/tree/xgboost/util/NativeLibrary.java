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
package hex.tree.xgboost.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
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
      loaded = doLoad();
    }
    return loaded;
  }

  /**
   * Load order:
   *
   */
  private boolean doLoad() throws IOException {
    final String libName = getName();
    try {
      System.loadLibrary(libName);
      return true;
    } catch (UnsatisfiedLinkError e) {
      try {
        return extractAndLoad(getPlatformLibraryPath());
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

  private String getResourcePrefix() {
    return "lib";
  }

  private ClassLoader getClassLoader() {
    return classLoader;
  }

  private boolean extractAndLoad(String libPath) throws IOException {
    try {
      URL libResource = getLibResource(libPath, getClassLoader());
      if (libResource == null) {
        logger.debug("We don't bundle library " + libPath);
        return false;
      }
      File temp = extract(libPath, libResource);
      // Finally, load the library
      System.load(temp.getAbsolutePath());
      // Perfect loaded, break the cycle
      logger.info("Loaded library from " + libPath + " (" + temp.getAbsolutePath() + ")");
      return true;
    } catch (IOException | UnsatisfiedLinkError e) {
      logger.warn("Cannot load library from path " + libPath);
      throw new IOException(e);
    }
  }

  private URL getLibResource() {
    return getLibResource(getPlatformLibraryPath(), getClassLoader());
  }

  public boolean isBundled() {
    return getLibResource() != null;
  }

  public File extractTo(File directory) throws IOException {
    File target = new File(directory, platform.getPlatformLibName(getName()));
    extractTo(getLibResource(), target);
    return target;
  }
  
  private static void extractTo(URL libResource, File target) throws IOException {
    try (InputStream is = libResource.openStream()) {
      Files.copy(is, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private static URL getLibResource(String libPath, ClassLoader classLoader) {
    return classLoader.getResource(libPath);
  }

  private static File extract(String libPath, URL libResource)
      throws IOException, IllegalArgumentException {
    assert libResource != null : "Argument `libResource` cannot be null, make sure you only call `extract` for " +
            "libraries that are available for the current platform.";

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

    // Open output stream and copy data between source file in JAR and the temporary file
    extractTo(libResource, temp);

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

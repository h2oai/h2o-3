// Copyright (c) Corporation for National Research Initiatives
package org.python.core;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.python.compiler.Module;
import org.python.core.util.FileUtil;
import org.python.core.util.LimitedCache;
import org.python.core.util.PlatformUtil;

/**
 * Utility functions for "import" support.
 *
 * Note that this class tries to match the names of the corresponding functions from CPython's
 * Python/import.c. In these cases we use CPython's function naming style (underscores and all
 * lowercase) instead of Java's typical camelCase style so that it's easier to compare with
 * import.c.
 */
public class imp {

    private static Logger logger = Logger.getLogger("org.python.import");

    private static final String UNKNOWN_SOURCEFILE = "<unknown>";

    private static final int APIVersion = 39;

    public static final int NO_MTIME = -1;

    // This should change to Python 3.x; note that 2.7 allows relative
    // imports unless `from __future__ import absolute_import`
    public static final int DEFAULT_LEVEL = -1;

    private static final boolean IS_OSX = PySystemState.getNativePlatform().equals("darwin");

    /**
     * A bundle of a file name, the file's content and a last modified time, with no behaviour. As
     * used here, the file is a class file and the last modified time is that of the matching
     * source, while the filename is taken from the annotation in the class file. See
     * {@link imp#readCodeData(String, InputStream, boolean, long)}.
     */
    public static class CodeData {

        private final byte[] bytes;
        private final long mtime;
        private final String filename;

        public CodeData(byte[] bytes, long mtime, String filename) {
            this.bytes = bytes;
            this.mtime = mtime;
            this.filename = filename;
        }

        public byte[] getBytes() {
            return bytes;
        }

        public long getMTime() {
            return mtime;
        }

        public String getFilename() {
            return filename;
        }
    }

    /**
     * A two-way selector given to
     * {@link imp#createFromPyClass(String, InputStream, boolean, String, String, long, CodeImport)}
     * that tells it whether the source file name to give the module-class constructor, and which
     * ends up in {@code co_filename} attribute of the module's {@code PyCode}, should be from the
     * caller or the compiled file.
     */
    static enum CodeImport {
        /** Take the filename from the {@code sourceName} argument */
        source,
        /** Take filename from the compiler annotation */
        compiled_only;
    }

    /** A non-empty fromlist for __import__'ing sub-modules. */
    private static final PyObject nonEmptyFromlist = new PyTuple(PyString.fromInterned("__doc__"));

    public static ClassLoader getSyspathJavaLoader() {
        return Py.getSystemState().getSyspathJavaLoader();
    }

    /**
     * Selects the parent class loader for Jython, to be used for dynamically loaded classes and
     * resources. Chooses between the current and context class loader based on the following
     * criteria:
     *
     * <ul>
     * <li>If both are the same class loader, return that class loader.
     * <li>If either is null, then the non-null one is selected.
     * <li>If both are not null, and a parent/child relationship can be determined, then the child
     * is selected.
     * <li>If both are not null and not on a parent/child relationship, then the current class
     * loader is returned (since it is likely for the context class loader to <b>not</b> see the
     * Jython classes)
     * </ul>
     *
     * @return the parent class loader for Jython or null if both the current and context class
     *         loaders are null.
     */
    public static ClassLoader getParentClassLoader() {
        ClassLoader current = imp.class.getClassLoader();
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        if (context == current || context == null) {
            return current;
        } else if (current == null) {
            return context;
        } else if (isAncestor(context, current)) {
            return current;
        } else if (isAncestor(current, context)) {
            return context;
        } else {
            return current;
        }
    }

    /** True iff a {@code possibleAncestor} is the ancestor of the {@code subject}. */
    private static boolean isAncestor(ClassLoader possibleAncestor, ClassLoader subject) {
        try {
            ClassLoader parent = subject.getParent();
            if (possibleAncestor == parent) {
                return true;
            } else if (parent == null || parent == subject) {
                // The subject is the boot class loader
                return false;
            } else {
                return isAncestor(possibleAncestor, parent);
            }
        } catch (SecurityException e) {
            return false;
        }
    }

    private imp() {} // Prevent instantiation.

    /**
     * If the given name is found in sys.modules, the entry from there is returned. Otherwise a new
     * {@link PyModule} is created for the name and added to {@code sys.modules}. Creating the
     * module does not execute the body of the module to initialise its attributes.
     *
     * @param name fully-qualified name of the module
     * @return created {@code PyModule}
     */
    public static PyModule addModule(String name) {
        name = name.intern();
        PyObject modules = Py.getSystemState().modules;
        PyModule module = (PyModule) modules.__finditem__(name);
        if (module != null) {
            return module;
        }
        module = new PyModule(name, null);
        PyModule __builtin__ = (PyModule) modules.__finditem__("__builtin__");
        PyObject __dict__ = module.__getattr__("__dict__");
        __dict__.__setitem__("__builtins__", __builtin__.__getattr__("__dict__"));
        __dict__.__setitem__("__package__", Py.None);
        modules.__setitem__(name, module);
        return module;
    }

    /**
     * Remove name from sys.modules if present.
     *
     * @param name the module name
     */
    private static void removeModule(String name) {
        name = name.intern();
        PyObject modules = Py.getSystemState().modules;
        if (modules.__finditem__(name) != null) {
            try {
                modules.__delitem__(name);
            } catch (PyException pye) {
                // another thread may have deleted it
                if (!pye.match(Py.KeyError)) {
                    throw pye;
                }
            }
        }
    }

    /**
     * Read a stream as a new byte array and close the stream.
     *
     * @param fp to read
     * @return bytes read
     */
    private static byte[] readBytes(InputStream fp) {
        try {
            return FileUtil.readBytes(fp);
        } catch (IOException ioe) {
            throw Py.IOError(ioe);
        } finally {
            try {
                fp.close();
            } catch (IOException e) {
                throw Py.IOError(e);
            }
        }
    }

    /** Open a file, raising a {@code PyException} on error. */
    private static InputStream makeStream(File file) {
        try {
            return new FileInputStream(file);
        } catch (IOException ioe) {
            throw Py.IOError(ioe);
        }
    }

    /**
     * As {@link #createFromPyClass(String, InputStream, boolean, String, String, long, CodeImport)}
     * but always constructs the named class using {@code sourceName} as argument and makes no check
     * on the last-modified time.
     *
     * @param name module name on which to base the class name as {@code name + "$py"}
     * @param fp stream from which to read class file (closed when read)
     * @param testing if {@code true}, failures are signalled by a {@code null} not an exception
     * @param sourceName used for identification in messages and the constructor of the named class.
     * @param compiledName used for identification in messages and {@code __file__}.
     * @return the module or {@code null} on failure (if {@code testing}).
     * @throws PyException {@code ImportError} on API mismatch or i/o error.
     */
    static PyObject createFromPyClass(String name, InputStream fp, boolean testing,
                                      String sourceName, String compiledName) {
        return createFromPyClass(name, fp, testing, sourceName, compiledName, NO_MTIME);
    }

    /**
     * As {@link #createFromPyClass(String, InputStream, boolean, String, String, long, CodeImport)}
     * but always constructs the named class using {@code sourceName} as argument.
     *
     * @param name module name on which to base the class name as {@code name + "$py"}
     * @param fp stream from which to read class file (closed when read)
     * @param testing if {@code true}, failures are signalled by a {@code null} not an exception
     * @param sourceName used for identification in messages and the constructor of the named class.
     * @param compiledName used for identification in messages and {@code __file__}.
     * @param sourceLastModified time expected to match {@code MTime} annotation in the class file
     * @return the module or {@code null} on failure (if {@code testing}).
     * @throws PyException {@code ImportError} on API or last-modified time mismatch or i/o error.
     */
    static PyObject createFromPyClass(String name, InputStream fp, boolean testing,
                                      String sourceName, String compiledName, long sourceLastModified) {
        return createFromPyClass(name, fp, testing, sourceName, compiledName, sourceLastModified,
                CodeImport.source);
    }

    /**
     * Create a Python module from its compiled form, reading the class file from the open input
     * stream passed in (which is closed once read). The method may be used in a "testing" mode in
     * which the module is imported (if possible), but error conditions return {@code null}, or in a
     * non-testing mode where they throw. The caller may choose whether the source file name to give
     * the module-class constructor, and which ends up in {@code co_filename} attribute of the
     * module's {@code PyCode}, should be {@code sourceName} or the compiled file (See
     * {@link CodeImport}.)
     *
     * @param name module name on which to base the class name as {@code name + "$py"}
     * @param fp stream from which to read class file (closed when read)
     * @param testing if {@code true}, failures are signalled by a {@code null} not an exception
     * @param sourceName used for identification in messages.
     * @param compiledName used for identification in messages and {@code __file__}.
     * @param sourceLastModified time expected to match {@code MTime} annotation in the class file
     * @param source choose what to use as the file name when initialising the class
     * @return the module or {@code null} on failure (if {@code testing}).
     * @throws PyException {@code ImportError} on API or last-modified time mismatch or i/o error.
     */
    static PyObject createFromPyClass(String name, InputStream fp, boolean testing,
                                      String sourceName, String compiledName, long sourceLastModified, CodeImport source) {

        // Get the contents of a compiled ($py.class) file and some meta-data
        CodeData data = null;
        try {
            data = readCodeData(compiledName, fp, testing, sourceLastModified);
        } catch (IOException ioe) {
            if (!testing) {
                throw Py.ImportError(ioe.getMessage() + "[name=" + name + ", source=" + sourceName
                        + ", compiled=" + compiledName + "]");
            }
        }
        if (testing && data == null) {
            return null;
        }

        // Instantiate the class and have it produce its PyCode object.
        PyCode code;
        try {
            // Choose which file name to provide to the module-class constructor
            String display = source == CodeImport.compiled_only ? data.getFilename() : sourceName;
            code = BytecodeLoader.makeCode(name + "$py", data.getBytes(), display);
        } catch (Throwable t) {
            if (testing) {
                return null;
            } else {
                throw Py.JavaError(t);
            }
        }

        // Execute the PyCode object (run the module body) to populate the module __dict__
        logger.log(Level.CONFIG, "import {0} # precompiled from {1}",
                new Object[] {name, compiledName});
        return createFromCode(name, code, compiledName);
    }

    /**
     * As {@link #readCodeData(String, InputStream, boolean, long)} but do not check last-modified
     * time and return only the class file bytes as an array.
     *
     * @param name of source file (used for identification in error/log messages)
     * @param fp stream from which to read class file (closed when read)
     * @param testing if {@code true}, failures are signalled by a {@code null} not an exception
     * @return the class file bytes as an array or {@code null} on failure (if {@code testing}).
     * @throws PyException {@code ImportError} on API or last-modified time mismatch
     * @throws IOException from read failures
     */
    public static byte[] readCode(String name, InputStream fp, boolean testing) throws IOException {
        return readCode(name, fp, testing, NO_MTIME);
    }

    /**
     * As {@link #readCodeData(String, InputStream, boolean, long)} but return only the class file
     * bytes as an array.
     *
     * @param name of source file (used for identification in error/log messages)
     * @param fp stream from which to read class file (closed when read)
     * @param testing if {@code true}, failures are signalled by a {@code null} not an exception
     * @param sourceLastModified time expected to match {@code MTime} annotation in the class file
     * @return the class file bytes as an array or {@code null} on failure (if {@code testing}).
     * @throws PyException {@code ImportError} on API or last-modified time mismatch
     * @throws IOException from read failures
     */
    public static byte[] readCode(String name, InputStream fp, boolean testing,
                                  long sourceLastModified) throws IOException {
        CodeData data = readCodeData(name, fp, testing, sourceLastModified);
        if (data == null) {
            return null;
        } else {
            return data.getBytes();
        }
    }

    /**
     * As {@link #readCodeData(String, InputStream, boolean, long)} but do not check last-modified
     * time.
     *
     * @param name of source file (used for identification in error/log messages)
     * @param fp stream from which to read class file (closed when read)
     * @param testing if {@code true}, failures are signalled by a {@code null} not an exception
     * @return the {@code CodeData} bundle or {@code null} on failure (if {@code testing}).
     * @throws PyException {@code ImportError} on API mismatch
     * @throws IOException from read failures
     */
    public static CodeData readCodeData(String name, InputStream fp, boolean testing)
            throws IOException {
        return readCodeData(name, fp, testing, NO_MTIME);
    }

    /**
     * Create a {@link CodeData} object bundling the contents of a class file (given as a stream),
     * source-last-modified time supplied, and the name of the file taken from annotations on the
     * class. On the way, the method checks the API version annotation matches the current process,
     * and that the {@code org.python.compiler.MTime} annotation matches the source-last-modified
     * time passed in.
     *
     * @param name of source file (used for identification in error/log messages)
     * @param fp stream from which to read class file (closed when read)
     * @param testing if {@code true}, failures are signalled by a {@code null} not an exception
     * @param sourceLastModified time expected to match {@code MTime} annotation in the class file
     * @return the {@code CodeData} bundle or {@code null} on failure (if {@code testing}).
     * @throws PyException {@code ImportError} on API or last-modified time mismatch
     * @throws IOException from read failures
     */
    public static CodeData readCodeData(String name, InputStream fp, boolean testing,
                                        long sourceLastModified) throws IOException, PyException {

        byte[] classFileData = readBytes(fp);
        AnnotationReader ar = new AnnotationReader(classFileData);

        // Check API version fossilised in the class file against that expected
        int api = ar.getVersion();
        if (api != APIVersion) {
            if (testing) {
                return null;
            } else {
                String fmt = "compiled unit contains version %d code (%d required): %.200s";
                throw Py.ImportError(String.format(fmt, api, APIVersion, name));
            }
        }

        /*
         * The source-last-modified time is fossilised in the class file. The source may have been
         * installed from a JAR, and this will have resulted in rounding of the last-modified time
         * down (see build.xml::jar-sources) to the nearest 2 seconds.
         */
        if (testing && sourceLastModified != NO_MTIME) {
            long diff = ar.getMTime() - sourceLastModified;
            if (diff > 2000L) { // = 2000 milliseconds
                logger.log(Level.FINE, "# {0} time is {1} ms later than source",
                        new Object[] {name, diff});
                return null;
            }
        }

        // All well: make the bundle.
        return new CodeData(classFileData, sourceLastModified, ar.getFilename());
    }

    /**
     * Compile Python source in file to a class file represented by a byte array.
     *
     * @param name of module (class name will be name$py)
     * @param source file containing the source
     * @return Java byte code as array
     */
    public static byte[] compileSource(String name, File source) {
        return compileSource(name, source, null);
    }

    /**
     * Compile Python source in file to a class file represented by a byte array.
     *
     * @param name of module (class name will be name$py)
     * @param source file containing the source
     * @param filename explicit source file name (or {@code null} to use that in source)
     * @return Java byte code as array
     */
    public static byte[] compileSource(String name, File source, String filename) {
        if (filename == null) {
            filename = source.toString();
        }
        long mtime = source.lastModified();
        return compileSource(name, makeStream(source), filename, mtime);
    }

    /**
     * Compile Python source in file to a class file represented by a byte array.
     *
     * @param name of module (class name will be name$py)
     * @param source file containing the source
     * @param sourceFilename explicit source file name (or {@code null} to use that in source)
     * @param compiledFilename ignored (huh?)
     * @return Java byte code as array
     * @deprecated Use {@link #compileSource(String, File, String)} instead.
     */
    @Deprecated
    public static byte[] compileSource(String name, File source, String sourceFilename,
                                       String compiledFilename) {
        return compileSource(name, source, sourceFilename);
    }

    /** Remove the last three characters of a file name and add the compiled suffix "$py.class". */
    public static String makeCompiledFilename(String filename) {
        return filename.substring(0, filename.length() - 3) + "$py.class";
    }

    /**
     * Stores the bytes in compiledSource in compiledFilename.
     *
     * If compiledFilename is null, it's set to the results of makeCompiledFilename(sourcefileName).
     *
     * If sourceFilename is null or set to UNKNOWN_SOURCEFILE, then null is returned.
     *
     * @return the compiledFilename eventually used; or null if a compiledFilename couldn't be
     *         determined or if an error was thrown while writing to the cache file.
     */
    public static String cacheCompiledSource(String sourceFilename, String compiledFilename,
                                             byte[] compiledSource) {
        if (compiledFilename == null) {
            if (sourceFilename == null || sourceFilename.equals(UNKNOWN_SOURCEFILE)) {
                return null;
            }
            compiledFilename = makeCompiledFilename(sourceFilename);
        }
        FileOutputStream fop = null;
        try {
            SecurityManager man = System.getSecurityManager();
            if (man != null) {
                man.checkWrite(compiledFilename);
            }
            fop = new FileOutputStream(FileUtil.makePrivateRW(compiledFilename));
            fop.write(compiledSource);
            fop.close();
            return compiledFilename;
        } catch (IOException | SecurityException exc) {
            // If we can't write the cache file, just log and continue
            logger.log(Level.FINE, "Unable to write to source cache file ''{0}'' due to {1}",
                    new Object[] {compiledFilename, exc});
            return null;
        } finally {
            if (fop != null) {
                try {
                    fop.close();
                } catch (IOException e) {
                    logger.log(Level.FINE, "Unable to close source cache file ''{0}'' due to {1}",
                            new Object[] {compiledFilename, e});
                }
            }
        }
    }

    /**
     * Compile Python source to a class file represented by a byte array.
     *
     * @param name of module (class name will be name$py)
     * @param source open input stream (will be closed)
     * @param filename of source (or {@code null} if unknown)
     * @return Java byte code as array
     */
    public static byte[] compileSource(String name, InputStream source, String filename) {
        return compileSource(name, source, filename, NO_MTIME);
    }

    /**
     * Compile Python source to a class file represented by a byte array.
     *
     * @param name of module (class name will be name$py)
     * @param source open input stream (will be closed)
     * @param filename of source (or {@code null} if unknown)
     * @param mtime last-modified time of source, to annotate class
     * @return Java byte code as array
     */
    public static byte[] compileSource(String name, InputStream source, String filename,
                                       long mtime) {
        ByteArrayOutputStream ofp = new ByteArrayOutputStream();
        try {
            if (filename == null) {
                filename = UNKNOWN_SOURCEFILE;
            }
            org.python.antlr.base.mod node;
            try {
                // Compile source to AST
                node = ParserFacade.parse(source, CompileMode.exec, filename, new CompilerFlags());
            } finally {
                source.close();
            }
            // Generate code
            Module.compile(node, ofp, name + "$py", filename, true, false, null, mtime);
            return ofp.toByteArray();
        } catch (Throwable t) {
            throw ParserFacade.fixParseError(null, t, filename);
        }
    }

    public static PyObject createFromSource(String name, InputStream fp, String filename) {
        return createFromSource(name, fp, filename, null, NO_MTIME);
    }

    public static PyObject createFromSource(String name, InputStream fp, String filename,
                                            String outFilename) {
        return createFromSource(name, fp, filename, outFilename, NO_MTIME);
    }

    /**
     * Compile Jython source (as an {@code InputStream}) to a module.
     *
     * @param name of the module to create (class will be name$py)
     * @param fp opened on the (Jython) source to compile (will be closed)
     * @param filename of the source backing {@code fp} (to embed in class as data)
     * @param outFilename in which to write the compiled class
     * @param mtime last modified time of the file backing {@code fp}
     * @return created module
     */
    public static PyObject createFromSource(String name, InputStream fp, String filename,
                                            String outFilename, long mtime) {
        byte[] bytes = compileSource(name, fp, filename, mtime);
        if (!Py.getSystemState().dont_write_bytecode) {
            outFilename = cacheCompiledSource(filename, outFilename, bytes);
        }

        logger.log(Level.CONFIG, "import {0} # from {1}", new Object[]{name, filename});

        PyCode code = BytecodeLoader.makeCode(name + "$py", bytes, filename);
        return createFromCode(name, code, filename);
    }

    /**
     * Returns a module with the given name whose contents are the results of running c. __file__ is
     * set to whatever is in c.
     */
    public static PyObject createFromCode(String name, PyCode c) {
        return createFromCode(name, c, null);
    }

    /**
     * Return a Python module with the given {@code name} whose attributes are the result of running
     * {@code PyCode c}. If {@code moduleLocation != null} it is used to set {@code __file__ }.
     * <p>
     * In normal circumstances, if {@code c} comes from a local {@code .py} file or compiled
     * {@code $py.class} class the caller should should set {@code moduleLocation} to something like
     * {@code new File(moduleLocation).getAbsolutePath()}. If {@code c} comes from a remote file or
     * is a JAR, {@code moduleLocation} should be the full URI for that source or class.
     *
     * @param name fully-qualified name of the module
     * @param c code supplying the module
     * @param moduleLocation to become {@code __file__} if not {@code null}
     * @return the module object
     */
    public static PyObject createFromCode(String name, PyCode c, String moduleLocation) {
        checkName(name);
        PyModule module = addModule(name);

        PyBaseCode code = null;
        if (c instanceof PyBaseCode) {
            code = (PyBaseCode) c;
        }

        if (moduleLocation != null) {
            // Standard library expects __file__ to be encoded bytes
            module.__setattr__("__file__", Py.fileSystemEncode(moduleLocation));
        } else if (module.__findattr__("__file__") == null) {
            // Should probably never happen (but maybe with an odd custom builtins, or
            // Java Integration)
            logger.log(Level.WARNING, "{0} __file__ is unknown", name);
        }

        ReentrantLock importLock = Py.getSystemState().getImportLock();
        importLock.lock();
        try {
            PyFrame f = new PyFrame(code, module.__dict__, module.__dict__, null);
            code.call(Py.getThreadState(), f);
            return module;
        } catch (RuntimeException t) {
            removeModule(name);
            throw t;
        } finally {
            importLock.unlock();
        }
    }

    @SuppressWarnings("unchecked")
    static PyObject createFromClass(String name, Class<?> c) {
        // Two choices. c implements PyRunnable or c is Java package
        if (PyRunnable.class.isAssignableFrom(c)) {
            try {
                if (ContainsPyBytecode.class.isAssignableFrom(c)) {
                    BytecodeLoader.fixPyBytecode((Class<? extends ContainsPyBytecode>) c);
                }
                return createFromCode(name,
                        ((PyRunnable) c.getDeclaredConstructor().newInstance()).getMain());
            } catch (ReflectiveOperationException | SecurityException | IllegalArgumentException
                     | IOException e) {
                throw Py.JavaError(e);
            }
        }
        return PyType.fromClass(c);
    }

    public static PyObject getImporter(PyObject p) {
        PySystemState sys = Py.getSystemState();
        return getPathImporter(sys.path_importer_cache, sys.path_hooks, p);
    }

    /**
     * Return an importer object for an element of {@code sys.path} or of a package's
     * {@code __path__}, possibly by fetching it from the {@code cache}. If it wasn’t yet cached,
     * traverse {@code hooks} until a hook is found that can handle the path item. Return
     * {@link Py#None} if no hook could do so. This tells our caller it should fall back to the
     * built-in import mechanism. Cache the result in {@code cache}. Return a new reference to the
     * importer object.
     * <p>
     * This is the "path hooks" mechanism first described in PEP 302
     *
     * @param cache normally {@code sys.path_importer_cache}
     * @param hooks normally (@code sys.path_hooks}
     * @param p an element of {@code sys.path} or of a package's {@code __path__}
     * @return the importer object for the path element or {@code Py.None} for "fall-back".
     */
    static PyObject getPathImporter(PyObject cache, PyList hooks, PyObject p) {

        // Is it in the cache?
        PyObject importer = cache.__finditem__(p);
        if (importer != null) {
            return importer;
        }

        // Nothing in the cache, so check all hooks.
        PyObject iter = hooks.__iter__();
        for (PyObject hook; (hook = iter.__iternext__()) != null;) {
            try {
                importer = hook.__call__(p);
                break;
            } catch (PyException e) {
                if (!e.match(Py.ImportError)) {
                    throw e;
                }
            }
        }

        if (importer == null) {
            // No hook claims to handle the location p, so add an imp.NullImporter
            try {
                importer = new PyNullImporter(p);
            } catch (PyException e) {
                if (!e.match(Py.ImportError)) {
                    throw e;
                }
            }
        }

        if (importer != null) {
            // We found an importer. Cache it for next time.
            cache.__setitem__(p, importer);
        } else {
            // Caller will fall-back to built-in mechanisms.
            importer = Py.None;
        }

        return importer;
    }

    /**
     * Try to load a Python module from {@code sys.meta_path}, as a built-in module, or from either
     * the {@code __path__} of the enclosing package or {@code sys.path} if the module is being
     * sought at the top level.
     *
     * @param name simple name of the module.
     * @param moduleName fully-qualified (dotted) name of the module (ending in {@code name}).
     * @param path {@code __path__} of the enclosing package (or {@code null} if top level).
     * @return the module if we can load it (or {@code null} if we can't).
     */
    static PyObject find_module(String name, String moduleName, PyList path) {
        PyObject loader = Py.None;
        PySystemState sys = Py.getSystemState();
        PyObject metaPath = sys.meta_path;

        // Check for importers along sys.meta_path
        for (PyObject importer : metaPath.asIterable()) {
            PyObject findModule = importer.__getattr__("find_module");
            loader = findModule.__call__(new PyObject[] { //
                    new PyString(moduleName), path == null ? Py.None : path});
            if (loader != Py.None) {
                return loadFromLoader(loader, moduleName);
            }
        }

        // Attempt to load from (prepared) builtins in sys.builtins.
        PyObject ret = loadBuiltin(moduleName);
        if (ret != null) {
            return ret;
        }

        // Note the path here may be sys.path or the search path of a Python package.
        path = path == null ? sys.path : path;
        for (int i = 0; ret == null && i < path.__len__(); i++) {
            PyObject p = path.__getitem__(i);
            // Is there a path-specific importer?
            PyObject importer = getPathImporter(sys.path_importer_cache, sys.path_hooks, p);
            if (importer != Py.None) {
                // A specific importer is defined. Try its finder method.
                PyObject findModule = importer.__getattr__("find_module");
                loader = findModule.__call__(new PyObject[] {new PyString(moduleName)});
                if (loader != Py.None) {
                    return loadFromLoader(loader, moduleName);
                }
            }
            // p could be a unicode or bytes object (in the file system encoding)
            String pathElement = fileSystemDecode(p, false);
            if (pathElement != null) {
                ret = loadFromSource(sys, name, moduleName, pathElement);
            }
        }

        return ret;
    }

    /**
     * Load a built-in module by reference to {@link PySystemState#builtins}, which maps Python
     * module names to class names. Special treatment is given to the modules {@code sys} and
     * {@code __builtin__}.
     *
     * @param fully-qualified name of module
     * @return the module named
     */
    private static PyObject loadBuiltin(String name) {
        final String MSG = "import {0} # builtin";
        if (name == "sys") {
            logger.log(Level.CONFIG, MSG, name);
            return Py.java2py(Py.getSystemState());
        }
        if (name == "__builtin__") {
            logger.log(Level.CONFIG, MSG, new Object[] {name, name});
            return new PyModule("__builtin__", Py.getSystemState().builtins);
        }
        String mod = PySystemState.getBuiltin(name);
        if (mod != null) {
            Class<?> c = Py.findClassEx(mod, "builtin module");
            if (c != null) {
                logger.log(Level.CONFIG, "import {0} # builtin {1}", new Object[] {name, mod});
                try {
                    if (PyObject.class.isAssignableFrom(c)) { // xxx ok?
                        return PyType.fromClass(c);
                    }
                    return createFromClass(name, c);
                } catch (NoClassDefFoundError e) {
                    throw Py.ImportError(
                            "Cannot import " + name + ", missing class " + c.getName());
                }
            }
        }
        return null;
    }

    static PyObject loadFromLoader(PyObject importer, String name) {
        checkName(name);
        PyObject load_module = importer.__getattr__("load_module");
        ReentrantLock importLock = Py.getSystemState().getImportLock();
        importLock.lock();
        try {
            return load_module.__call__(new PyObject[] {new PyString(name)});
        } finally {
            importLock.unlock();
        }
    }

    public static PyObject loadFromCompiled(String name, InputStream stream, String sourceName,
                                            String compiledName) {
        return createFromPyClass(name, stream, false, sourceName, compiledName);
    }

    /**
     * Import a module defined in Python by loading it from source (or a compiled
     * {@code name$pyclass}) file in the specified location (often an entry from {@code sys.path},
     * or a sub-directory of it named for the {@code modName}. For example, if {@code name} is
     * "pkg1" and the {@code modName} is "pkg.pkg1", {@code location} might be "mylib/pkg".
     *
     * @param sys the sys module of the interpreter importing the module.
     * @param name by which to look for files or a directory representing the module.
     * @param modName name by which to enter the module in {@code sys.modules}.
     * @param location where to look for the {@code name}.
     * @return the module if we can load it (or {@code null} if we can't).
     */
    static PyObject loadFromSource(PySystemState sys, String name, String modName,
                                   String location) {
        File sourceFile;     // location/name/__init__.py or location/name.py
        File compiledFile;   // location/name/__init__$py.class or location/name$py.class
        boolean haveSource = false, haveCompiled = false;

        // display* names are for mainly identification purposes (e.g. __file__)
        String displayLocation = (location.equals("") || location.equals(",")) ? null : location;
        String displaySourceName, displayCompiledName;

        try {
            /*
             * Distinguish package and module cases by choosing File objects sourceFile and
             * compiledFile based on name/__init__ or name. haveSource and haveCompiled are set true
             * if the corresponding source or compiled files exist, and this is what steers the
             * loading in the second part of the process.
             */
            String dirName = sys.getPath(location);
            File dir = new File(dirName, name);

            if (dir.isDirectory()) {
                // This should be a package: location/name
                File displayDir = new File(displayLocation, name);

                // Source is location/name/__init__.py
                String sourceName = "__init__.py";
                sourceFile = new File(dir, sourceName);
                displaySourceName = new File(displayDir, sourceName).getPath();

                // Compiled is location/name/__init__$py.class
                String compiledName = makeCompiledFilename(sourceName);
                compiledFile = new File(dir, compiledName);
                displayCompiledName = new File(displayDir, compiledName).getPath();

                // Check the directory name is ok according to case-matching option and platform.
                if (caseok(dir, name)) {
                    haveSource = sourceFile.isFile() && Files.isReadable(sourceFile.toPath());
                    haveCompiled = compiledFile.isFile() && Files.isReadable(compiledFile.toPath());
                }

                if (haveSource || haveCompiled) {
                    // Create a PyModule (uninitialised) for name, called modName in sys.modules
                    PyModule m = addModule(modName);
                    PyString filename = Py.fileSystemEncode(displayDir.getPath());
                    m.__dict__.__setitem__("__path__", new PyList(new PyObject[] {filename}));
                } else {
                    /*
                     * There is neither source nor compiled code for __init__.py. In Jython, this
                     * message warning is premature, as there may be a Java package by this name.
                     */
                    String printDirName = PyString.encode_UnicodeEscape(dir.getPath(), '\'');
                    Py.warning(Py.ImportWarning, String.format(
                            "Not importing directory %s: missing __init__.py", printDirName));
                }

            } else {
                // This is a (non-package) module: location/name

                // Source is location/name.py
                String sourceName = name + ".py";
                sourceFile = new File(dirName, sourceName);     // location/name.py
                displaySourceName = new File(displayLocation, sourceName).getPath();

                // Compiled is location/name$py.class
                String compiledName = makeCompiledFilename(sourceName);
                compiledFile = new File(dirName, compiledName); // location/name$py.class
                displayCompiledName = new File(displayLocation, compiledName).getPath();

                // Check file names exist (and readable) and ok according to case-matching option and platform.
                haveSource = sourceFile.isFile() && caseok(sourceFile, sourceName) && Files.isReadable(sourceFile.toPath());
                haveCompiled = compiledFile.isFile() && caseok(compiledFile, compiledName) && Files.isReadable(compiledFile.toPath());
            }

            /*
             * Now we are ready to load and execute the module in sourceFile or compiledFile, from
             * its compiled or source form, as directed by haveSource and haveCompiled.
             */
            if (haveSource) {
                // Try to create the module from source or an existing compiled class.
                long pyTime = sourceFile.lastModified();

                if (haveCompiled) {
                    // We have the compiled file and will use that if it is not out of date
                    logger.log(Level.FINE, "# trying precompiled {0}", compiledFile.getPath());
                    long classTime = compiledFile.lastModified();
                    if (classTime >= pyTime) {
                        // The compiled file does not appear out of date relative to the source.
                        PyObject ret = createFromPyClass(modName, makeStream(compiledFile), //
                                true, // OK to fail here as we have the source
                                displaySourceName, displayCompiledName, pyTime);
                        if (ret != null) {
                            return ret;
                        }
                    } else {
                        logger.log(Level.FINE,
                                "# {0} dated ({1,date} {1,time,long}) < ({2,date} {2,time,long})",
                                new Object[] {name, new Date(classTime), new Date(pyTime)});
                    }
                }

                // The compiled class is not present, is out of date, or using it failed somehow.
                logger.log(Level.FINE, "# trying source {0}", sourceFile.getPath());
                return createFromSource(modName, makeStream(sourceFile), displaySourceName,
                        compiledFile.getPath(), pyTime);

            } else if (haveCompiled) {
                // There is no source, try loading compiled
                logger.log(Level.FINE, "# trying precompiled with no source {0}",
                        compiledFile.getPath());
                return createFromPyClass(modName, makeStream(compiledFile), //
                        false, // throw ImportError here if this fails
                        displaySourceName, displayCompiledName, NO_MTIME, CodeImport.compiled_only);
            }

        } catch (SecurityException e) {
            // We were prevented from reading some essential file, so pretend we didn't find it.
        }
        return null;
    }

    /**
     * Check that the canonical name of {@code file} matches {@code filename}, case-sensitively,
     * even when the OS platform is case-insensitive. This is used to obtain as a check during
     * import on platforms (Windows) that may be case-insensitive regarding file open. It is assumed
     * that {@code file} was derived from attempting to find {@code filename}, so it returns
     * {@code true} on a case-sensitive platform.
     * <p>
     * Algorithmically, we return {@code true} if any of the following is true:
     * <ul>
     * <li>{@link Options#caseok} is {@code true} (default is {@code false}).</li>
     * <li>The platform is case sensitive (according to
     * {@link PlatformUtil#isCaseInsensitive()})</li>
     * <li>The name part of the canonical path of {@code file} starts with {@code filename}</li>
     * <li>The name of any sibling (in the same directory as) {@code file} equals
     * {@code filename}</li>
     * </ul>
     * and false otherwise.
     *
     * @param file to be tested
     * @param filename to be matched
     * @return {@code file} matches {@code filename}
     */
    public static boolean caseok(File file, String filename) {
        if (Options.caseok || !PlatformUtil.isCaseInsensitive()) {
            return true;
        }
        try {
            File canFile = new File(file.getCanonicalPath());
            boolean match = filename.regionMatches(0, canFile.getName(), 0, filename.length());
            if (!match) {
                // Get parent and look for exact match in listdir(). This is horrible, but rare.
                for (String c : file.getParentFile().list()) {
                    if (c.equals(filename)) {
                        return true;
                    }
                }
            }
            return match;
        } catch (IOException exc) {
            return false;
        }
    }

    /**
     * Load the module by name. Upon loading the module it will be added to sys.modules.
     *
     * @param name the name of the module to load
     * @return the loaded module
     */
    public static PyObject load(String name) {
        checkName(name);
        ReentrantLock importLock = Py.getSystemState().getImportLock();
        importLock.lock();
        try {
            return import_first(name, new StringBuilder());
        } finally {
            importLock.unlock();
        }
    }

    /**
     * Find the parent package name for a module.
     * <p>
     * If __name__ does not exist in the module or if level is <code>0</code>, then the parent is
     * <code>null</code>. If __name__ does exist and is not a package name, the containing package
     * is located. If no such package exists and level is <code>-1</code>, the parent is
     * <code>null</code>. If level is <code>-1</code>, the parent is the current name. Otherwise,
     * <code>level-1</code> dotted parts are stripped from the current name. For example, the
     * __name__ <code>"a.b.c"</code> and level <code>2</code> would return <code>"a.b"</code>, if
     * <code>c</code> is a package and would return <code>"a"</code>, if <code>c</code> is not a
     * package.
     *
     * @param dict the __dict__ of a loaded module that is the context of the import statement
     * @param level used for relative and absolute imports. -1 means try both, 0 means absolute
     *            only, positive ints represent the level to look upward for a relative path (1
     *            means current package, 2 means one level up). See PEP 328 at
     *            http://www.python.org/dev/peps/pep-0328/
     *
     * @return the parent name for a module
     */
    private static String get_parent(PyObject dict, int level) {
        String modname;
        int orig_level = level;

        if ((dict == null && level == -1) || level == 0) {
            // try an absolute import
            return null;
        }

        PyObject tmp = dict.__finditem__("__package__");
        if (tmp != null && tmp != Py.None) {
            if (!Py.isInstance(tmp, PyString.TYPE)) {
                throw Py.ValueError("__package__ set to non-string");
            }
            modname = ((PyString) tmp).getString();
        } else {
            // __package__ not set, so figure it out and set it.

            tmp = dict.__finditem__("__name__");
            if (tmp == null) {
                return null;
            }
            modname = tmp.toString();

            // locate the current package
            tmp = dict.__finditem__("__path__");
            if (tmp instanceof PyList) {
                // __path__ is set, so modname is already the package name.
                dict.__setitem__("__package__", new PyString(modname));
            } else {
                // __name__ is not a package name, try one level upwards.
                int dot = modname.lastIndexOf('.');
                if (dot == -1) {
                    if (level <= -1) {
                        // there is no package, perform an absolute search
                        dict.__setitem__("__package__", Py.None);
                        return null;
                    }
                    throw Py.ValueError("Attempted relative import in non-package");
                }
                // modname should be the package name.
                modname = modname.substring(0, dot);
                dict.__setitem__("__package__", new PyString(modname));
            }
        }

        // walk upwards if required (level >= 2)
        while (level-- > 1) {
            int dot = modname.lastIndexOf('.');
            if (dot == -1) {
                throw Py.ValueError("Attempted relative import beyond toplevel package");
            }
            modname = modname.substring(0, dot);
        }

        if (Py.getSystemState().modules.__finditem__(modname) == null) {
            if (orig_level < 1) {
                if (modname.length() > 0) {
                    Py.warning(Py.RuntimeWarning, String.format(
                            "Parent module '%.200s' not found " + "while handling absolute import",
                            modname));
                }
            } else {
                throw Py.SystemError(String.format(
                        "Parent module '%.200s' not loaded, " + "cannot perform relative import",
                        modname));
            }
        }
        return modname.intern();
    }

    /**
     * Try to import the module named by <i>parentName.name</i>. The method tries 3 ways, accepting
     * the first that * succeeds:
     * <ol>
     * <li>Check for the module (by its fully-qualified name) in {@code sys.modules}.</li>
     * <li>If {@code mod==null}, try to load the module via
     * {@link #find_module(String, String, PyList)}. If {@code mod!=null}, find it as an attribute
     * of {@code mod} via its {@link PyObject#impAttr(String)} method (which then falls back to
     * {@code find_module} if {@code mod} has a {@code __path__}). Either way, add the loaded module
     * to {@code sys.modules}.</li>
     * <li>Try to load the module as a Java package by the name {@code outerFullName}
     * {@link JavaImportHelper#tryAddPackage(String, PyObject)}.</li>
     * </ol>
     * Finally, if one is found, If a module by the given name already exists in {@code sys.modules}
     * it will be returned from there directly. Otherwise, in {@code mod==null} (frequent case) it
     * will be looked for via {@link #find_module(String, String, PyList)}.
     * <p>
     * The case {@code mod!=null} supports circumstances in which the module sought may be found as
     * an attribute of a parent module.
     *
     * @param mod if not {@code null}, a package where the module may be an attribute.
     * @param parentName parent name of the module. (Buffer is appended with "." and {@code name}.
     * @param name the (simple) name of the module to load
     * @param outerFullName name to use with the {@code JavaImportHelper}.
     * @param fromlist if not {@code null} the import is {@code from <module> import <fromlist>}
     * @return the imported module (or {@code null} or {@link Py#None} on failure).
     */
    private static PyObject import_next(PyObject mod, StringBuilder parentName, String name,
                                        String outerFullName, PyObject fromlist) {

        // Concatenate the parent name and module name *modifying the parent name buffer*
        if (parentName.length() > 0 && name != null && name.length() > 0) {
            parentName.append('.');
        }
        String fullName = parentName.append(name).toString().intern();

        // Check if already in sys.modules (possibly Py.None).
        PyObject modules = Py.getSystemState().modules;
        PyObject ret = modules.__finditem__(fullName);
        if (ret != null) {
            return ret;
        }

        if (mod == null) {
            // We are looking for a top-level module
            ret = find_module(fullName, name, null);
        } else {
            // Look within mod as enclosing package
            ret = mod.impAttr(name.intern());
        }

        if (ret == null || ret == Py.None) {
            // Maybe this is a Java package: full name from the import and maybe classes to import
            if (JavaImportHelper.tryAddPackage(outerFullName, fromlist)) {
                // The action has already added it to sys.modules
                ret = modules.__finditem__(fullName);
            }
            return ret;
        }

        // The find operation may have added to sys.modules the module object we seek.
        if (modules.__finditem__(fullName) == null) {
            modules.__setitem__(fullName, ret);     // Nope, add it
        } else {
            ret = modules.__finditem__(fullName);   // Yep, return that instead
        }

        // On OSX we currently have to monkeypatch setuptools.command.easy_install.
        if (IS_OSX && fullName.equals("setuptools.command")) {
            // See http://bugs.jython.org/issue2570
            load("_fix_jython_setuptools_osx");
        }

        return ret;
    }

    /**
     * Top of the import logic in the case of a simple {@code import a.b.c.m}.
     *
     * @param name fully-qualified name of module to import {@code import a.b.c.m}
     * @param parentName used as a workspace as the search descends the package hierarchy
     * @return the named module (never {@code null} or {@code None})
     * @throws PyException {@code ImportError} if not found
     */
    private static PyObject import_first(String name, StringBuilder parentName) throws PyException {
        PyObject ret = import_next(null, parentName, name, null, null);
        if (ret == null || ret == Py.None) {
            throw Py.ImportError("No module named " + name);
        }
        return ret;
    }

    /**
     * Top of the import logic in the case of a complex {@code from a.b.c.m import n1, n2, n3}.
     *
     * @param name fully-qualified name of module to import {@code a.b.c.m}.
     * @param parentName used as a workspace as the search descends the package hierarchy
     * @param fullName the "outer" name by which the module is known {@code a.b.c.m}.
     * @param fromlist names to import from the module {@code n1, n2, n3}.
     * @return the named module (never returns {@code null} or {@code None})
     * @throws PyException {@code ImportError} if not found
     */
    private static PyObject import_first(String name, StringBuilder parentName,
                                         String fullName, PyObject fromlist) throws PyException {

        // Try the "normal" Python import process
        PyObject ret = import_next(null, parentName, name, fullName, fromlist);

        // If unsuccessful try importing as a Java package
        if (ret == null || ret == Py.None) {
            if (JavaImportHelper.tryAddPackage(fullName, fromlist)) {
                ret = import_next(null, parentName, name, fullName, fromlist);
            }
        }

        // If still unsuccessful, it's an error
        if (ret == null || ret == Py.None) {
            throw Py.ImportError("No module named " + name);
        }
        return ret;
    }

    /**
     * Iterate through the components (after the first) of a fully-qualified module name
     * {@code a.b.c.m} finding the corresponding modules {@code a.b}, {@code a.b.c}, and
     * {@code a.b.c.m}, importing them if necessary. This is a helper to
     * {@link #import_module_level(String, boolean, PyObject, PyObject, int)}, used when the module
     * name involves more than one level.
     * <p>
     * This method may be called in support of (effectively) of a simple import statement like
     * {@code import a.b.c.m} or a complex statement {@code from a.b.c.m import n1, n2, n3}. This
     * method always returns the "deepest" name, in the example, the module {@code m} whose full
     * name is {@code a.b.c.m}.
     *
     * @param mod top module of the import
     * @param parentName used as a workspace as the search descends the package hierarchy
     * @param restOfName {@code b.c.m}
     * @param fullName {@code a.b.c.m}
     * @param fromlist names to import from the module {@code n1, n2, n3}.
     * @return the last named module (never {@code null} or {@code None})
     * @throws PyException {@code ImportError} if not found
     */
    // ??pending: check if result is really a module/jpkg/jclass?
    private static PyObject import_logic(PyObject mod, StringBuilder parentName, String restOfName,
                                         String fullName, PyObject fromlist) throws PyException {

        int dot = 0;
        int start = 0;

        do {
            // Extract the name that starts at restOfName[start:] up to next dot.
            String name;
            dot = restOfName.indexOf('.', start);
            if (dot == -1) {
                name = restOfName.substring(start);
            } else {
                name = restOfName.substring(start, dot);
            }

            PyJavaPackage jpkg = null;
            if (mod instanceof PyJavaPackage) {
                jpkg = (PyJavaPackage) mod;
            }

            // Find (and maybe import) the package/module corresponding to this new segment.
            mod = import_next(mod, parentName, name, fullName, fromlist);

            // Try harder when importing as a Java package :/
            if (jpkg != null && (mod == null || mod == Py.None)) {
                // try again -- under certain circumstances a PyJavaPackage may
                // have been added as a side effect of the last import_next
                // attempt. see Lib/test_classpathimport.py#test_bug1126
                mod = import_next(jpkg, parentName, name, fullName, fromlist);
            }

            // If still unsuccessful, it's an error
            if (mod == null || mod == Py.None) {
                throw Py.ImportError("No module named " + name);
            }

            // Next module/package simple-name starts just after the last dot we found
            start = dot + 1;
        } while (dot != -1);

        return mod;
    }

    /**
     * Import a module by name. This supports the default {@code __import__()} function
     * {@code __builtin__.__import__}. (Called with the import system locked.)
     *
     * @param name qualified name of the package/module to import (may be relative)
     * @param top if true, return the top module in the name, otherwise the last
     * @param modDict the __dict__ of the importing module (used to navigate a relative import)
     * @param fromlist list of names being imported
     * @param level 0=absolute, n&gt;0=relative levels to go up - 1, -1=try relative then absolute.
     * @return an imported module (Java or Python)
     */
    private static PyObject import_module_level(String name, boolean top, PyObject modDict,
                                                PyObject fromlist, int level) {

        // Check for basic invalid call
        if (name.length() == 0) {
            if (level == 0 || modDict == null) {
                throw Py.ValueError("Empty module name");
            } else {
                PyObject moduleName = modDict.__findattr__("__name__");
                // XXX: should this test be for "__main__"?
                if (moduleName != null && moduleName.toString().equals("__name__")) {
                    throw Py.ValueError("Attempted relative import in non-package");
                }
            }
        }

        // Seek the module (in sys.modules) that the import is relative to.
        PyObject modules = Py.getSystemState().modules;
        PyObject pkgMod = null;
        String pkgName = null;
        if (modDict != null && modDict.isMappingType()) {
            pkgName = get_parent(modDict, level);
            pkgMod = modules.__finditem__(pkgName);
            if (pkgMod != null && !(pkgMod instanceof PyModule)) {
                pkgMod = null;
            }
        }

        // Extract the first element of the (fully qualified) name.
        int dot = name.indexOf('.');
        String firstName;
        if (dot == -1) {
            firstName = name;
        } else {
            firstName = name.substring(0, dot);
        }

        // Import the first-named module, relative to pkgMod (which may be null)
        StringBuilder parentName = new StringBuilder(pkgMod != null ? pkgName : "");
        PyObject topMod = import_next(pkgMod, parentName, firstName, name, fromlist);

        if (topMod == Py.None || topMod == null) {
            // The first attempt failed.
            parentName = new StringBuilder("");
            // could throw ImportError
            if (level > 0) {
                // Import relative to context. pkgName was already computed from level.
                topMod = import_first(pkgName + "." + firstName, parentName, name, fromlist);
            } else {
                // Absolute import
                topMod = import_first(firstName, parentName, name, fromlist);
            }
        }

        PyObject mod = topMod;

        if (dot != -1) {
            // This is a dotted name: work through the remaining name elements.
            mod = import_logic(topMod, parentName, name.substring(dot + 1), name, fromlist);
        }

        if (top) {
            return topMod;
        }

        if (fromlist != null && fromlist != Py.None) {
            ensureFromList(mod, fromlist, name);
        }
        return mod;
    }

    /** Defend against attempt to import by filename (withdrawn feature). */
    private static void checkNotFile(String name){
        if (name.indexOf(File.separatorChar) != -1) {
            throw Py.ImportError("Import by filename is not supported.");
        }
    }

    /**
     * Enforce ASCII module name, as a guard on module names supplied as an argument. The parser
     * guarantees the name from an actual import statement is a valid identifier.
     */
    private static void checkName(String name) {
        for (int i = name.length(); i > 0;) {
            if (name.charAt(--i) > 255) {
                throw Py.ImportError("No module named " + name);
            }
        }
    }

    /**
     * This cache supports {@link #fileSystemDecode(PyObject)} and
     * {@link #fileSystemDecode(PyObject, boolean)}. Observation shows the import mechanism converts
     * the same file name hundreds of times during any use of Jython, so we use this to remember the
     * conversions of recent file names.
     */
    // 20 is plenty
    private static LimitedCache<PyObject, String> fileSystemDecodeCache = new LimitedCache<>(20);

    /**
     * A wrapper for {@link Py#fileSystemDecode(PyObject)} for <b>project internal use</b> within
     * the import mechanism to convert decoding errors that occur during import to either
     * {@code null} or {@link Py#ImportError(String)} calls (and a log message), which usually
     * results in quiet failure.
     *
     * @param p assumed to be a (partial) file path
     * @param raiseImportError if true and {@code p} cannot be decoded raise {@code ImportError}.
     * @return String form of the object {@code p} (or {@code null}).
     */
    public static String fileSystemDecode(PyObject p, boolean raiseImportError) {
        try {
            String decoded = fileSystemDecodeCache.get(p);
            if (decoded == null) {
                decoded = Py.fileSystemDecode(p);
                fileSystemDecodeCache.add(p, decoded);
            }
            return decoded;
        } catch (PyException e) {
            if (e.match(Py.UnicodeDecodeError)) {
                // p is bytes we cannot convert to a String using the FS encoding
                if (raiseImportError) {
                    logger.log(Level.CONFIG, "Cannot decode path entry {0}", p.__repr__());
                    throw Py.ImportError("cannot decode");
                }
                return null;
            } else {
                // Any other kind of exception continues as itself
                throw e;
            }
        }
    }

    /**
     * For <b>project internal use</b>, equivalent to {@code fileSystemDecode(p, true)} (see
     * {@link #fileSystemDecode(PyObject, boolean)}).
     *
     * @param p assumed to be a (partial) file path
     * @return String form of the object {@code p}.
     */
    public static String fileSystemDecode(PyObject p) {
        return fileSystemDecode(p, true);
    }

    /**
     * Ensure that the items mentioned in the from-list of an import are actually present, even if
     * they are modules we have not imported yet.
     *
     * @param mod module we are importing from
     * @param fromlist tuple of names to import
     * @param name of module we are importing from (as given, may be relative)
     */
    private static void ensureFromList(PyObject mod, PyObject fromlist, String name) {
        ensureFromList(mod, fromlist, name, false);
    }

    /**
     * Ensure that the items mentioned in the from-list of an import are actually present, even if
     * they are modules we have not imported yet.
     *
     * @param mod module we are importing from
     * @param fromlist tuple of names to import
     * @param name of module we are importing from (as given, may be relative)
     * @param recursive true, when this method calls itself
     */
    private static void ensureFromList(PyObject mod, PyObject fromlist, String name,
                                       boolean recursive) {
        // THE ONLY CUSTOM CHANGE MADE IN THIS FILE
        // The last Jython version that contains this "if" statement is 2.7.1b3. The newer versions throw an exception
        // on line 1495 "Item in from list not a string". The failing type [None] is created in the library, not in H2O
        // code.
        if (mod.__findattr__("__path__") == null) {
            return;
        }
        // THE END OF THE CUSTOM CHANGE 
        
        // This can happen with imports like "from . import foo"
        if (name.length() == 0) {
            name = mod.__findattr__("__name__").toString();
        }

        StringBuilder modNameBuffer = new StringBuilder(name);
        for (PyObject item : fromlist.asIterable()) {
            if (!Py.isInstance(item, PyBaseString.TYPE)) {
                throw Py.TypeError("Item in ``from list'' not a string");
            }
            if (item.toString().equals("*")) {
                if (recursive) {
                    // Avoid endless recursion
                    continue;
                }
                PyObject all;
                if ((all = mod.__findattr__("__all__")) != null) {
                    ensureFromList(mod, all, name, true);
                }
            }

            if (mod.__findattr__((PyString)item) == null) {
                String fullName = modNameBuffer.toString() + "." + item.toString();
                import_next(mod, modNameBuffer, item.toString(), fullName, null);
            }
        }
    }

    /**
     * Import a module by name.
     *
     * @param name the name of the package to import
     * @param top if true, return the top module in the name, otherwise the last
     * @return an imported module (Java or Python)
     */
    public static PyObject importName(String name, boolean top) {
        checkNotFile(name);
        checkName(name);
        ReentrantLock importLock = Py.getSystemState().getImportLock();
        importLock.lock();
        try {
            return import_module_level(name, top, null, null, DEFAULT_LEVEL);
        } finally {
            importLock.unlock();
        }
    }

    /**
     * Import a module by name. This supports the default {@code __import__()} function
     * {@code __builtin__.__import__}. Locks the import system while it operates.
     *
     * @param name the fully-qualified name of the package/module to import
     * @param top if true, return the top module in the name, otherwise the last
     * @param modDict the __dict__ of the importing module (used for name in relative import)
     * @param fromlist list of names being imported
     * @param level 0=absolute, n&gt;0=relative levels to go up, -1=try relative then absolute.
     * @return an imported module (Java or Python)
     */
    public static PyObject importName(String name, boolean top, PyObject modDict,
                                      PyObject fromlist, int level) {
        checkNotFile(name);
        checkName(name);
        ReentrantLock importLock = Py.getSystemState().getImportLock();
        importLock.lock();
        try {
            return import_module_level(name, top, modDict, fromlist, level);
        } finally {
            importLock.unlock();
        }
    }

    /**
     * Called from jython generated code when a statement like "import spam" is executed.
     */
    @Deprecated
    public static PyObject importOne(String mod, PyFrame frame) {
        return importOne(mod, frame, imp.DEFAULT_LEVEL);
    }

    /**
     * Called from jython generated code when a statement like "import spam" is executed.
     */
    public static PyObject importOne(String mod, PyFrame frame, int level) {
        PyObject module =
                __builtin__.__import__(mod, frame.f_globals, frame.getLocals(), Py.None, level);
        return module;
    }

    /**
     * Called from jython generated code when a statement like "import spam as foo" is executed.
     */
    @Deprecated
    public static PyObject importOneAs(String mod, PyFrame frame) {
        return importOneAs(mod, frame, imp.DEFAULT_LEVEL);
    }

    /**
     * Called from jython generated code when a statement like "import spam as foo" is executed.
     */
    public static PyObject importOneAs(String mod, PyFrame frame, int level) {
        PyObject module =
                __builtin__.__import__(mod, frame.f_globals, frame.getLocals(), Py.None, level);
        int dot = mod.indexOf('.');
        while (dot != -1) {
            int dot2 = mod.indexOf('.', dot + 1);
            String name;
            if (dot2 == -1) {
                name = mod.substring(dot + 1);
            } else {
                name = mod.substring(dot + 1, dot2);
            }
            module = module.__getattr__(name);
            dot = dot2;
        }
        return module;
    }

    /**
     * replaced by importFrom with level param. Kept for backwards compatibility.
     *
     * @deprecated use importFrom with level param.
     */
    @Deprecated
    public static PyObject[] importFrom(String mod, String[] names, PyFrame frame) {
        return importFromAs(mod, names, null, frame, DEFAULT_LEVEL);
    }

    /**
     * Called from jython generated code when a statement like "from spam.eggs import foo, bar" is
     * executed.
     */
    public static PyObject[] importFrom(String mod, String[] names, PyFrame frame, int level) {
        return importFromAs(mod, names, null, frame, level);
    }

    /**
     * replaced by importFromAs with level param. Kept for backwards compatibility.
     *
     * @deprecated use importFromAs with level param.
     */
    @Deprecated
    public static PyObject[] importFromAs(String mod, String[] names, PyFrame frame) {
        return importFromAs(mod, names, null, frame, DEFAULT_LEVEL);
    }

    /**
     * Called from jython generated code when a statement like "from spam.eggs import foo as spam"
     * is executed.
     */
    public static PyObject[] importFromAs(String mod, String[] names, String[] asnames,
                                          PyFrame frame, int level) {
        PyObject[] pyNames = new PyObject[names.length];
        for (int i = 0; i < names.length; i++) {
            pyNames[i] = Py.newString(names[i]);
        }

        PyObject module =
                __builtin__.__import__(mod, frame.f_globals, frame.getLocals(),
                        new PyTuple(pyNames), level);
        PyObject[] submods = new PyObject[names.length];
        for (int i = 0; i < names.length; i++) {
            PyObject submod = module.__findattr__(names[i]);
            // XXX: Temporary fix for http://bugs.jython.org/issue1900
            if (submod == null) {
                submod = module.impAttr(names[i]);
            }
            // end temporary fix.

            if (submod == null) {
                throw Py.ImportError("cannot import name " + names[i]);
            }
            submods[i] = submod;
        }
        return submods;
    }

    private final static PyTuple all = new PyTuple(Py.newString('*'));

    /**
     * Called from jython generated code when a statement like "from spam.eggs import *" is
     * executed.
     */
    public static void importAll(String mod, PyFrame frame, int level) {
        PyObject module =
                __builtin__.__import__(mod, frame.f_globals, frame.getLocals(), all, level);
        importAll(module, frame);
    }

    @Deprecated
    public static void importAll(String mod, PyFrame frame) {
        importAll(mod, frame, DEFAULT_LEVEL);
    }

    public static void importAll(PyObject module, PyFrame frame) {
        PyObject names;
        boolean filter = true;
        if (module instanceof PyJavaPackage) {
            names = ((PyJavaPackage)module).fillDir();
        } else {
            PyObject __all__ = module.__findattr__("__all__");
            if (__all__ != null) {
                names = __all__;
                filter = false;
            } else {
                names = module.__dir__();
            }
        }

        loadNames(names, module, frame.getLocals(), filter);
    }

    /**
     * From a module, load the attributes found in <code>names</code> into locals.
     *
     * @param filter if true, if the name starts with an underscore '_' do not add it to locals
     * @param locals the namespace into which names will be loaded
     * @param names the names to load from the module
     * @param module the fully imported module
     */
    private static void loadNames(PyObject names, PyObject module, PyObject locals, boolean filter) {
        for (PyObject name : names.asIterable()) {
            String sname = ((PyString)name).internedString();
            if (filter && sname.startsWith("_")) {
                continue;
            } else {
                try {
                    PyObject value = module.__findattr__(sname);
                    if (value == null) {
                        PyObject nameObj = module.__findattr__("__name__");
                        if (nameObj != null) {
                            String submodName = nameObj.__str__().toString() + '.' + sname;
                            value =
                                    __builtin__
                                            .__import__(submodName, null, null, nonEmptyFromlist);
                        }
                    }
                    locals.__setitem__(sname, value);
                } catch (Exception exc) {
                    continue;
                }
            }
        }
    }

    static PyObject reload(PyModule m) {
        PySystemState sys = Py.getSystemState();
        PyObject modules = sys.modules;
        Map<String, PyModule> modules_reloading = sys.modules_reloading;
        ReentrantLock importLock = Py.getSystemState().getImportLock();
        importLock.lock();
        try {
            return _reload(m, modules, modules_reloading);
        } finally {
            modules_reloading.clear();
            importLock.unlock();
        }
    }

    private static PyObject _reload(PyModule m, PyObject modules,
                                    Map<String, PyModule> modules_reloading) {
        String name = m.__getattr__("__name__").toString().intern();
        PyModule nm = (PyModule)modules.__finditem__(name);
        if (nm == null || !nm.__getattr__("__name__").toString().equals(name)) {
            throw Py.ImportError("reload(): module " + name + " not in sys.modules");
        }
        PyModule existing_module = modules_reloading.get(name);
        if (existing_module != null) {
            // Due to a recursive reload, this module is already being reloaded.
            return existing_module;
        }
        // Since we are already in a re-entrant lock,
        // this test & set is guaranteed to be atomic
        modules_reloading.put(name, nm);

        PyList path = Py.getSystemState().path;
        String modName = name;
        int dot = name.lastIndexOf('.');
        if (dot != -1) {
            String iname = name.substring(0, dot).intern();
            PyObject pkg = modules.__finditem__(iname);
            if (pkg == null) {
                throw Py.ImportError("reload(): parent not in sys.modules");
            }
            path = (PyList)pkg.__getattr__("__path__");
            name = name.substring(dot + 1, name.length()).intern();
        }

        nm.__setattr__("__name__", new PyString(modName)); // FIXME necessary?!
        try {
            PyObject ret = find_module(name, modName, path);
            modules.__setitem__(modName, ret);
            return ret;
        } catch (RuntimeException t) {
            // Need to restore module, due to the semantics of addModule, which removed it
            // Fortunately we are in a module import lock
            modules.__setitem__(modName, nm);
            throw t;
        }
    }

    public static int getAPIVersion() {
        return APIVersion;
    }
}

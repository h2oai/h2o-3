package testngframework;

import java.lang.reflect.Method;
import java.net.*;

import water.util.Log;

/**
 * Creates a node in-process using a separate class loader.
 */
public class NodeContainer extends Thread {
    private final String[] _args;
    private final URLClassLoader _initialClassLoader, _classLoader;

    public NodeContainer(String[] args) {
        super("NodeContainer");
        _args = args;
        _initialClassLoader = (URLClassLoader) Thread.currentThread().getContextClassLoader();
        URL[] _classpath = _initialClassLoader.getURLs();
        _classLoader = new URLClassLoader(_classpath, null);
    }

    public void run() {
        assert Thread.currentThread().getContextClassLoader() == _initialClassLoader;
        Thread.currentThread().setContextClassLoader(_classLoader);

        try {
            Class<?> c = _classLoader.loadClass("water.H2O");
            Method method = c.getMethod("main", String[].class);
            method.setAccessible(true);
            method.invoke(null, (Object) _args);
        } catch( Exception e ) {
            throw Log.throwErr(e);
        } finally {
            Thread.currentThread().setContextClassLoader(_initialClassLoader);
        }
    }
}
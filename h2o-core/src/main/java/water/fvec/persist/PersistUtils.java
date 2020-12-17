package water.fvec.persist;

import water.AutoBuffer;
import water.H2O;
import water.persist.Persist;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;

public class PersistUtils {
    
    public static String sanitizeUri(String uri) {
        if (uri.endsWith("/")) {
            return uri.substring(0, uri.length()-1);
        } else {
            return uri;
        }
    }

    public static <T> T read(URI uri, Reader<T> r) {
        final Persist persist = H2O.getPM().getPersistForURI(uri);
        try (final InputStream inputStream = persist.open(uri.toString())) {
            final AutoBuffer autoBuffer = new AutoBuffer(inputStream);
            T res = r.read(autoBuffer);
            autoBuffer.close();
            return res;
        } catch (IOException e) {
            throw new RuntimeException("Failed to write to " + uri, e);
        }
    }

    public static void write(URI uri, Writer w) {
        final Persist persist = H2O.getPM().getPersistForURI(uri);
        try (final OutputStream outputStream = persist.create(uri.toString(), true)) {
            final AutoBuffer autoBuffer = new AutoBuffer(outputStream, true);
            w.write(autoBuffer);
            autoBuffer.close();
        } catch (IOException e) {
            throw new RuntimeException("Failed to write to " + uri, e);
        }
    }
    
    public static boolean exists(URI uri) {
        final Persist persist = H2O.getPM().getPersistForURI(uri);
        return persist.exists(uri.toString());
    }

    public interface Reader<T> {
        T read(AutoBuffer ab);
    }

    public interface Writer {
        void write(AutoBuffer ab);
    }
}

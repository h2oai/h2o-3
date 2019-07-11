package water.udf;

import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * Loader for custom function providers.
 *
 * A provider provides a way to instantiate given function reference (given as {@link CFuncRef}).
 * It needs to publish {@link CFuncLoader} implementation via Java SPI.
 */
public class CFuncLoaderService {
  public static CFuncLoaderService INSTANCE = new CFuncLoaderService();

  private final ServiceLoader<CFuncLoader> loader;

  public CFuncLoaderService() {
    loader = ServiceLoader.load(CFuncLoader.class);
  }

  synchronized public CFuncLoader getByLang(String lang) {
    Iterator<CFuncLoader> it = loader.iterator();

    while (it.hasNext()) {
      CFuncLoader ul = it.next();
      if (ul.getLang().equals(lang)) {
        return ul;
      }
    }
    return null;
  }
}

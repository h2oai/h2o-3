package water.parser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Service to manage optional implementation of parsers.
 */
public final class ParserService {
  public static ParserService INSTANCE = new ParserService();

  /** Service loader.
   *
   * Based on JavaDoc of SPI: "Instances of this class are not safe for use by multiple concurrent threads." - all usages of the loader
   * are protected by synchronized block.
   */
  private final ServiceLoader<ParserProvider> loader;

  public ParserService() {
    loader = ServiceLoader.load(ParserProvider.class);
  }

  /** Return list of all parser providers sorted based on priority. */
  public List<ParserProvider> getAllProviders() {
    return getAllProviders(true);
  }

  /**
   * Returns all parser providers sorted based on priority if required.
   *
   * @param sort
   * @return
   */
  synchronized public List<ParserProvider> getAllProviders(boolean sort) {
    List<ParserProvider> providers = new ArrayList<>();
    for(ParserProvider pp : loader) {
      providers.add(pp);
    }
    if (sort) {
      Collections.sort(providers, PARSER_PROVIDER_COMPARATOR);
    }
    return providers;
  }

  synchronized public String[] getAllProviderNames(boolean sort) {
    List<ParserProvider> providers = getAllProviders(sort);
    String[] names = new String[providers.size()];
    int i = 0;
    for (ParserProvider pp : providers) {
      names[i++] = pp.info().name();
    }
    return names;
  }

  public ParserProvider getByInfo(ParserInfo info) {
    return getByName(info.name());
  }

  synchronized public ParserProvider getByName(String name) {
    for (ParserProvider pp : loader) {
      if (pp.info().name().equals(name)) {
        return pp;
      }
    }
    return null;
  }

  private static Comparator<ParserProvider> PARSER_PROVIDER_COMPARATOR = new Comparator<ParserProvider>() {
    @Override
    public int compare(ParserProvider o1, ParserProvider o2) {
      int x = o1.info().prior;
      int y = o2.info().prior;
      // Cannot use Integer.compare(int, int) since it is available from Java7 and also cannot
      // use `-` for comparison
      return (x < y) ? -1 : ((x == y) ? 0 : 1);
    }
  };
}

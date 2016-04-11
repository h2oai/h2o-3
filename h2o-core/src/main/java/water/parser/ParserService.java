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
  public static final String[] N = INSTANCE.getAllProviderNames(true);

  private final ServiceLoader<ParserProvider> loader;

  public ParserService() {
    loader = ServiceLoader.load(ParserProvider.class);
  }

  /** Return list of all parser providers sorted based on priority. */
  public Collection<ParserProvider> getAllProviders() {
    return getAllProviders(true);
  }

  /**
   * Returns all parser providers sorted based on priority if required.
   *
   * @param sort
   * @return
   */
  public List<ParserProvider> getAllProviders(boolean sort) {
    List<ParserProvider> providers = new ArrayList<>();
    Iterator<ParserProvider> it = loader.iterator();
    while (it.hasNext()) {
      providers.add(it.next());
    }
    if (sort) {
      Collections.sort(providers, PARSER_PROVIDER_COMPARATOR);
    }
    return providers;
  }

  public String[] getAllProviderNames(boolean sort) {
    List<ParserProvider> providers = getAllProviders(sort);
    String[] names = new String[providers.size()];
    int i = 0;
    for (ParserProvider pp : providers) {
      names[i++] = pp.info().name();
    }
    return names;
  }

  public ParserProvider getByName(String name) {
    Iterator<ParserProvider> it = loader.iterator();
    while (it.hasNext()) {
      ParserProvider pp = it.next();
      if (pp.info().name().equals(name)) {
        return pp;
      }
    }
    return null;
  }

  private static Comparator<ParserProvider> PARSER_PROVIDER_COMPARATOR = new Comparator<ParserProvider>() {
    @Override
    public int compare(ParserProvider o1, ParserProvider o2) {
      return o2.info().prior - o1.info().prior;
    }
  };
}

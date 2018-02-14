package water.persist;

import java.util.ArrayList;
import java.util.List;

abstract class StringCache {
  private static final long TIMEOUT_MILLIS = 60 * 1000;

  private long _lastUpdated = 0;

  private List<String> _cache = new ArrayList<>();

  abstract List<String> update();

  List<String> fetch(String resultPrefix, String filter, int limit) {
    if (System.currentTimeMillis() > _lastUpdated + TIMEOUT_MILLIS) {
      _cache = update();
      _lastUpdated = System.currentTimeMillis();
    }

    int resultCapacity = limit > 0 ? limit : _cache.size();
    List<String> result = new ArrayList<>(resultCapacity);
    for (String entry : _cache) {
      if (result.size() == resultCapacity) {
        break;
      }
      if (entry.contains(filter)) {
        result.add(resultPrefix + entry);
      }
    }
    return result;
  }
}

package hex.segments;

import water.DKV;
import water.Iced;
import water.Key;
import water.util.IcedLong;

class WorkAllocator extends Iced<WorkAllocator> {
  private final Key _counter_key;
  private final long _max_work;

  WorkAllocator(Key counterKey, long maxWork) {
    _counter_key = counterKey;
    _max_work = maxWork;
    DKV.put(_counter_key, new IcedLong(-1));
  }

  long getNextWorkItem() {
    return IcedLong.incrementAndGet(_counter_key);
  }

  long getMaxWork() {
    return _max_work;
  }

}

package ai.h2o.automl;

import water.Iced;
import water.util.ArrayUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class WorkAllocations extends Iced<WorkAllocations> {

  public enum JobType {
    Unknown,
    ModelBuild,
    HyperparamSearch,
    Selection,
  }

  public static class Work extends Iced<Work> {
    String _id;
    Algo _algo;
    JobType _type;
    int _weight;

    Work(String id, Algo algo, JobType type, int weight) {
      this._algo = algo;
      this._type = type;
      this._id = id;
      this._weight = weight;
    }

    public int consume() {
      int consumed = _weight;
      _weight = 0;
      return consumed;
    }
  }

  private boolean frozen;
  private Work[] allocations = new Work[0];

  WorkAllocations allocate(Work work) {
    if (frozen) throw new IllegalStateException("Can not allocate new work.");
    allocations = ArrayUtils.append(allocations, work);
    return this;
  }

  WorkAllocations freeze() {
    frozen = true;
    return this;
  }

  void remove(Algo algo) {
    if (frozen) throw new IllegalStateException("Can not modify allocations.");
    List<Work> filtered = new ArrayList<>(allocations.length);
    for (Work alloc : allocations) {
      if (!algo.equals(alloc._algo)) {
        filtered.add(alloc);
      }
    }
    allocations = filtered.toArray(new Work[0]);
  }

  public Work getAllocation(String id, Algo algo) {
    for (Work alloc : allocations) {
      if (alloc._algo == algo && alloc._id.equals(id)) return alloc;
    }
    return null;
  }

  public Work[] getAllocations(Predicate<Work> predicate) {
    return Stream.of(allocations)
            .filter(predicate)
            .toArray(Work[]::new);
  }

  private int sum(Work[] workItems) {
    int tot = 0;
    for (Work item : workItems) {
      tot += item._weight;
    }
    return tot;
  }

  int remainingWork() {
    return sum(allocations);
  }

  int remainingWork(Predicate<Work> predicate) {
    return sum(getAllocations(predicate));
  }

  float remainingWorkRatio(Work work) {
    return (float) work._weight / remainingWork();
  }

  float remainingWorkRatio(Work work, Predicate<Work> predicate) {
    return (float) work._weight / remainingWork(predicate);
  }

}

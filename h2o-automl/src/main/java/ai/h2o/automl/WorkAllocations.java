package ai.h2o.automl;

import org.apache.commons.lang.builder.StandardToStringStyle;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import water.Iced;
import water.util.ArrayUtils;

import java.util.ArrayList;
import java.util.Arrays;
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
    IAlgo _algo;
    JobType _type;
    int _weight;
    int _priorityGroup;

    Work(String id, IAlgo algo, JobType type, int weight, int priorityGroup) {
      this._algo = algo;
      this._type = type;
      this._id = id;
      this._weight = weight;
      this._priorityGroup = priorityGroup;
    }

    // Used by tests
    Work(String id, IAlgo algo, JobType type, int weight) {
      this(id, algo, type, weight, Integer.MAX_VALUE);
    }

    public int consume() {
      int consumed = _weight;
      _weight = 0;
      return consumed;
    }

    @Override
    public String toString() {
      final StringBuilder sb = new StringBuilder("Work{")
              .append(_id).append(", ")
              .append(_algo).append(", ")
              .append(_type).append(", ")
              .append("weight=").append(_weight).append(", ")
              .append("priority_group=").append(_priorityGroup)
              .append('}');
      return sb.toString();
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

  void remove(IAlgo algo) {
    if (frozen) throw new IllegalStateException("Can not modify allocations.");
    List<Work> filtered = new ArrayList<>(allocations.length);
    for (Work alloc : allocations) {
      if (!algo.name().equals(alloc._algo.name())) {
        filtered.add(alloc);
      }
    }
    allocations = filtered.toArray(new Work[0]);
  }

  public Work getAllocation(String id, IAlgo algo) {
    for (Work alloc : allocations) {
      if (alloc._algo.name().equals(algo.name()) && alloc._id.equals(id)) return alloc;
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

  @Override
  public String toString() {
    return Arrays.toString(allocations);
  }
}

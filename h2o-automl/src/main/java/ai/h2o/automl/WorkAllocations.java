package ai.h2o.automl;

import water.Iced;
import water.util.ArrayUtils;
import water.util.fp.Predicate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class WorkAllocations extends Iced<WorkAllocations> {

  public enum JobType {
    Unknown,
    ModelBuild,
    HyperparamSearch
  }

  public static class Work extends Iced<Work> {
    Algo algo;
    int count;
    JobType type;
    int share;

    Work(Algo algo, int count, JobType type, int share) {
      this.algo = algo;
      this.count = count;
      this.type = type;
      this.share = share;
    }

    public int consume() {
      return consume(1);
    }

    public int consume(int amount) {
      int c = Math.min(this.count, amount);
      this.count -= c;
      return c * this.share;
    }

    public int consumeAll() {
      return consume(Integer.MAX_VALUE);
    }
  }

  private boolean canAllocate = true;
  private Work[] allocations = new Work[0];

  WorkAllocations allocate(Algo algo, int count, JobType type, int workShare) {
    if (!canAllocate) throw new IllegalStateException("Can't allocate new work.");

    allocations = ArrayUtils.append(allocations, new Work(algo, count, type, workShare));
    return this;
  }

  void end() {
    canAllocate = false;
  }

  void remove(Algo algo) {
    List<Work> filtered = new ArrayList<>(allocations.length);
    for (Work alloc : allocations) {
      if (!algo.equals(alloc.algo)) {
        filtered.add(alloc);
      }
    }
    allocations = filtered.toArray(new Work[0]);
  }

  public Work getAllocation(Algo algo, JobType workType) {
    for (Work alloc : allocations) {
      if (alloc.algo == algo && alloc.type == workType) return alloc;
    }
    return null;
  }

  private int sum(Work[] workItems) {
    int tot = 0;
    for (Work item : workItems) {
      tot += (item.count * item.share);
    }
    return tot;
  }

  int remainingWork() {
    return sum(allocations);
  }

  int remainingWork(Predicate<Work> predicate) {
    List<Work> selected = predicate.filter(Arrays.asList(allocations));
    return sum(selected.toArray(new Work[0]));
  }

  float remainingWorkRatio(Work work) {
    return (float) work.share / remainingWork();
  }

  float remainingWorkRatio(Work work, Predicate<Work> predicate) {
    return (float) work.share / remainingWork(predicate);
  }

}

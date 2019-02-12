package water.hive;

import org.apache.hadoop.hive.metastore.api.Partition;
import org.apache.hadoop.hive.metastore.api.Table;
import water.DKV;
import water.H2O;
import water.Job;
import water.Key;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.Rapids;
import water.rapids.Val;

import java.util.List;

import static water.fvec.Vec.T_STR;
import static water.fvec.Vec.makeZero;

public class PartitionFrameJoiner extends H2O.H2OCountedCompleter {

  private final Job<Frame> _job;
  private final Table _table;
  private final List<Partition> _partitions;
  private final String _targetFrame;
  private final List<Job<Frame>> _parseJobs;

  public PartitionFrameJoiner(
      Job<Frame> job, Table table, List<Partition> partitions, String targetFrame, List<Job<Frame>> parseJobs
  ) {
    _job = job;
    _table = table;
    _partitions = partitions;
    _targetFrame = targetFrame;
    _parseJobs = parseJobs;
  }

  @Override
  public void compute2() {
    int keyCount = _table.getPartitionKeysSize();
    StringBuilder partKeys = new StringBuilder();
    for (int partIndex = 0; partIndex < _partitions.size(); partIndex++) {
      Partition part = _partitions.get(partIndex);
      Frame partitionFrame = _parseJobs.get(partIndex).get();
      partKeys.append(" ").append(partitionFrame._key);
      long rows = partitionFrame.numRows();
      for (int keyIndex = 0; keyIndex < keyCount; keyIndex++) {
        String partitionValue = part.getValues().get(keyIndex);
        Vec vec = makeVecWithValue(rows, partitionValue);
        partitionFrame.add(_table.getPartitionKeys().get(keyIndex).getName(), vec);
      }
      _job.update(1);
    }

    String tree = "(rbind" + partKeys + ")";
    Val val = Rapids.exec(tree);
    Frame merged = val.getFrame();
    merged._key = Key.make(_targetFrame);
    DKV.put(merged);
    for (Job<Frame> parseJob : _parseJobs) {
      DKV.remove(parseJob._result);
    }
    _job.update(1);
    tryComplete();
  }

  private Vec makeVecWithValue(long rows, final String value) {
    Vec zeroes = makeZero(rows, T_STR);
    return new MRTask() {
      @Override
      public void map(Chunk[] cs) {
        for (Chunk c : cs)
          for (int r = 0; r < c._len; r++)
            c.set(r, value);
      }
    }.doAll(zeroes)._fr.vecs()[0];
  }

}

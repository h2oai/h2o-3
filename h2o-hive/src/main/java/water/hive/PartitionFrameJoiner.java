package water.hive;

import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.Rapids;
import water.rapids.Val;

import java.util.List;

import static water.fvec.Vec.T_STR;
import static water.fvec.Vec.makeZero;

public class PartitionFrameJoiner extends H2O.H2OCountedCompleter<PartitionFrameJoiner> {

  private final Job<Frame> _job;
  private final HiveMetaData.Table _table;
  private final List<HiveMetaData.Partition> _partitions;
  private final String _targetFrame;
  private final List<Job<Frame>> _parseJobs;

  public PartitionFrameJoiner(
      Job<Frame> job, HiveMetaData.Table table, List<HiveMetaData.Partition> partitions, String targetFrame, List<Job<Frame>> parseJobs
  ) {
    _job = job;
    _table = table;
    _partitions = partitions;
    _targetFrame = targetFrame;
    _parseJobs = parseJobs;
  }

  @Override
  public void compute2() {
    int keyCount = _table.getPartitionKeys().size();
    StringBuilder partKeys = new StringBuilder();
    for (Job<Frame> job : _parseJobs) {
      Frame partitionFrame = job.get();
      String partKey = partitionFrame._key.toString();
      String[] keySplit = partKey.split("_");
      int partIndex = Integer.parseInt(keySplit[keySplit.length - 1]);
      HiveMetaData.Partition part = _partitions.get(partIndex);
      partKeys.append(" ").append(partKey);
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

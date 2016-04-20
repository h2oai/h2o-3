package hex.util;

import hex.DataInfo;
import water.*;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;

import java.util.*;

public class Aggregator extends Keyed<Aggregator> {
  // INPUT
  public Frame _input_frame;
  public double _radius_scale=1.0;
  public Key<Frame> _output_frame_key;
  public boolean _keep_member_indices;

  // OUTPUT
  public Frame _output_frame;
  public double[][] _exemplars;
  public long[] _counts;

  // STATE
  private long[][] _member_indices;
  private Key _diKey;

  public Aggregator() {}

  public Frame getMembersForExemplar(Key<Frame> frameKey, int exemplarId) {
    return createFrameFromRawValues(frameKey, ((DataInfo)(_diKey.get()))._adaptedFrame.names(), this.collectMembers(exemplarId), null);
  }

  @Override
  protected Futures remove_impl(Futures fs) {
    _diKey.remove();
    return super.remove_impl(fs);
  }

  public Aggregator(Frame input, Key<Frame> output, double radiusScale, boolean keepMemberIndices) {
    _input_frame = input;
    _output_frame_key = output;
    _radius_scale = radiusScale;
    _keep_member_indices = keepMemberIndices;
  }

  public static Frame createFrameFromRawValues(Key<Frame> outputFrameKey, String[] names, double[][] ex, long[] counts) {
    int nrows = ex.length;
    Vec[] vecs = new Vec[ex[0].length+(counts==null?0:1)];
    int ncol = vecs.length;
    for (int c=0; c<ncol; ++c) {
      vecs[c] = Vec.makeZero(nrows);
      Vec.Writer vw = vecs[c].open();
      if (c==ncol-1 && counts!=null) {
        for (int r = 0; r < nrows; ++r)
          vw.set(r, counts[r]);
      } else {
        for (int r = 0; r < nrows; ++r)
          vw.set(r, ex[r][c]);
      }
      vw.close();
    }
    if (counts!=null) {
      names = Arrays.copyOf(names, names.length + 1);
      names[names.length - 1] = "counts";
    }

    Frame f = new Frame(outputFrameKey, names, vecs); //all numeric
    DKV.put(f);
    return f;
  }

  public double[][] collectMembers(int whichExemplar) {
    long[] memberindices = _member_indices[whichExemplar];
    Set<Key> chunksFetched = new TreeSet<>();
    DataInfo di = DKV.getGet(_diKey);
    DataInfo.Row row = di.newDenseRow();
    int nrows = memberindices.length;
    int ncols = row.numVals.length;
    double[][] res = new double[nrows][ncols];
    //TODO: multi-threading
    int count=0;
    for (long r : memberindices) {
      row = di.extractDenseRow(di._adaptedFrame.vecs(), r, row);
      res[count++] = Arrays.copyOf(row.numVals, row.numVals.length);

      // for cache cleanup
      int ckIdx = _input_frame.vecs()[0].elem2ChunkIdx(r);
      chunksFetched.add(_input_frame.vecs()[0].chunkKey(ckIdx));
    }
    for (Key ck : chunksFetched) {
      Value v = DKV.get(ck);
      if (!ck.home()) {
        v.freeMem();
      }
    }
    return res;
  }

  public Aggregator execImpl() {
    if (_input_frame == null)     throw new H2OIllegalArgumentException("Dataset not found");

    //_dataset -> replace all categoricals with k pca components

    DataInfo di = new DataInfo(_input_frame, null, true, DataInfo.TransformType.NORMALIZE, false, false, false);
    DKV.put(di);
    _diKey = di._key;
    final double radius = _radius_scale * .1/Math.pow(Math.log(_input_frame.numRows()), 1.0 / _input_frame.numCols());
    AggregateTask aggTask = new AggregateTask(di._key, radius, _keep_member_indices).doAll(di._adaptedFrame);
    _exemplars = aggTask._exemplars;
    _counts = aggTask._counts;
    _member_indices = aggTask._memberIndices;
    if (_output_frame_key!=null)
      _output_frame = createFrameFromRawValues(_output_frame_key, di._adaptedFrame.names(), _exemplars, _counts);
    return this;
  }

  private static class AggregateTask extends MRTask<AggregateTask> {
    //INPUT
    final double _delta;
    final DataInfo _dataInfo;
    final boolean _keepMemberIndices;

    // OUTPUT
    double[][] _exemplars;
    long[] _counts;
    long[][] _memberIndices;

    public AggregateTask(Key<DataInfo> dataInfoKey, double radius, boolean keepMemberIndices) {
      _delta = radius*radius;
      _dataInfo = DKV.getGet(dataInfoKey);
      _keepMemberIndices = keepMemberIndices;
    }

    @Override
    public void map(Chunk[] chks) {
      List<double[]> exemplars = new ArrayList<>();
      List<Long> counts = new ArrayList<>();
      List<List<Long>> memberIndices = new ArrayList<>();

      final int nCols = chks.length;

      // loop over rows
      DataInfo.Row row = _dataInfo.newDenseRow();
      for (int r=0; r<chks[0]._len; ++r) {
        row = _dataInfo.extractDenseRow(chks, r, row);
        double[] data = Arrays.copyOf(row.numVals, row.numVals.length);
//        // fill a data row
//        double[] data = new double[chks.length];
//        for (int c = 0; c< nCols; ++c) {
//          data[c] = chks[c].atd(r);
//        }
        if (r==0) {
          exemplars.add(data);
          counts.add(1L);
          if (_keepMemberIndices) {
            memberIndices.add(new ArrayList<Long>());
            memberIndices.get(0).add(0L);
          }
        }
        else {
          /* find closest exemplar to this case */
          Long rowIndex = chks[0].start()+r;
          double distanceToNearestExemplar = Double.POSITIVE_INFINITY;
          Iterator<double[]> it = exemplars.iterator();
          int closestExemplarIndex = 0;
          int index = 0;
          while (it.hasNext()) {
            double[] e = it.next();
            double d = squaredEuclideanDistance(e, data, nCols);
            if (d < distanceToNearestExemplar) {
              distanceToNearestExemplar = d;
              closestExemplarIndex = index;
            }
            /* do not need to look further even if some other exemplar is closer */
            if (distanceToNearestExemplar < _delta)
              break;
            index++;
          }
          /* found a close exemplar, so add to list */
          if (distanceToNearestExemplar < _delta) {
            Long count = counts.get(closestExemplarIndex);
            counts.set(closestExemplarIndex, count + 1);
            if (_keepMemberIndices) {
              memberIndices.get(closestExemplarIndex).add(rowIndex);
            }
          } else {
            /* otherwise, assign a new exemplar */
            exemplars.add(data);
            counts.add(1L);
            if (_keepMemberIndices) {
              ArrayList<Long> member = new ArrayList<>();
              member.add(rowIndex);
              memberIndices.add(member);
            }
          }
        }
        // populate output primitive arrays
        Object[] exemplarArray = exemplars.toArray();
        _exemplars = new double[exemplars.size()][];
        for (int i = 0; i < exemplars.size(); i++) {
          _exemplars[i] = (double[]) exemplarArray[i];
        }
        Object[] countsArray = counts.toArray();
        _counts = new long[counts.size()];
        for (int i = 0; i < counts.size(); i++) {
          _counts[i] = (Long) countsArray[i];
        }
        if (_keepMemberIndices) {
          _memberIndices = new long[_exemplars.length][];
          for (int i = 0; i < _exemplars.length; i++) {
            _memberIndices[i] = new long[memberIndices.get(i).size()];
            for (int j = 0; j < _memberIndices[i].length; ++j) {
              _memberIndices[i][j] = memberIndices.get(i).get(j);
            }
          }
        }
      }
      assert(_exemplars.length <= chks[0].len());
      assert(_counts.length == _exemplars.length);
      if (_keepMemberIndices) {
        assert (_memberIndices.length == _exemplars.length);
      }
      long sum=0;
      for (int i=0; i<_counts.length;++i) {
        if (_keepMemberIndices) {
          assert (_counts[i] == _memberIndices[i].length);
        }
        sum += _counts[i];
      }
      assert(sum <= chks[0].len());
    }

    @Override
    public void reduce(AggregateTask mrt) {
      if (mrt == null  || mrt._exemplars == null) return;

      // reduce mrt into this
      double[][] exemplars = mrt._exemplars;
      long[] counts = mrt._counts;
      long[][] memberIndices = mrt._memberIndices;
      long localCounts = 0;
      for (long c : _counts) localCounts += c;
      long remoteCounts = 0;
      for (long c : counts) remoteCounts += c;

      // loop over other task's exemplars
      for (int r=0; r<exemplars.length; ++r) {
        double[] data = exemplars[r];
        double distanceToNearestExemplar = Double.POSITIVE_INFINITY;
        int closestExemplarIndex = 0;
        // loop over my exemplars (which might grow below)
        for (int it=0; it<_exemplars.length; ++it) {
          double[] e = _exemplars[it];
          double d = squaredEuclideanDistance(e, data, data.length);
          if (d < distanceToNearestExemplar) {
            distanceToNearestExemplar = d;
            closestExemplarIndex = it;
          }
           /* do not need to look further even if some other exemplar is closer */
          if (distanceToNearestExemplar < _delta)
            break;
        }
        if (distanceToNearestExemplar < _delta) {
          // add remote exemplar counts/indices to one of my exemplars that are close enough
          _counts[closestExemplarIndex]+=counts[r];
          if (_keepMemberIndices) {
            long[] newIndices = new long[_memberIndices[closestExemplarIndex].length + memberIndices[r].length];
            System.arraycopy(_memberIndices[closestExemplarIndex], 0, newIndices, 0, _memberIndices[closestExemplarIndex].length);
            System.arraycopy(memberIndices[r], 0, newIndices, _memberIndices[closestExemplarIndex].length, memberIndices[r].length);
            _memberIndices[closestExemplarIndex] = newIndices;
          }
        } else {
          // append remote AggregateTasks' exemplars to my exemplars
          double[][] newExemplars = new double[_exemplars.length+1][_exemplars[0].length];
          for (int i=0;i<_exemplars.length;++i)
            newExemplars[i] = _exemplars[i];
          newExemplars[_exemplars.length] = exemplars[r];
          _exemplars = newExemplars;
          exemplars[r] = null;

          long[] newCounts = new long[_counts.length+1];
          System.arraycopy(_counts, 0, newCounts, 0, _counts.length);
          newCounts[newCounts.length-1] = counts[r];
          _counts = newCounts;

          if (_keepMemberIndices) {
            long[][] newMemberIndices = new long[_memberIndices.length + 1][];
            for (int i = 0; i < _memberIndices.length; ++i)
              newMemberIndices[i] = _memberIndices[i];
            newMemberIndices[_memberIndices.length] = memberIndices[r];
            _memberIndices = newMemberIndices;
            memberIndices[r] = null;
          }
        }
      }
      mrt._exemplars = null;
      mrt._counts = null;
      mrt._memberIndices = null;

      assert(_exemplars.length <= localCounts + remoteCounts);
      assert(_counts.length == _exemplars.length);
      if (_keepMemberIndices) {
        assert (_memberIndices.length == _exemplars.length);
      }
      long sum=0;
      for (int i=0; i<_counts.length;++i) {
        if (_keepMemberIndices) {
          assert (_counts[i] == _memberIndices[i].length);
        }
        sum += _counts[i];
      }
      assert(sum == localCounts + remoteCounts);
    }

    @Override
    protected void postGlobal() {
      if (_keepMemberIndices) {
        for (int i=0; i<_memberIndices.length;++i) {
          Arrays.sort(_memberIndices[i]);
        }
      }
    }

    private static double squaredEuclideanDistance(double[] e1, double[] e2, int nCols) {
      double sum = 0;
      int n = 0;
      // TODO: unroll into 4+ partial sums
      for (int j = 0; j < nCols; j++) {
        double d1 = e1[j];
        double d2 = e2[j];
        if (!isMissing(d1) && !isMissing(d2)) {
          sum += (d1 - d2) * (d1 - d2);
          n++;
        }
      }
      sum *= (double) nCols / n;
      return sum;
    }

    private static boolean isMissing(double x) {
      return Double.isNaN(x);
    }

  }
}

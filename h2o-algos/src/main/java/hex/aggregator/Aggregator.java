package hex.aggregator;

import hex.DataInfo;

import hex.ModelBuilder;
import hex.ModelCategory;

import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class Aggregator extends ModelBuilder<AggregatorModel,AggregatorModel.AggregatorParameters,AggregatorModel.AggregatorOutput> {
  // Number of columns in training set (p)
  private transient int _ncolExp;    // With categoricals expanded into 0/1 indicator cols
  @Override protected AggregatorDriver trainModelImpl() { return new AggregatorDriver(); }
  @Override public ModelCategory[] can_build() { return new ModelCategory[]{ ModelCategory.Clustering }; }

  // Called from an http request
  public Aggregator(AggregatorModel.AggregatorParameters parms) { super(parms); init(false); }
  public Aggregator(boolean startup_once) { super(new AggregatorModel.AggregatorParameters(),startup_once); }

  @Override
  public void init(boolean expensive) {
    super.init(expensive);
  }

  class AggregatorDriver extends Driver {

    // Main worker thread
    @Override
    public void compute2() {
      AggregatorModel model = null;
      DataInfo dinfo = null;

      try {
        Scope.enter();
        init(true);   // Initialize parameters
        _parms.read_lock_frames(_job); // Fetch & read-lock input frames
        if (error_count() > 0) throw new IllegalArgumentException("Found validation errors: " + validationErrors());

        // The model to be built
        model = new AggregatorModel(dest(), _parms, new AggregatorModel.AggregatorOutput(Aggregator.this));
        model.delete_and_lock(_job);

        //TODO -> replace all categoricals with k pca components in _train (create new FrameUtils)

        _job.update(1, "Preprocessing data.");
        DataInfo di = new DataInfo(_train, null, true, _parms._transform, false, false, false);
        DKV.put(di);
        model._diKey = di._key;
        final double radius = _parms._radius_scale * .1/Math.pow(Math.log(di._adaptedFrame.numRows()), 1.0 / di._adaptedFrame.numCols());
        _job.update(1, "Starting aggregation.");
        AggregateTask aggTask = new AggregateTask(di._key, radius, _parms._keep_member_indices).doAll(di._adaptedFrame);
        model._exemplars = aggTask._exemplars;
        model._counts = aggTask._counts;
        model._member_indices = aggTask._memberIndices;

        _job.update(1, "Creating output frame.");
        model._output._output_frame = AggregatorModel.createFrameFromRawValues(
                Key.<Frame>make("aggregated_" + _parms._train.toString() + "_by_" + model._key.toString()),
                di._adaptedFrame.names(), model._exemplars, model._counts)._key;
        Key<Vec>[] keep = new Key[(model._output._output_frame.get()).vecs().length];
        for (int i=0; i<keep.length; ++i)
          keep[i] = model._output ._output_frame.get().vec(i)._key;
        Scope.untrack(keep);

        // At the end: validation scoring (no need to gather scoring history)
        _job.update(1, "Done.");
        model.update(_job);
      } finally {
        _parms.read_unlock_frames(_job);
        if (model != null) model.unlock(_job);
        if (dinfo != null) dinfo.remove();
        Scope.exit();
      }
      tryComplete();
    }
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

package hex.aggregator;

import hex.DataInfo;

import hex.ModelBuilder;
import hex.ModelCategory;

import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.Log;

import java.io.Serializable;
import java.util.*;

public class Aggregator extends ModelBuilder<AggregatorModel,AggregatorModel.AggregatorParameters,AggregatorModel.AggregatorOutput> {

  public static class Exemplar extends Iced<Exemplar> {
    Exemplar(double[] d, long id) { data=d; gid=id; }
    final double[] data;
    final long gid;
  }

  // Number of columns in training set (p)
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

        // Add workspace vector for exemplar assignment
        Vec[] vecs = Arrays.copyOf(di._adaptedFrame.vecs(), di._adaptedFrame.vecs().length+1);
        Vec assignment = vecs[vecs.length-1] = di._adaptedFrame.anyVec().makeZero();

        _job.update(1, "Starting aggregation.");
        AggregateTask aggTask = new AggregateTask(di._key, radius).doAll(vecs);
        new RenumberTask(aggTask._mapping).doAll(assignment);
        model._exemplars = aggTask._exemplars;
        model._counts = aggTask._counts;
        model._exemplar_assignment_vec_key = assignment._key;

        if (false) {
          // BEGIN DEBUGGING
          long sum = 0;
          for (long l : model._counts) sum += l;
          assert (sum == di._adaptedFrame.numRows());
          final long[] exemplarGIDs = new long[model._counts.length];
          for (int i = 0; i < model._exemplars.length; ++i)
            exemplarGIDs[i] = model._exemplars[i].gid;
          long[] counts = new long[model._exemplars.length];
          for (int i = 0; i < di._adaptedFrame.numRows(); ++i) {
            long ass = assignment.at8(i);
            for (int j = 0; j < exemplarGIDs.length; ++j) {
              if (exemplarGIDs[j] == ass) {
                counts[j]++;
                break;
              }
            }
          }
          sum = 0;
          for (long l : counts) sum += l;
          assert (sum == di._adaptedFrame.numRows());

          for (int i = 0; i < counts.length; ++i) {
            assert (counts[i] == model._counts[i]);
          }
          // END DEBUGGING
        }

        _job.update(1, "Creating output frame.");
        model._output._output_frame = AggregatorModel.createFrameFromRawValues(
                Key.<Frame>make("aggregated_" + _parms._train.toString() + "_by_" + model._key.toString()),
                di._adaptedFrame.names(), model._exemplars, model._counts)._key;
        Key<Vec>[] keep = new Key[(model._output._output_frame.get()).vecs().length];
        for (int i=0; i<keep.length; ++i)
          keep[i] = model._output ._output_frame.get().vec(i)._key;
        Scope.untrack(keep); //keep aggregated output frame
        Scope.untrack(new Key[]{model._exemplar_assignment_vec_key, model._diKey}); //keep this too

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

    // OUTPUT
    Exemplar[] _exemplars;
    long[] _counts;

    static class MyPair extends Iced<MyPair> implements Comparable<MyPair> {
      long first;
      long second;
      public MyPair(long f, long s) { first=f; second=s; }

      @Override
      public int compareTo(MyPair o) {
        if (first < o.first) return -1;
        if (first == o.first) return 0;
        return 1;
      }
    }

    // WORKSPACE
    static class GIDMapping extends Iced<GIDMapping> {
      MyPair[] pairSet;
      int len;
      int capacity;
      GIDMapping() {
        capacity=32;
        len=0;
        pairSet = new MyPair[capacity];
      }

      void set(long from, long to) {
        for (int i=0;i<len;++i) {
          MyPair p = pairSet[i];
//          assert (p.first != from);
          if (p.second == from) {
            p.second = to;
          }
        }
        MyPair p = new MyPair(from, to);
        if (len==capacity) {
          capacity*=2;
          pairSet = Arrays.copyOf(pairSet, capacity);
        }
        pairSet[len++]=p;
      }

      long[][] unsortedList() {
        long[][] li = new long[2][len];
        MyPair[] pl = pairSet;
        for (int i=0;i<len;++i) {
          li[0][i] = pl[i].first;
          li[1][i] = pl[i].second;
        }
        return li;
      }
    }

    GIDMapping _mapping;

    public AggregateTask(Key<DataInfo> dataInfoKey, double radius) {
      _delta = radius*radius;
      _dataInfo = DKV.getGet(dataInfoKey);
    }

    @Override
    public void map(Chunk[] chks) {
      _mapping = new GIDMapping();
      List<Exemplar> exemplars = new ArrayList<>();
      List<Long> counts = new ArrayList<>();

      Chunk[] dataChks = Arrays.copyOf(chks, chks.length-1);
      Chunk assignmentChk = chks[chks.length-1];

      final int nCols = dataChks.length;

      // loop over rows
      DataInfo.Row row = _dataInfo.newDenseRow(); //shared _dataInfo - faster, no writes
      for (int r=0; r<chks[0]._len; ++r) {
        long rowIndex = chks[0].start()+r;
        row = _dataInfo.extractDenseRow(dataChks, r, row);
        double[] data = Arrays.copyOf(row.numVals, row.numVals.length);
        if (r==0) {
          Exemplar ex = new Exemplar(data, rowIndex);
          exemplars.add(ex);
          counts.add(1L);
          assignmentChk.set(r, ex.gid);
        } else {
          /* find closest exemplar to this case */
          double distanceToNearestExemplar = Double.POSITIVE_INFINITY;
          Iterator<Exemplar> it = exemplars.iterator();
          int closestExemplarIndex = 0;
          int index = 0;
          long gid=-1;
          while (it.hasNext()) {
            Exemplar e = it.next();
            double d = squaredEuclideanDistance(e.data, data, nCols);
            if (d < distanceToNearestExemplar) {
              distanceToNearestExemplar = d;
              closestExemplarIndex = index;
              gid = e.gid;
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
            assignmentChk.set(r, gid);
          } else {
            /* otherwise, assign a new exemplar */
            Exemplar ex = new Exemplar(data, rowIndex);
            exemplars.add(ex);
            counts.add(1L);
            assignmentChk.set(r, rowIndex); //assign to self
          }
        }
      }
      // populate output primitive arrays
      _exemplars = exemplars.toArray(new Exemplar[0]);

      Object[] countsArray = counts.toArray();
      _counts = new long[counts.size()];
      for (int i = 0; i < counts.size(); i++) {
        _counts[i] = (Long) countsArray[i];
      }
      assert(_exemplars.length <= chks[0].len());
      assert(_counts.length == _exemplars.length);
      long sum=0;
      for (int i=0; i<_counts.length;++i) {
        sum += _counts[i];
      }
      assert(sum <= chks[0].len());
//      for (Exemplar ex : _exemplars) {
//        Log.info("Exemplar in map: " + ex.gid);
//      }
    }

    @Override
    public void reduce(AggregateTask mrt) {
      for (int i=0; i<mrt._mapping.len; ++i)
        _mapping.set(mrt._mapping.pairSet[i].first, mrt._mapping.pairSet[i].second);
      // reduce mrt into this
      Exemplar[] exemplars = mrt._exemplars;
      long[] counts = mrt._counts;
      long localCounts = 0;
      for (long c : _counts) localCounts += c;
      long remoteCounts = 0;
      for (long c : counts) remoteCounts += c;

      // loop over other task's exemplars
      for (int r=0; r<exemplars.length; ++r) {
        double[] data = exemplars[r].data;
        double distanceToNearestExemplar = Double.POSITIVE_INFINITY;
        int closestExemplarIndex = 0;
        // loop over my exemplars (which might grow below)
        for (int it=0; it<_exemplars.length; ++it) {
          double[] e = _exemplars[it].data;
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

//          Log.info("Reduce: Reassigning " + counts[r] + " rows from " + exemplars[r].gid + " to " + _exemplars[closestExemplarIndex].gid);
          _mapping.set(exemplars[r].gid, _exemplars[closestExemplarIndex].gid);
        } else {
          // append this remote exemplar to my local exemplars, as a new exemplar
          Exemplar[] newExemplars = Arrays.copyOf(_exemplars, _exemplars.length+1);
          newExemplars[_exemplars.length] = exemplars[r];
          _exemplars = newExemplars;
          exemplars[r] = null;

          long[] newCounts = Arrays.copyOf(_counts, _counts.length+1);
          newCounts[_counts.length] = counts[r];
          _counts = newCounts;
        }
      }
      mrt._exemplars = null;
      mrt._counts = null;

      assert(_exemplars.length <= localCounts + remoteCounts);
      assert(_counts.length == _exemplars.length);
      long sum=0;
      for (int i=0; i<_counts.length;++i) {
        sum += _counts[i];
      }
      assert(sum == localCounts + remoteCounts);
      assert(_exemplars != null);
      assert(_counts != null);
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

  private static class RenumberTask extends MRTask<RenumberTask> {
    final long[][] _map;
    public RenumberTask(AggregateTask.GIDMapping mapping) { _map = mapping.unsortedList(); }
    @Override
    public void map(Chunk c) {
      for (int i=0;i<c._len;++i) {
        long old = c.at8(i);
        //int pos=Arrays.binarySearch(_map[0], old);
        int pos = ArrayUtils.find(_map[0], old);
        if (pos>=0) {
          long newVal =_map[1][pos];
          c.set(i, newVal);
        }
      }

    }
  }
}

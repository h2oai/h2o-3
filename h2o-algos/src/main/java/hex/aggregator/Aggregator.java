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
    Exemplar deepClone() { return new AutoBuffer().put(this).flipForReading().get(); }
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

      DataInfo di = null;
      try {
        init(true);   // Initialize parameters
        _parms.read_lock_frames(_job); // Fetch & read-lock input frames
        if (error_count() > 0) throw new IllegalArgumentException("Found validation errors: " + validationErrors());

        // The model to be built
        model = new AggregatorModel(dest(), _parms, new AggregatorModel.AggregatorOutput(Aggregator.this));
        model.delete_and_lock(_job);

        //TODO -> replace all categoricals with k pca components in _train (create new FrameUtils)
        Frame orig = train(); //this has ignored columns removed etc.

        _job.update(1, "Preprocessing data.");
        di = new DataInfo(orig, null, true, _parms._transform, false, false, false);
        DKV.put(di);
        final double radius = _parms._radius_scale * .1/Math.pow(Math.log(orig.numRows()), 1.0 / orig.numCols());

        // Add workspace vector for exemplar assignment
        Vec[] vecs = Arrays.copyOf(orig.vecs(), orig.vecs().length+1);
        Vec assignment = vecs[vecs.length-1] = orig.anyVec().makeZero();

        _job.update(1, "Starting aggregation.");
        AggregateTask aggTask = new AggregateTask(di._key, radius).doAll(vecs);

        _job.update(1, "Aggregating exemplar assignments.");
        new RenumberTask(aggTask._mapping).doAll(assignment);

        // Populate model output state
        model._exemplars = aggTask._exemplars;
        model._counts = aggTask._counts;
        model._exemplar_assignment_vec_key = assignment._key;
        model._output._output_frame = Key.make("aggregated_" + _parms._train.toString() + "_by_" + model._key);

        _job.update(1, "Creating output frame.");
        model.createFrameOfExemplars(model._output._output_frame);

        _job.update(1, "Done.");
        model.update(_job);
      } finally {
        _parms.read_unlock_frames(_job);
        if (model != null) model.unlock(_job);
        if (di!=null) di.remove();
      }
      tryComplete();
    }
  }

  private static class AggregateTask extends MRTask<AggregateTask> {
    //INPUT
    final double _delta;
    final Key _dataInfoKey;

    // OUTPUT
    Exemplar[] _exemplars;
    long[] _counts;

    static class MyPair extends Iced<MyPair> implements Comparable<MyPair> {
      long first;
      long second;
      public MyPair(long f, long s) { first=f; second=s; }
      public MyPair(){}

      @Override
      public int compareTo(MyPair o) {
        if (first < o.first) return -1;
        if (first == o.first) return 0;
        return 1;
      }
    }

    // WORKSPACE
    static private class GIDMapping extends Iced<GIDMapping> {
      MyPair[] pairSet;
      int len;
      int capacity;
      public GIDMapping() {
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
      _dataInfoKey = dataInfoKey;
    }

    @Override
    public void map(Chunk[] chks) {
      _mapping = new GIDMapping();
      List<Exemplar> exemplars = new ArrayList<>();
      List<Long> counts = new ArrayList<>();

      Chunk[] dataChks = Arrays.copyOf(chks, chks.length-1);
      Chunk assignmentChk = chks[chks.length-1];

      // loop over rows
      DataInfo di = ((DataInfo)_dataInfoKey.get());
      assert(di!=null);
      DataInfo.Row row = di.newDenseRow(); //shared _dataInfo - faster, no writes
      final int nCols = row.nNums;
      for (int r=0; r<chks[0]._len; ++r) {
        long rowIndex = chks[0].start()+r;
        row = di.extractDenseRow(dataChks, r, row);
        double[] data = Arrays.copyOf(row.numVals, nCols);
        if (r==0) {
          Exemplar ex = new Exemplar(data, rowIndex);
          exemplars.add(ex);
          counts.add(1L);
          assignmentChk.set(r, ex.gid);
        } else {
          /* find closest exemplar to this case */
          double distanceToNearestExemplar = Double.MAX_VALUE;
          Iterator<Exemplar> it = exemplars.iterator();
          int closestExemplarIndex = 0;
          int index = 0;
          long gid=-1;
          while (it.hasNext()) {
            Exemplar e = it.next();
            double d = squaredEuclideanDistance(e.data, data, nCols, distanceToNearestExemplar);
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
      for (long c:_counts) sum+=c;
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

      ArrayList<Exemplar> myExemplars = new ArrayList();
      myExemplars.addAll(Arrays.asList(_exemplars));

      // loop over other task's exemplars
      for (int r=0; r<exemplars.length; ++r) {
        double[] data = exemplars[r].data;
        double distanceToNearestExemplar = Double.MAX_VALUE;
        int closestExemplarIndex = 0;
        // loop over my exemplars (which might grow below)
        Iterator<Exemplar> it = myExemplars.iterator();
        int itIdx=0;
        while(it.hasNext()) {
          Exemplar ex = it.next();
          double[] e = ex.data;
          double d = squaredEuclideanDistance(e, data, data.length, distanceToNearestExemplar);
          if (d < distanceToNearestExemplar) {
            distanceToNearestExemplar = d;
            closestExemplarIndex = itIdx;
          }
           /* do not need to look further even if some other exemplar is closer */
          if (distanceToNearestExemplar < _delta)
            break;
          itIdx++;
        }
        if (distanceToNearestExemplar < _delta) {
          // add remote exemplar counts/indices to one of my exemplars that are close enough
          _counts[closestExemplarIndex]+=counts[r];

//          Log.info("Reduce: Reassigning " + counts[r] + " rows from " + exemplars[r].gid + " to " + _exemplars[closestExemplarIndex].gid);
          _mapping.set(exemplars[r].gid, _exemplars[closestExemplarIndex].gid);
        } else {
          myExemplars.add(exemplars[r].deepClone());

          long[] newCounts = Arrays.copyOf(_counts, _counts.length+1);
          newCounts[_counts.length] = counts[r];
          _counts = newCounts;
        }
      }
      _exemplars = myExemplars.toArray(new Exemplar[0]);
      mrt._exemplars = null;
      mrt._counts = null;

      assert(_exemplars.length <= localCounts + remoteCounts);
      assert(_counts.length == _exemplars.length);
      long sum=0;
      for(long c:_counts) sum+=c;
      assert(sum == localCounts + remoteCounts);
    }

    private static double squaredEuclideanDistance(double[] e1, double[] e2, int nCols, double thresh) {
      double sum = 0;
      int n = 0;
      boolean missing = false;
      for (int j = 0; j < nCols; j++) {
        final double d1 = e1[j];
        final double d2 = e2[j];
        if (!isMissing(d1) && !isMissing(d2)) {
          final double dist = (d1 - d2);
          sum += dist*dist;
          n++;
        } else {
          missing=true;
        }
        if (!missing && sum > thresh) break; //early cutout
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

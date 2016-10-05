package hex.aggregator;

import hex.*;
import hex.util.LinearAlgebraUtils;
import water.*;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;

import java.util.Arrays;
import java.util.Collections;

public class Aggregator extends ModelBuilder<AggregatorModel,AggregatorModel.AggregatorParameters,AggregatorModel.AggregatorOutput> {

  @Override
  public ToEigenVec getToEigenVec() {
    return LinearAlgebraUtils.toEigen;
  }

  @Override public BuilderVisibility builderVisibility() { return BuilderVisibility.Experimental; }

  public static class Exemplar extends Iced<Exemplar> {
    Exemplar(double[] d, long id) { data=d; gid=id; _cnt=1; }
    final double[] data;
    final long gid;

    long _cnt;  // exemplar count

    /**
     * Add a new exemplar to the input array (doubling it if necessary)
     * @param es Array of exemplars
     * @param e  Adding this exemplar to the array of exemplars
     * @return   Array of exemplars containing the new exemplar
     */
    public static Exemplar[] addExemplar(Exemplar[] es, Exemplar e) {
      Exemplar[] res=es;
      int idx=es.length-1;
      while(idx>=0 && null==es[idx]) idx--;
      if( idx==es.length-1 ) {
        res = Arrays.copyOf(es,es.length<<1);
        res[es.length]=e;
        return res;
      }
      res[idx+1]=e;
      return res;
    }

    /**
     * Trim any training nulls
     * @param es the array to trim
     * @return a new Exemplar[] without trailing nulls
     */
    public static Exemplar[] trim(Exemplar[] es) {
      int idx=es.length-1;
      while(null==es[idx]) idx--;
      return Arrays.copyOf(es,idx+1);
    }

    private double squaredEuclideanDistance(double[] e2, double thresh) {
      double sum = 0;
      int n = 0;
      boolean missing = false;
      double e1[] = data;
      double ncols = e1.length;
      for (int j = 0; j < ncols; j++) {
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
      sum *= ncols / n;
      return sum;
    }

    private static boolean isMissing(double x) {
      return Double.isNaN(x);
    }
  }

  // Number of columns in training set (p)
  @Override protected AggregatorDriver trainModelImpl() { return new AggregatorDriver(); }
  @Override public ModelCategory[] can_build() { return new ModelCategory[]{ ModelCategory.Clustering }; }

  // Called from an http request
  public Aggregator(AggregatorModel.AggregatorParameters parms) { super(parms); init(false); }
  public Aggregator(boolean startup_once) { super(new AggregatorModel.AggregatorParameters(),startup_once); }

  @Override
  public void init(boolean expensive) {
    if (expensive && _parms._categorical_encoding == Model.Parameters.CategoricalEncodingScheme.AUTO){
      _parms._categorical_encoding=Model.Parameters.CategoricalEncodingScheme.Eigen;
    }
    super.init(expensive);
    if (expensive) {
      byte[] types = _train.types();
      for (byte b : types) {
        if (b != Vec.T_NUM) {
          error("_categorical_encoding", "Categorical features must be turned into numeric features. Specify categorical_encoding=\"Eigen\", \"OneHotExplicit\" or \"Binary\"");
        }
      }
    }
    if (error_count() > 0)
      throw H2OModelBuilderIllegalArgumentException.makeFromBuilder(Aggregator.this);
  }

  class AggregatorDriver extends Driver {

    // Main worker thread
    @Override
    public void computeImpl() {
      AggregatorModel model = null;

      DataInfo di = null;
      try {
        init(true);   // Initialize parameters
        if (error_count() > 0) throw new IllegalArgumentException("Found validation errors: " + validationErrors());

        // The model to be built
        model = new AggregatorModel(dest(), _parms, new AggregatorModel.AggregatorOutput(Aggregator.this));
        model.delete_and_lock(_job);

        Frame orig = train(); //this has ignored columns removed etc.

        _job.update(1,"Preprocessing data.");
        di = new DataInfo(orig, null, true, _parms._transform, false, false, false);
        DKV.put(di);
        final double radius = _parms._radius_scale * .1/Math.pow(Math.log(orig.numRows()), 1.0 / orig.numCols()); // mostly always going to be ~ (_radius_scale * 0.09)

        // Add workspace vector for exemplar assignment
        Vec[] vecs = Arrays.copyOf(orig.vecs(), orig.vecs().length+1);
        Vec assignment = vecs[vecs.length-1] = orig.anyVec().makeZero();

        _job.update(1, "Aggregating.");
        AggregateTask aggTask = new AggregateTask(di._key, radius, _job._key).doAll(vecs);

        _job.update(1, "Aggregating exemplar assignments.");
        new RenumberTask(aggTask._mapping).doAll(assignment);

        // Populate model output state
        model._exemplars = aggTask._exemplars;
        model._counts = new long[aggTask._exemplars.length];
        for(int i=0;i<aggTask._exemplars.length;++i)
          model._counts[i] = aggTask._exemplars[i]._cnt;
        model._exemplar_assignment_vec_key = assignment._key;
        model._output._output_frame = Key.make("aggregated_" + _parms._train.toString() + "_by_" + model._key);

        _job.update(1, "Creating output frame.");
        model.createFrameOfExemplars(_parms._train.get(), model._output._output_frame);

        _job.update(1, "Done.");
        model.update(_job);
      } finally {
        if (model != null) {
          model.unlock(_job);
          Scope.untrack(Collections.singletonList(model._exemplar_assignment_vec_key));
          Frame outFrame = model._output._output_frame.get();
          if (outFrame != null) Scope.untrack(outFrame.keysList());
        }
        if (di!=null) di.remove();
      }
    }
  }

  private static class AggregateTask extends MRTask<AggregateTask> {
    //INPUT
    final double _delta;
    final Key _dataInfoKey;
    final Key _jobKey;

    // OUTPUT
    Exemplar[] _exemplars;
//    long[] _counts;

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

    public AggregateTask(Key<DataInfo> dataInfoKey, double radius, Key<Job> jobKey) {
      _delta = radius*radius;
      _dataInfoKey = dataInfoKey;
      _jobKey = jobKey;
    }

    @Override
    public void map(Chunk[] chks) {
      _mapping = new GIDMapping();
      Exemplar[] es = new Exemplar[4];

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
          es = Exemplar.addExemplar(es,ex);
          assignmentChk.set(r, ex.gid);
        } else {
          /* find closest exemplar to this case */
          double distanceToNearestExemplar = Double.MAX_VALUE;
          int closestExemplarIndex = 0;
          int index = 0;
          long gid=-1;
          for(Exemplar e: es) {
            if( null==e ) break;
            double distToExemplar = e.squaredEuclideanDistance(data,distanceToNearestExemplar);
            if( distToExemplar < distanceToNearestExemplar ) {
              distanceToNearestExemplar = distToExemplar;
              closestExemplarIndex = index;
              gid=e.gid;
            }
            /* do not need to look further even if some other exemplar is closer */
            if (distanceToNearestExemplar < _delta)
              break;
            index++;
          }
          /* found a close exemplar, so add to list */
          if (distanceToNearestExemplar < _delta) {
            es[closestExemplarIndex]._cnt++;
            assignmentChk.set(r, gid);
          } else {
            /* otherwise, assign a new exemplar */
            Exemplar ex = new Exemplar(data, rowIndex);
            es = Exemplar.addExemplar(es,ex);
            assignmentChk.set(r, rowIndex); //assign to self
          }
        }
      }
      // populate output primitive arrays
      _exemplars = Exemplar.trim(es);
      assert(_exemplars.length <= chks[0].len());
      long sum=0;
      for (Exemplar e: _exemplars) sum+=e._cnt;
      assert(sum <= chks[0].len());
      ((Job)_jobKey.get()).update(1, "Aggregating.");
    }

    @Override
    public void reduce(AggregateTask mrt) {
      for (int i=0; i<mrt._mapping.len; ++i)
        _mapping.set(mrt._mapping.pairSet[i].first, mrt._mapping.pairSet[i].second);
      // reduce mrt into this
      Exemplar[] exemplars = mrt._exemplars;
//      long[] counts = mrt._counts;
      long localCounts = 0;
      for (Exemplar e : _exemplars) localCounts += e._cnt;
      long remoteCounts = 0;
      for (Exemplar e : mrt._exemplars) remoteCounts += e._cnt;

      // remote tasks exemplars
      for(int r=0;r<mrt._exemplars.length;++r) {
        double distanceToNearestExemplar = Double.MAX_VALUE;
        int closestExemplarIndex = 0;
        int index=0;
        for(Exemplar le: _exemplars) {
          if( null==le ) break; // tapped out
          double distToExemplar = le.squaredEuclideanDistance(mrt._exemplars[r].data,distanceToNearestExemplar);
          if( distToExemplar < distanceToNearestExemplar ) {
            distanceToNearestExemplar = distToExemplar;
            closestExemplarIndex=index;
          }
           /* do not need to look further even if some other exemplar is closer */
          if (distanceToNearestExemplar < _delta)
            break;
          index++;
        }
        if (distanceToNearestExemplar < _delta) {
          // add remote exemplar counts/indices to one of my exemplars that are close enough
          _exemplars[closestExemplarIndex]._cnt += mrt._exemplars[r]._cnt;

//          Log.info("Reduce: Reassigning " + counts[r] + " rows from " + exemplars[r].gid + " to " + _exemplars[closestExemplarIndex].gid);
          _mapping.set(exemplars[r].gid, _exemplars[closestExemplarIndex].gid);
        } else {
          _exemplars = Exemplar.addExemplar(_exemplars, IcedUtils.deepCopy(mrt._exemplars[r]));
        }
      }
      mrt._exemplars = null;
      _exemplars = Exemplar.trim(_exemplars);
      assert(_exemplars.length <= localCounts + remoteCounts);
      long sum=0;
      for(Exemplar e: _exemplars) sum+=e._cnt;
      assert(sum == localCounts + remoteCounts);
      ((Job)_jobKey.get()).update(1, "Aggregating.");
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

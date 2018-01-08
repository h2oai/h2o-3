package hex.aggregator;

import hex.*;
import hex.util.LinearAlgebraUtils;
import water.*;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.IcedInt;
import water.util.Log;

import java.util.Arrays;
import java.util.Collections;

public class Aggregator extends ModelBuilder<AggregatorModel,AggregatorModel.AggregatorParameters,AggregatorModel.AggregatorOutput> {

  @Override
  public ToEigenVec getToEigenVec() {
    return LinearAlgebraUtils.toEigen;
  }

  @Override public BuilderVisibility builderVisibility() { return BuilderVisibility.Stable; }
  @Override public boolean isSupervised() { return false; }

  public static class Exemplar extends Iced<Exemplar> {
    Exemplar(double[] d, int[] c, long id) { data=d; cats=c; gid=id; _cnt=1; }
    final double[] data; //numerical
    final int[] cats; //categorical
    final long gid;

    long _cnt;  // exemplar count

    /**
     * Add a new exemplar to the input array (doubling it if necessary)
     * @param es Array of exemplars
     * @param e  Adding this exemplar to the array of exemplars
     * @return   Array of exemplars containing the new exemplar
     */
    public static Exemplar[] addExemplar(Exemplar[] es, Exemplar e) {
      if (es.length == 0) {
        return new Exemplar[]{e};
      } else {
        Exemplar[] res=es;
        int idx = es.length - 1;
        while (idx >= 0 && es[idx] == null) idx--;
        if (idx == es.length - 1) {
          res = Arrays.copyOf(es, es.length << 1);
          res[es.length] = e;
          return res;
        }
        res[idx + 1] = e;
        return res;
      }
    }

    /**
     * Trim any training nulls
     * @param es the array to trim
     * @return a new Exemplar[] without trailing nulls
     */
    public static Exemplar[] trim(Exemplar[] es) {
      int idx=es.length-1;
      while(idx>=0 && null==es[idx]) idx--;
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
    if (_parms._target_num_exemplars <= 0) {
      error("_target_num_exemplars", "target_num_exemplars must be > 0.");
    }
    if (_parms._rel_tol_num_exemplars <= 0 || _parms._rel_tol_num_exemplars>=1) {
      error("_rel_tol_num_exemplars", "rel_tol_num_exemplars must be inside 0...1.");
    }
    super.init(expensive);
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
        Vec assignment;
        AggregateTask aggTask;
        final double radiusBase = .1 / Math.pow(Math.log(orig.numRows()), 1.0 / orig.numCols()); // Lee's magic formula
        final int targetNumExemplars = (int)Math.min((long)_parms._target_num_exemplars, orig.numRows());

        // Increase radius until we have low enough number of exemplars
        _job.update(0, "Aggregating.");
        int numExemplars;
        double lo = 0;
        double hi = 256;
        double mid = 8; //starting point of radius_scale

        double tol = _parms._rel_tol_num_exemplars;
        int upperLimit = (int)((1.+tol)*targetNumExemplars);
        int lowerLimit = (int)((1.-tol)*targetNumExemplars);

        Key terminateKey = Key.make();
        while(true) {
          Log.info("radius_scale lo/mid/hi: " + lo + "/" + mid + "/" + hi);
          double radius = mid * radiusBase;
          if (targetNumExemplars==orig.numRows()) radius = 0;

          // Add workspace vector for exemplar assignment
          Vec[] vecs = Arrays.copyOf(orig.vecs(), orig.vecs().length + 1);
          assignment = vecs[vecs.length - 1] = orig.anyVec().makeZero();
          Log.info("Aggregating with radius " + String.format("%5f", radius) + ":");
          aggTask = new AggregateTask(di._key, radius, _job._key, upperLimit, radius == 0 ? null : terminateKey).doAll(vecs);

          if (radius == 0) {
            Log.info(" Returning original dataset.");
            numExemplars = aggTask._exemplars.length;
            assert(numExemplars == orig.numRows());
            break;
          }

          // stuck in range [0,256] with too many exemplars? - just do it
          if (aggTask.isTerminated() && Math.abs(hi-lo) < 1e-3 * Math.abs(lo+hi)) {
            aggTask = new AggregateTask(di._key, radius, _job._key, (int)orig.numRows(), terminateKey).doAll(vecs);
            Log.info(" Running again without early cutout.");
            numExemplars = aggTask._exemplars.length;
            break;
          }

          if (aggTask.isTerminated() || aggTask._exemplars.length > upperLimit) {
            Log.info(" Too many exemplars.");
            lo = mid;
          } else {
            numExemplars = aggTask._exemplars.length;
            Log.info(" " + numExemplars + " exemplars.");
            if (numExemplars >= lowerLimit && numExemplars <= upperLimit) { // close enough
              Log.info(" Within " + (100*tol) +"% of target number of exemplars. Done.");
              break;
            } else {
              Log.info(" Too few exemplars.");
              hi = mid;
            }
          }
          mid = lo + (hi-lo)/2.;
        }
        _job.update(1, "Aggregation finished. Got " + numExemplars + " examplars");
        assert (!aggTask.isTerminated());
        DKV.remove(terminateKey);

        String msg = "Creating exemplar assignments.";
        Log.info(msg);
        _job.update(1, msg);
        new RenumberTask(aggTask._mapping).doAll(assignment);

        // Populate model output state
        model._exemplars = aggTask._exemplars;
        model._counts = new long[aggTask._exemplars.length];
        for(int i=0;i<aggTask._exemplars.length;++i)
          model._counts[i] = aggTask._exemplars[i]._cnt;
        model._exemplar_assignment_vec_key = assignment._key;
        model._output._output_frame = Key.make("aggregated_" + _parms._train.toString() + "_by_" + model._key);
        msg = "Creating output frame.";
        Log.info(msg);
        _job.update(1, msg);
        model.createFrameOfExemplars(_parms._train.get(), model._output._output_frame);
        if(model._parms._save_mapping_frame){
          model._output._mapping_frame = Key.make("aggregated_mapping_" + _parms._train.toString() + "_by_" + model._key);
          model.createMappingOfExemplars(model._output._mapping_frame);
        }
        _job.update(1, "Done.");
        model.update(_job);
      } catch (Throwable t){
        t.printStackTrace();
        throw t;
      } finally {
        if (model != null) {
          model.unlock(_job);
          Scope.untrack(model._exemplar_assignment_vec_key);
          Frame outFrame = model._output._output_frame != null ? model._output._output_frame.get() : null;
          if (outFrame != null) Scope.untrack(outFrame.keys());
          Frame mappingFrame = model._output._mapping_frame != null ? model._output._mapping_frame.get() : null;
          if (mappingFrame != null) Scope.untrack(mappingFrame.keys());
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
    final int _maxExemplars;

    // OUTPUT
    Exemplar[] _exemplars;
    Key _terminateKey;
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

    public AggregateTask(Key<DataInfo> dataInfoKey, double radius, Key<Job> jobKey, int maxExemplars, Key terminateKey) {
      _delta = radius*radius;
      _dataInfoKey = dataInfoKey;
      _jobKey = jobKey;
      _maxExemplars = maxExemplars;
      _terminateKey = terminateKey;
      if (_terminateKey!=null)
        DKV.put(_terminateKey, new IcedInt(0));
    }
    private boolean isTerminated() {
      return _terminateKey != null && ((IcedInt)(DKV.getGet(_terminateKey)))._val==1;
    }
    private void terminate() {
      if (_terminateKey != null)
        DKV.put(_terminateKey, new IcedInt(1));
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
        if (r%100 == 0 && isTerminated())
          return;
        long rowIndex = chks[0].start()+r;
        row = di.extractDenseRow(dataChks, r, row);
        double[] data = Arrays.copyOf(row.numVals, nCols);
        int[] cats = Arrays.copyOf(row.binIds, row.binIds.length);
        if (r==0) {
          Exemplar ex = new Exemplar(data, cats, rowIndex);
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
            // all categoricals must match: only non-trivial (empty) for categorical_handling == Enum
            if (!Arrays.equals(cats, e.cats)) {
              index++;
              continue;
            }
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
            Exemplar ex = new Exemplar(data, cats, rowIndex);
            assert(Arrays.equals(cats, ex.cats));
            es = Exemplar.addExemplar(es,ex);
            if (es.length > 2*_maxExemplars) { //es array grows by 2x - have to be conservative here
              terminate();
            }
            assignmentChk.set(r, rowIndex); //assign to self
          }
        }
      }
      // populate output primitive arrays
      _exemplars = Exemplar.trim(es);
      if (_exemplars.length > _maxExemplars) {
        terminate();
      }
      if (isTerminated())
        return;

      assert(_exemplars.length <= chks[0].len());
      long sum=0;
      for (Exemplar e: _exemplars) sum+=e._cnt;
      assert(sum <= chks[0].len());
      ((Job)_jobKey.get()).update(1, "Aggregating.");
    }

    @Override
    public void reduce(AggregateTask mrt) {
      if (isTerminated() || _exemplars == null || mrt._exemplars == null || _exemplars.length > _maxExemplars || mrt._exemplars.length > _maxExemplars) {
        terminate();
        _mapping = null;
        _exemplars = null;
        mrt._exemplars = null;
      }
      if (isTerminated())
        return;

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

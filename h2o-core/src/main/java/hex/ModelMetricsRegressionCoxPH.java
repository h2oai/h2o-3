package hex;

import water.DKV;
import water.Key;
import water.MRTask;
import water.Scope;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;

import java.util.*;

import java.util.stream.DoubleStream;

import org.apache.commons.lang.ArrayUtils;
import water.rapids.Merge;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.*;

public class ModelMetricsRegressionCoxPH extends ModelMetricsRegression {

  private double _concordance;
  private long _concordant;
  private long _discordant;
  private long _tied_y;

  public double concordance() { return _concordance; }
  public long concordant() { return _concordant; }
  public long discordant() { return _discordant; }
  public long tiedY() { return _tied_y; }

  public ModelMetricsRegressionCoxPH(Model model, Frame frame, long nobs, double mse, double sigma, double mae,
                                     double rmsle, double meanResidualDeviance, CustomMetric customMetric,
                                     double concordance, long concordant, long discordant, long tied_y) {
    super(model, frame, nobs, mse, sigma, mae, rmsle, meanResidualDeviance, customMetric);
    
    this._concordance = concordance;
    this._concordant = concordant;
    this._discordant = discordant;
    this._tied_y = tied_y;
  }

  public static ModelMetricsRegressionCoxPH getFromDKV(Model model, Frame frame) {
    ModelMetrics mm = ModelMetrics.getFromDKV(model, frame);

    if (! (mm instanceof ModelMetricsRegressionCoxPH))
      throw new H2OIllegalArgumentException("Expected to find a Regression ModelMetrics for model: " + model._key.toString() + " and frame: " + frame._key.toString(),
              "Expected to find a ModelMetricsRegression for model: " + model._key.toString() + " and frame: " + frame._key.toString() + " but found a: " + mm.getClass());

    return (ModelMetricsRegressionCoxPH) mm;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(super.toString());
    
    if(!Double.isNaN(_concordance)) {
      sb.append(" concordance: " + (float) _concordance + "\n");
    } else {
      sb.append(" concordance: N/A\n");
    }
    
    sb.append(" concordant: " + _concordant + "\n");
    sb.append(" discordant: " + _discordant + "\n");
    sb.append(" tied.y: " + _tied_y + "\n");

    return sb.toString();
  }

  public static class MetricBuilderRegressionCoxPH<T extends MetricBuilderRegressionCoxPH<T>> extends MetricBuilderRegression<T> {
    
    private final String startVecName;
    private final String stopVecName;
    private final boolean isStratified;
    private final String[] stratifyBy;

    public MetricBuilderRegressionCoxPH(String startVecName, String stopVecName, boolean isStratified, String[] stratifyByName) {
      this.startVecName = startVecName;
      this.stopVecName = stopVecName;
      this.isStratified = isStratified;
      this.stratifyBy = stratifyByName;
    }

    // Having computed a MetricBuilder, this method fills in a ModelMetrics
    public ModelMetricsRegressionCoxPH makeModelMetrics(Model m, Frame f, Frame adaptedFrame, Frame preds) {
      final ModelMetricsRegression modelMetricsRegression = super.computeModelMetrics(m, f, adaptedFrame, preds);
      final Stats stats = concordance(m, f, adaptedFrame, preds);
      
      ModelMetricsRegressionCoxPH mm = new ModelMetricsRegressionCoxPH(m, f, _count, modelMetricsRegression.mse(),
              weightedSigma(), modelMetricsRegression.mae() , modelMetricsRegression.rmsle(), modelMetricsRegression.mean_residual_deviance(),
              _customMetric, stats.c(), stats.nconcordant, stats.discordant(), stats.nties);


      if (m!=null) m.addModelMetrics(mm);
      return mm;
    }

    private Stats concordance(Model m, Frame fr, Frame adaptFrm, Frame scored) {
      final Vec startVec = adaptFrm.vec(startVecName);
      final Vec stopVec = adaptFrm.vec(stopVecName);
      final Vec statusVec = adaptFrm.lastVec();
      final Vec estimateVec = scored.lastVec();

      final List<Vec> strataVecs =
              isStratified ?
                      Arrays.asList(stratifyBy).stream().map(s -> fr.vec(s) ).collect(toList()) :
                      Collections.emptyList();
     
      return concordance(startVec, stopVec, statusVec, strataVecs, estimateVec);
    }

    static private boolean isValidComparison(double time1, double time2, boolean event1, boolean event2) {
      if (time1 == time2) {
        return event1 != event2;
      }
      if (event1 && event2) {
        return true;
      }
      if (event1 && time1 < time2) {
        return true;
      }
      if (event2 &&  time2 < time1) {
        return true;
      }
      return false;
    }

    static class Stats {
      final long ntotals;
      final long nconcordant;
      final long nties;

      Stats() {
        this(0, 0, 0);
      }

      Stats(long ntotals, long nconcordant, long nties) {
        this.ntotals = ntotals;
        this.nconcordant = nconcordant;
        this.nties = nties;
      }

      double c() {
        return (nconcordant + 0.5d * nties) / ntotals;
      }
      
      long discordant() {
        return ntotals - nconcordant - nties;
      }

      @Override
      public String toString() {
        return "Stats{" +
                "ntotals=" + ntotals +
                ", nconcordant=" + nconcordant +
                ", ndiscordant=" + discordant() +
                ", nties=" + nties +
                '}';
      }

      Stats plus(Stats s2) {
        return new Stats(ntotals + s2.ntotals, nconcordant + s2.nconcordant, nties + s2.nties);
      }
    }
    
    static Stats concordance(final Vec startVec, final Vec stopVec, final Vec eventVec, List<Vec> strataVecs, final Vec estimateVec) {
      final long length = estimateVec.length();

      Scope.enter();

      try {
        final Vec durations = durations(startVec, stopVec);
        final Frame fr = prepareFrameForConcordanceComputation(eventVec, strataVecs, estimateVec, durations);
        return concordanceStats(fr, length);
      } finally {
        Scope.exit();
      }
    }

    private static Frame prepareFrameForConcordanceComputation(Vec eventVec, List<Vec> strataVecs, Vec estimateVec, Vec durations) {
      final Frame fr = new Frame(Key.make());
      fr.add("duration", durations);
      fr.add("event", eventVec);
      fr.add("estimate", estimateVec);
      for (int i = 0; i < strataVecs.size(); i++) {
        fr.add("strata_" + i, strataVecs.get(i));
      }
      DKV.put(fr);
      Scope.track(fr);
      return fr;
    }

    private static Vec durations(Vec startVec, Vec stopVec) {
      Vec vec = null == startVec ? stopVec.makeZero() : startVec;
      Frame fr1 = new Frame(Key.make(), new String[]{"start", "stop"}, new Vec[]{vec, stopVec});
      DKV.put(fr1);

      Scope.track(fr1);
      Frame frame = Scope.track(new MRTask() {
        @Override
        public void map(Chunk c0, Chunk c1, NewChunk nc) {
          for (int i = 0; i < c0._len; i++)
            nc.addNum(c1.atd(i) - c0.atd(i));
        }
      }.doAll(1, Vec.T_NUM, fr1)
              .outputFrame(Key.make("durations_" + fr1._key), new String[]{"durations"}, null));
      Scope.track(frame);
      return frame.vec(0);
    }

    private static Stats concordanceStats(Frame fr, long length){
      final Frame withoutNas = removeNAs(fr);

      final int[] stratasAndDuration = new int[withoutNas.numCols() - 2];
        final int[] strataIndexes = new int[withoutNas.numCols() - 3];
        for (int i = 0; i < strataIndexes.length; i++) {
          stratasAndDuration[i] = i + 3;
          strataIndexes[i] = i + 3;
        }
        stratasAndDuration[withoutNas.numCols() - 3] = 0;

        if (0 == withoutNas.numRows()) {
          return new Stats();
        }

        final Frame sorted = withoutNas.sort(stratasAndDuration);

        final List<Vec.Reader> strataCols = stream(strataIndexes).boxed().map(i -> sorted.vec(i).new Reader()).collect(toList());

        long lastStart = 0L;
        List lastRow = new ArrayList(sorted.numCols() - 3);
        Stats statsAcc = new Stats();

        for (long i = 0; i < sorted.numRows(); i++) {
          final List row = new ArrayList(sorted.numCols() - 3);
          for (Vec.Reader strataCol : strataCols) {
            row.add(strataCol.at(i));
          }

          if (!lastRow.equals(row)) {
            lastRow = row;
            Stats stats = statsForAStrata(sorted.vec("duration").new Reader()
                    , sorted.vec("event").new Reader()
                    , sorted.vec("estimate").new Reader()
                    , lastStart
                    , i);
            lastStart = i;

            statsAcc = statsAcc.plus(stats);
          }
        }

      Stats stats = statsForAStrata( sorted.vec("duration").new Reader()
                                   , sorted.vec("event").new Reader()
                                   , sorted.vec("estimate").new Reader()
                                   , lastStart
                                   , sorted.numRows());


      return statsAcc.plus(stats);
    }

    private static Frame removeNAs(Frame fr) {
      final int[] iDontWantNAsInThisCols = new int[]{0, 2};

      final Frame withoutNas = new Merge.RemoveNAsTask(iDontWantNAsInThisCols)
                                        .doAll(fr.types(), fr)
                                        .outputFrame(Key.make(), fr.names(), fr.domains());
      DKV.put(withoutNas);

      withoutNas.replace(1, withoutNas.vec("event").toNumericVec());

      Scope.track(withoutNas);
      return withoutNas;
    }

    private static Stats statsForAStrata(Vec.Reader duration, Vec.Reader eventVec, Vec.Reader estimateVec, long firstIndex, long lastIndex) {
      if (lastIndex == firstIndex) {
        return new Stats();
      }
      
      int countOfCensored = 0;
      int countOfDead = 0;
      
      for (long i = firstIndex; i < lastIndex; i++) {
        if (0 == eventVec.at(i)) {
          countOfCensored++;
        } else {
          countOfDead++;
        }
      }
      
      long[] indexesOfDead = new long[countOfDead];
      long[] indexesOfCensored = new long[countOfCensored];

      countOfCensored = 0;
      countOfDead = 0;
      
      for (long i = firstIndex; i < lastIndex; i++) {
        if (0 == eventVec.at(i)) {
          indexesOfCensored[countOfCensored++] = i;
        } else {
          indexesOfDead[countOfDead++] = i;
        }
      } 
      
      assert indexesOfCensored.length + indexesOfDead.length == lastIndex - firstIndex;
      
      int diedIndex = 0;
      int censoredIndex = 0;

      final DoubleStream estimatesOfDead = stream(indexesOfDead).mapToDouble(i -> estimateTime(estimateVec, i));
      final StatTree timesToCompare = new StatTree(estimatesOfDead.distinct().sorted().toArray());

      long nTotals = 0L;
      long nConcordant = 0L;
      long nTied = 0L;

      for(;;) {
        final boolean hasMoreCensored = censoredIndex < indexesOfCensored.length;
        final boolean hasMoreDead = diedIndex < indexesOfDead.length;

        // Should we look at some censored indices next, or died indices?
        if (hasMoreCensored && (!hasMoreDead || deadTime(duration, indexesOfDead[diedIndex]) > deadTime(duration,indexesOfCensored[censoredIndex]))) {
          final PairStats pairStats = handlePairs(indexesOfCensored, estimateVec, censoredIndex, timesToCompare);

          nTotals += pairStats.pairs;
          nConcordant += pairStats.concordant;
          nTied += pairStats.tied;

          censoredIndex = pairStats.next_ix;
        } else if (hasMoreDead && (!hasMoreCensored || deadTime(duration, indexesOfDead[diedIndex]) <= deadTime(duration, indexesOfCensored[censoredIndex]))) {
          final PairStats pairStats = handlePairs(indexesOfDead, estimateVec, diedIndex, timesToCompare);

          for (int i = diedIndex; i < pairStats.next_ix; i++) {
            final double pred = estimateTime(estimateVec, indexesOfDead[i]);
            timesToCompare.insert(pred);
          }
         
          nTotals += pairStats.pairs;
          nConcordant += pairStats.concordant;
          nTied += pairStats.tied; 
          
          diedIndex = pairStats.next_ix;
        } else {
          assert !(hasMoreDead || hasMoreCensored);
          break;
        }
      }

      return new Stats(nTotals, nConcordant, nTied);
    }

    private static double deadTime(Vec.Reader duration, long i) {
      return duration.at(i);
    }
    private static double estimateTime(Vec.Reader estimateVec, long i) {
      return -estimateVec.at(i);
    }

    static class PairStats {
      final long pairs; 
      final long concordant;
      final long tied;
      final int next_ix;

      public PairStats(long pairs, long concordant, long tied, int next_ix) {
        this.pairs = pairs;
        this.concordant = concordant;
        this.tied = tied;
        this.next_ix = next_ix;
      }
      
      @Override
      public String toString() {
        return "PairStats{" +
                "pairs=" + pairs +
                ", concordant=" + concordant +
                ", tied=" + tied +
                ", next_ix=" + next_ix +
                '}';
      }
    }

    static PairStats handlePairs(long[] truth, Vec.Reader estimateVec, int first_ix, StatTree statTree) {
      int next_ix = first_ix;

      while (next_ix < truth.length && truth[next_ix] == truth[first_ix]) {
        next_ix++;
      }

      final long pairs = statTree.len() * (next_ix - first_ix);
      long correct = 0L;
      long tied = 0L;

      for (int i = first_ix; i <  next_ix; i++) {
        double estimateTime = estimateTime(estimateVec, truth[i]);
        StatTree.RankAndCount rankAndCount = statTree.rankAndCount(estimateTime);
        correct += rankAndCount.rank;
        tied += rankAndCount.count;
      }

      PairStats pairStats = new PairStats(pairs, correct, tied, next_ix);
      return pairStats;
    }
   }
  
  static class StatTree {
    
    final double[] values;
    final long[] counts;

    StatTree(double[] possibleValues) {
      assert null != possibleValues;
      assert sortedAscending(possibleValues);
     
      this.values = new double[possibleValues.length];
      
      final int filled = fillTree(possibleValues, 0, possibleValues.length, 0);
      addMissingValues(possibleValues, filled);

      this.counts = new long[possibleValues.length];
     
      assert containsAll(possibleValues, this.values);
      assert isSearchTree(this.values);
      assert allZeroes(this.counts);
    }

    private void addMissingValues(double[] possibleValues, int filled) {
      final int missing = possibleValues.length - filled;

      for (int i = 0; i < missing; i++) {
        this.values[filled + i] = possibleValues[i * 2];
      }
    }

    private int fillTree(final double[] inputValues, final int start, final int stop, final int rootIndex) {
      int len = stop - start;
      
      if (0 >= len) {
        return 0;
      }
      
      final int lastFullRow = 32 - Integer.numberOfLeadingZeros(len + 1) - 1;
      final int fillable = (1 << lastFullRow) - 1;
      final int totalOverflow = len - fillable;
      final int leftOverflow = Math.min(totalOverflow, (1 << (lastFullRow - 1)));
      final int leftTreeSize = (1 << (lastFullRow - 1)) - 1 + leftOverflow;
      
      this.values[rootIndex] = inputValues[start + leftTreeSize];
      
      fillTree(inputValues, start, start + leftTreeSize, leftChild(rootIndex));
      fillTree(inputValues, start + leftTreeSize + 1, stop, rightChild(rootIndex));

      return fillable;
    }


    static private boolean sortedAscending(double[] a) {
      int i = 1;
      while (i < a.length) {
        if (a[i - 1] > a[i]) return false;
        i++;
      }
      return true;
    }
    
    static private boolean containsAll(double[] a, double b[]) {
      for (int i = 0; i < b.length; i++) {
        if (!ArrayUtils.contains(a, b[i])) {
          return false;
        }
      }
      return true;
    } 
    
    static private boolean isSearchTree(double[] a) {
      for (int i = 0; i < a.length; i++) {
        final int leftChild = leftChild(i);
        if (leftChild < a.length && a[i] < a[leftChild]){
          return false;
        }
        final int rightChild = rightChild(i);
        if (rightChild < a.length && a[i] > a[rightChild]){
          return false;
        }
      }
      return true;
    }

    static private boolean allZeroes(long[] a) {
      for (int i = 0; i < a.length; i++) {
        if (0L != a[i]){
          return false;
        }
      }
      return true;
    }

    void insert(final double value) {
      int i = 0;
      final long n = this.values.length;
      
      while (i < n) {
        double cur = this.values[i];
        this.counts[i]++;
        
        if (value < cur) {
          i = leftChild(i);
        } else if (value > cur) {
          i = rightChild(i);
        } else {
          return; 
        }
      }
      throw new IllegalArgumentException("Value " + value + " not contained in tree. Tree counts now in illegal state;");
    }

    public int size() {
      return this.values.length;
    }

    public long len() {
      return counts[0];
    }

    static class RankAndCount {
      final long rank;
      final long count;

      public RankAndCount(long rank, long count) {
        this.rank = rank;
        this.count = count;
      }

      @Override
      public String toString() {
        return "RankAndCount{" +
                "rank=" + rank +
                ", count=" + count +
                '}';
      }
    }
    
    RankAndCount rankAndCount(double value) {
      int i = 0;
      int rank = 0;
      long count = 0;
      
      while (i < this.values.length) {
        double cur = this.values[i];

        if (value < cur) {
          i = leftChild(i);
        } else if (value > cur) {
          rank += this.counts[i];
          //subtract off the right tree if exists
          final int nexti = rightChild(i);
          if (nexti < this.values.length) {
            rank -= this.counts[nexti];
            i = nexti;
          } else {
            return new RankAndCount(rank,count);
          }
        } else { //value == cur
          count = this.counts[i];
          final int lefti = leftChild(i);
          if (lefti < this.values.length) {
            long nleft = this.counts[lefti];
            count -= nleft;
            rank += nleft;
            final int righti = rightChild(i);
            if (righti < this.values.length) {
              count -= this.counts[righti];
            }
          }
          return new RankAndCount(rank, count);
        }
      }
      return new RankAndCount(rank, count);
    }

    @Override
    public String toString() {
      return toString(new StringBuilder()).toString();
    }

    private StringBuilder toString(StringBuilder strBuilder) {
      int i = 0;
      int to = 2;
      for (;;) {


        for (; i < to - 1; i++) {
          if (i < this.values.length) {
            strBuilder.append(this.values[i]).append('(').append(this.counts[i]).append(')').append(" ");
          } else {
            return strBuilder;
          }
        }

        strBuilder.append("\n");
        to*=2;
      }
      
    }

    private static int leftChild(int i) {
      return 2 * i + 1;
    }

    private static int rightChild(int i) {
      return 2 * i + 2;
    }
  }
}

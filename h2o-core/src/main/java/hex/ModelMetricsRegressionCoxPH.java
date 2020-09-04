package hex;

import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Frame;
import water.fvec.Vec;

import java.util.*;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

import org.apache.commons.lang.ArrayUtils;

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

      final Stats stats = concordanceStats(null == startVec ? null : startVec.new Reader(), 
              stopVec.new Reader(), eventVec.new Reader(),
              strataVecs.stream().map(it -> it.new Reader()).collect(toList()),
              estimateVec.new Reader(), length);

      return stats;
    }

    private static Stats concordanceStats(Vec.Reader startVec, Vec.Reader stopVec, Vec.Reader eventVec, List<Vec.Reader> strataVecs, Vec.Reader estimateVec, long length) {
      assert 0 <= length && length <= Integer.MAX_VALUE;
      
      Collection<List<Integer>> byStrata =
        IntStream.range(0, (int) length)
                 .filter(i -> !estimateVec.isNA(i) && !stopVec.isNA(i) && (null == startVec || !startVec.isNA(i)))
                 .boxed()
                 .collect(groupingBy(
                      i -> strataVecs.stream()
                              .map(v -> v.at8(i))
                              .collect(toList())
                 )).values();

      return byStrata.stream()
        .map(
          indexes -> statsForAStrata(startVec, stopVec, eventVec, estimateVec, indexes)
        ).reduce(new Stats(), Stats::plus);
    }

    private static Stats statsForAStrata(Vec.Reader startVec, Vec.Reader stopVec, Vec.Reader eventVec, Vec.Reader estimateVec, List<Integer> indexes) {
      int[] indexesOfDead = indexes.stream()
                                   .filter(i -> 0 != eventVec.at(i))
                                   .sorted(Comparator.<Integer>comparingDouble(i -> deadTime(startVec, stopVec, i)))
                                   .mapToInt(Integer::intValue)
                                   .toArray();
      
      int[] indexesOfCensored = indexes.stream()
                                       .filter(i -> 0 == eventVec.at(i))
                                       .sorted(Comparator.<Integer>comparingDouble(i -> deadTime(startVec, stopVec, i)))
                                       .mapToInt(Integer::intValue)
                                       .toArray(); 

      assert indexesOfCensored.length + indexesOfDead.length == indexes.size();

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
        if (hasMoreCensored && (!hasMoreDead || deadTime(startVec, stopVec, indexesOfDead[diedIndex]) > deadTime(startVec, stopVec,indexesOfCensored[censoredIndex]))) {
          final PairStats pairStats = handlePairs(indexesOfCensored, estimateVec, censoredIndex, timesToCompare);

          nTotals += pairStats.pairs;
          nConcordant += pairStats.concordant;
          nTied += pairStats.tied;

          censoredIndex = pairStats.next_ix;
        } else if (hasMoreDead && (!hasMoreCensored || deadTime(startVec, stopVec, indexesOfDead[diedIndex]) <= deadTime(startVec, stopVec, indexesOfCensored[censoredIndex]))) {
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

    private static double deadTime(Vec.Reader startVec, Vec.Reader stopVec, int i) {
      return startVec == null ? stopVec.at(i) : stopVec.at(i) - startVec.at(i);
    }
    private static double estimateTime(Vec.Reader estimateVec, int i) {
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

    static PairStats handlePairs(int[] truth, Vec.Reader estimateVec, int first_ix, StatTree statTree) {
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
//      System.out.println("v=" + value);
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

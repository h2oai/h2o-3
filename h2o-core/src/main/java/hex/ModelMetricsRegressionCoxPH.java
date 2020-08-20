package hex;

import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Frame;
import water.fvec.Vec;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

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
                      Arrays.asList(stratifyBy).stream().map(s -> fr.vec(s) ).collect(Collectors.toList()) :
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

    static private class Stats {
      final long ntotals;
      final long nNotNaN;
      final long nconcordant;
      final long nties;

      final long totals;

      public Stats(long ntotals, long nNotNaN, long nconcordant, long nties, long totals) {
        this.ntotals = ntotals;
        this.nNotNaN = nNotNaN;
        this.nconcordant = nconcordant;
        this.nties = nties;
        this.totals = totals;
      }

      double c() {
        return (nconcordant + 0.5d * nties) / ntotals;
      }
      
      long discordant() {
        return ntotals - nconcordant - nties;
      }
    }
    
    static Stats concordance(final Vec startVec, final Vec stopVec, final Vec eventVec, List<Vec> strataVecs, final Vec estimateVec) {
      final long length = estimateVec.length();

      final Stats stats = concordanceStats(null == startVec ? null : startVec.new Reader(), 
              stopVec.new Reader(), eventVec.new Reader(),
              strataVecs.stream().map(it -> it.new Reader()).collect(Collectors.toList()),
              estimateVec.new Reader(), length);

      return stats;
    }

    private static Stats concordanceStats(Vec.Reader startVec, Vec.Reader stopVec, Vec.Reader eventVec, List<Vec.Reader> strataVecs, Vec.Reader estimateVec, long length) {
      long ntotals = 0;
      long nNotNaN = 0;
      long nconcordant = 0;
      long nties = 0;
      
      long totals = 0;
      
      for (long i = 0; i < length; i++) {
        for (long j = i+1; j < length; j++) {

          final double t1 = stopVec.at(i) - ((startVec != null) ? startVec.at(i) : 0d);
          final double t2 = stopVec.at(j) - ((startVec != null) ? startVec.at(j) : 0d);

          final long event1 = eventVec.at8(i);
          final long event2 = eventVec.at8(j);
          final double estimate1 = estimateVec.at(i);
          final double estimate2 = estimateVec.at(j);

          final long fi = i;
          final long fj = j;

          boolean sameStrata = strataVecs.stream().allMatch(
                  v -> v.at8(fi) == v.at(fj)
          );

          boolean censored1 = 0 == event1;
          boolean censored2 = 0 == event2;

          totals++;

          if (!Double.isNaN(t1) && !Double.isNaN(t2) && !Double.isNaN(estimate1) && !Double.isNaN(estimate2)) {
            nNotNaN++;
          } else {
            continue;
          }

          if (sameStrata && isValidComparison(t1, t2, !censored1, !censored2)) {
            ntotals++;
            if (estimate1 == estimate2) {
              nties++;
            } else if (estimate1 > estimate2) {
              if ((t1 < t2) || (t1 == t2 && !censored1 && censored2)) {
                nconcordant++;
              }
            } else {
              if ((t1 > t2) || (t1 == t2 && censored1 && !censored2)) {
                nconcordant++;
              }
            }
          }
        }
      }

      assert nNotNaN <= totals;
      assert ntotals <= totals;
      assert nconcordant <= totals;
      
      return new Stats(ntotals, nNotNaN, nconcordant, nties, totals);
    }
  }
}

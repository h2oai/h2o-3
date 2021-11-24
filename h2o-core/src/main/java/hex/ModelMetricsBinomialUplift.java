package hex;

import water.MRTask;
import water.Scope;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.C8DVolatileChunk;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;

import java.util.Arrays;

public class ModelMetricsBinomialUplift extends ModelMetricsSupervised {
    public final AUUC _auuc;

    public ModelMetricsBinomialUplift(Model model, Frame frame, long nobs, String[] domain,
                                      double sigma, AUUC auuc,
                                      CustomMetric customMetric) {
        super(model, frame,  nobs, 0, domain, sigma, customMetric);
        _auuc = auuc;
    }

    public static ModelMetricsBinomialUplift getFromDKV(Model model, Frame frame) {
        ModelMetrics mm = ModelMetrics.getFromDKV(model, frame);
        if( !(mm instanceof ModelMetricsBinomialUplift) )
            throw new H2OIllegalArgumentException("Expected to find a Binomial ModelMetrics for model: " + model._key.toString() + " and frame: " + frame._key.toString(),
                    "Expected to find a ModelMetricsBinomial for model: " + model._key.toString() + " and frame: " + frame._key.toString() + " but found a: " + (mm == null ? null : mm.getClass()));
        return (ModelMetricsBinomialUplift) mm;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.toString());
        if(_auuc != null){
            sb.append("Default AUUC: ").append((float) _auuc.auuc()).append("\n");
            sb.append("Qini AUUC: ").append((float) _auuc.auucByType(AUUC.AUUCType.qini)).append("\n");
            sb.append("Lift AUUC: ").append((float) _auuc.auucByType(AUUC.AUUCType.lift)).append("\n");
            sb.append("Gain AUUC: ").append((float) _auuc.auucByType(AUUC.AUUCType.gain)).append("\n");
        }
        return sb.toString();
    }

    public double auuc() {return _auuc.auuc();}

    @Override
    protected StringBuilder appendToStringMetrics(StringBuilder sb) {
        return sb;
    }

    /**
     * Build a Binomial ModelMetrics object from target-class probabilities, from actual labels, and a given domain for both labels (and domain[1] is the target class)
     * @param targetClassProbs A Vec containing target class probabilities
     * @param actualLabels A Vec containing the actual labels (can be for fewer labels than what's in domain, since the predictions can be for a small subset of the data)
     * @return ModelMetrics object
     */
    static public ModelMetricsBinomialUplift make(Vec targetClassProbs, Vec actualLabels, Vec treatment, AUUC.AUUCType auucType, int nbins) {
        return make(targetClassProbs, actualLabels, treatment, actualLabels.domain(), auucType, nbins);
    }
    
    /**
     * Build a Binomial ModelMetrics object from target-class probabilities, from actual labels, and a given domain for both labels (and domain[1] is the target class)
     * @param targetClassProbs A Vec containing target class probabilities
     * @param actualLabels A Vec containing the actual labels (can be for fewer labels than what's in domain, since the predictions can be for a small subset of the data)
     * @param treatment A Vec containing the treatment values               
     * @param domain The two class labels (domain[0] is the non-target class, domain[1] is the target class, for which probabilities are given)
     * @param auucType Type of default AUUC
     * @param auucNbins Number of bins to calculate AUUC (-1 means default value 1000, the number has to be higher than zero)                
     * @return ModelMetrics object
     */
    static public ModelMetricsBinomialUplift make(Vec targetClassProbs, Vec actualLabels, Vec treatment, String[] domain, AUUC.AUUCType auucType, int auucNbins) {
        Scope.enter();
        try {
            Vec labels = actualLabels.toCategoricalVec();
            if (domain == null) domain = labels.domain();
            if (labels == null || targetClassProbs == null || treatment ==  null)
                throw new IllegalArgumentException("Missing actualLabels or predictedProbs or treatment values for uplift binomial metrics!");
            if (!targetClassProbs.isNumeric())
                throw new IllegalArgumentException("Predicted probabilities must be numeric per-class probabilities for uplift binomial metrics.");
            if (domain.length != 2)
                throw new IllegalArgumentException("Domain must have 2 class labels, but is " + Arrays.toString(domain) + " for uplift binomial metrics.");
            labels = labels.adaptTo(domain);
            if (labels.cardinality() != 2)
                throw new IllegalArgumentException("Adapted domain must have 2 class labels, but is " + Arrays.toString(labels.domain()) + " for uplift binomial metrics.");
            if (!treatment.isCategorical() || treatment.cardinality() != 2)
                throw new IllegalArgumentException("Treatment values should be catecorical value and have 2 class " + Arrays.toString(treatment.domain()) + " for uplift binomial uplift metrics.");
            long dataSize = treatment.length();
            if (auucNbins < -1 || auucNbins == 0 || auucNbins > dataSize)
                throw new IllegalArgumentException("The number of bins to calculate AUUC need to be -1 (default value) or higher than zero, but less than data size.");
            if(auucNbins == -1)
                auucNbins = AUUC.NBINS > dataSize ? (int) dataSize : AUUC.NBINS;
            Frame fr = new Frame(targetClassProbs);
            fr.add("labels", labels);
            fr.add("treatment", treatment);
            MetricBuilderBinomialUplift mb = new UpliftBinomialMetrics(labels.domain(), AUUC.calculateQuantileThresholds(auucNbins, targetClassProbs)).doAll(fr)._mb;
            labels.remove();
            Frame preds = new Frame(targetClassProbs);
            ModelMetricsBinomialUplift mm = (ModelMetricsBinomialUplift) mb.makeModelMetrics(null, fr, preds,
                    fr.vec("labels"), fr.vec("treatment"), auucType, auucNbins); // use the Vecs from the frame (to make sure the ESPC is identical)
            mm._description = "Computed on user-given predictions and labels.";
            return mm;
        } finally {
            Scope.exit();
        }
    }

    // helper to build a ModelMetricsBinomial for a N-class problem from a Frame that contains N per-class probability columns, and the actual label as the (N+1)-th column
    private static class UpliftBinomialMetrics extends MRTask<UpliftBinomialMetrics> {
        String[] domain;
        double[] thresholds;
        public MetricBuilderBinomialUplift _mb;
        
        public UpliftBinomialMetrics(String[] domain, double[] thresholds) { 
            this.domain = domain; 
            this.thresholds = thresholds;
        }
        
        @Override public void map(Chunk[] chks) {
            _mb = new MetricBuilderBinomialUplift(domain, thresholds);
            Chunk uplift = chks[0];
            Chunk actuals = chks[1];
            Chunk treatment =chks[2];
            double[] ds = new double[1];
            float[] acts = new float[1];
            for (int i=0; i<chks[0]._len;++i) {
                ds[0] = uplift.atd(i);
                acts[0] = (float) actuals.atd(i);
                float t = (float )treatment.atd(i);
                _mb.perRow(ds, acts, t, 1, 0, null);
            }
        }
        @Override public void reduce(UpliftBinomialMetrics mrt) { _mb.reduce(mrt._mb); }
    }

    public static class MetricBuilderBinomialUplift<T extends MetricBuilderBinomialUplift<T>> extends MetricBuilderSupervised<T> {
        
        protected AUUC.AUUCBuilder _auuc;

        public MetricBuilderBinomialUplift( String[] domain, double[] thresholds) { 
            super(2,domain); 
            if(thresholds != null) {
                _auuc = new AUUC.AUUCBuilder(thresholds);
            }
        }

        public MetricBuilderBinomialUplift( String[] domain) {
            super(2,domain);
        }
        
        public void resetThresholds(double[] thresholds){
            if(thresholds != null) {
                _auuc = new AUUC.AUUCBuilder(thresholds);
            }
        }
        
        @Override public double[] perRow(double ds[], float[] yact, Model m) {
            return perRow(ds, yact, Float.NaN,1, 0, m);
        }
        
        @Override public double[] perRow(double ds[], float[] yact, float treatment, double weight, double offset, Model m) {
            if(Float .isNaN(yact[0])) return ds; // No errors if   actual   is missing
            if(ArrayUtils.hasNaNs(ds)) return ds;  // No errors if prediction has missing values (can happen for GLM)
            if(weight == 0 || Double.isNaN(weight)) return ds;
            int y = (int)yact[0];
            if (y != 0 && y != 1) return ds; // The actual is effectively a NaN
            _wY += weight * y;
            _wYY += weight * y * y;
            _count++;
            _wcount += weight;
            if(_auuc != null) {
                _auuc.perRow(ds[0], weight, y, treatment);
            }
            return ds;
        }

        @Override public void reduce( T mb ) {
            super.reduce(mb);
            if(_auuc != null) {
                _auuc.reduce(mb._auuc);
            }
        }

        /**
         * Create a ModelMetrics for a given model and frame
         * @param m Model
         * @param f Frame
         * @param frameWithExtraColumns Frame that contains extra columns such as weights
         * @param preds Optional predictions (can be null), only used to compute Gains/Lift table for binomial problems  @return
         * @return ModelMetricsBinomialUplift
         */
        @Override public ModelMetrics makeModelMetrics(final Model m, final Frame f,
                                                       Frame frameWithExtraColumns, final Frame preds) {
            Vec resp = null;
            Vec treatment = null;
            AUUC.AUUCType auucType = m==null ? AUUC.AUUCType.AUTO : m._parms._auuc_type;
            if (preds!=null) {
                if (frameWithExtraColumns == null)
                    frameWithExtraColumns = f;
                resp = m==null && frameWithExtraColumns.vec(f.numCols()-1).isCategorical() ?
                        frameWithExtraColumns.vec(f.numCols()-1) //work-around for the case where we don't have a model, assume that the last column is the actual response
                        :
                        frameWithExtraColumns.vec(m._parms._response_column);
                if(m != null && m._parms._treatment_column != null){
                    treatment = frameWithExtraColumns.vec(m._parms._treatment_column);
                }
            }
            int auucNbins = m==null || m._parms._auuc_nbins == -1? 
                    AUUC.NBINS : m._parms._auuc_nbins;
            return makeModelMetrics(m, f, preds, resp, treatment, auucType, auucNbins);
        }

        private ModelMetrics makeModelMetrics(final Model m, final Frame f, final Frame preds,
                                              final Vec resp, final Vec treatment, AUUC.AUUCType auucType, int nbins) {
            AUUC auuc = null;
            if (preds != null) {
                if (resp != null) {
                    if (_auuc == null) {
                        auuc = new AUUC(preds.vec(0), resp, treatment, auucType, nbins);
                    } else {
                        auuc = new AUUC(_auuc, auucType);
                    }
                }
            }
            return makeModelMetrics(m, f, auuc);
        }

        private ModelMetrics makeModelMetrics(Model m, Frame f, AUUC auuc) {
            double sigma = Double.NaN;
            if(auuc == null) {
                sigma = weightedSigma();
                auuc = new AUUC(_auuc, m._parms._auuc_type);
            }
            ModelMetricsBinomialUplift mm = new ModelMetricsBinomialUplift(m, f, _count, _domain, sigma, auuc, _customMetric);
            if (m!=null) m.addModelMetrics(mm);
            return mm;
        }

        @Override
        public Frame makePredictionCache(Model m, Vec response) {
            return new Frame(response.makeVolatileDoubles(1));
        }

        @Override
        public void cachePrediction(double[] cdist, Chunk[] chks, int row, int cacheChunkIdx, Model m) {
            assert cdist.length == 3;
            ((C8DVolatileChunk) chks[cacheChunkIdx]).getValues()[row] = cdist[0];
        }

        public String toString(){
            return "";
        }
    }
}

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
import java.util.Optional;

public class ModelMetricsBinomialUplift extends ModelMetricsSupervised {
    public final AUUC _auuc;
    public final GainsUplift _gainsUplift;

    public ModelMetricsBinomialUplift(Model model, Frame frame, long nobs, String[] domain,
                                      double sigma, AUUC auuc, GainsUplift uplift,
                                      CustomMetric customMetric) {
        super(model, frame,  nobs, 0, domain, sigma, customMetric);
        _gainsUplift = uplift;
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
            sb.append("AUUC: ").append((float) _auuc._auuc).append("\n");
        }
        if (_gainsUplift != null) sb.append(_gainsUplift);
        return sb.toString();
    }

    public GainsUplift gainsUplift() { return _gainsUplift; }
    
    public double auuc() {return _auuc.auuc();}
    

    /**
     * Build a Binomial ModelMetrics object from target-class probabilities, from actual labels, and a given domain for both labels (and domain[1] is the target class)
     * @param targetClassProbs A Vec containing target class probabilities
     * @param actualLabels A Vec containing the actual labels (can be for fewer labels than what's in domain, since the predictions can be for a small subset of the data)
     * @return ModelMetrics object
     */
    static public ModelMetricsBinomialUplift make(Vec targetClassProbs, Vec actualLabels, Vec uplift) {
        return make(targetClassProbs, actualLabels, uplift, actualLabels.domain());
    }

    static public ModelMetricsBinomialUplift make(Vec targetClassProbs, Vec actualLabels, Vec uplift,  String[] domain) {
        return make(targetClassProbs, actualLabels,  uplift, domain);
    }

    /**
     * Build a Binomial ModelMetrics object from target-class probabilities, from actual labels, and a given domain for both labels (and domain[1] is the target class)
     * @param targetClassProbs A Vec containing target class probabilities
     * @param actualLabels A Vec containing the actual labels (can be for fewer labels than what's in domain, since the predictions can be for a small subset of the data)
     * @param weights A Vec containing the observation weights.
     * @param domain The two class labels (domain[0] is the non-target class, domain[1] is the target class, for which probabilities are given)
     * @return ModelMetrics object
     */
    static public ModelMetricsBinomialUplift make(Vec targetClassProbs, Vec actualLabels, Vec weights, Vec treatment, String[] domain) {
        Scope.enter();
        try {
            Vec labels = actualLabels.toCategoricalVec();
            if (domain == null) domain = labels.domain();
            if (labels == null || targetClassProbs == null)
                throw new IllegalArgumentException("Missing actualLabels or predictedProbs for binomial metrics!");
            if (!targetClassProbs.isNumeric())
                throw new IllegalArgumentException("Predicted probabilities must be numeric per-class probabilities for binomial metrics.");
            if (targetClassProbs.min() < 0 || targetClassProbs.max() > 1)
                throw new IllegalArgumentException("Predicted probabilities must be between 0 and 1 for binomial metrics.");
            if (domain.length != 2)
                throw new IllegalArgumentException("Domain must have 2 class labels, but is " + Arrays.toString(domain) + " for binomial metrics.");
            labels = labels.adaptTo(domain);
            if (labels.cardinality() != 2)
                throw new IllegalArgumentException("Adapted domain must have 2 class labels, but is " + Arrays.toString(labels.domain()) + " for binomial metrics.");

            Frame fr = new Frame(targetClassProbs);
            fr.add("labels", labels);
            if (weights != null) {
                fr.add("weights", weights);
            }
            if(treatment != null){
                fr.add("treatment", treatment);
            }
            // TODO solve nbins parameter
            MetricBuilderBinomialUplift mb = new UpliftBinomialMetrics(labels.domain(), AUUC.calculateQuantileThresholds(AUUC.NBINS, targetClassProbs)).doAll(fr)._mb;
            labels.remove();
            Frame preds = new Frame(targetClassProbs);
            // todo solve for uplift here too, meantime null uplift vector is given
            ModelMetricsBinomialUplift mm = (ModelMetricsBinomialUplift) mb.makeModelMetrics(null, fr, preds,
                    fr.vec("labels"), fr.vec("weights"), fr.vec("treatment")); // use the Vecs from the frame (to make sure the ESPC is identical)
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
            Chunk actuals = chks[1];
            Chunk weights = chks.length == 3 ? chks[2] : null;
            Chunk uplift = chks.length == 4 ? chks[3] : null;
            double[] ds = new double[3];
            float[] acts = new float[1];
            for (int i=0; i<chks[0]._len;++i) {
                ds[2] = chks[0].atd(i); //class 1 probs (user-given)
                ds[1] = chks[1].atd(i); //class 0 probs
                ds[0] = ds[1] - ds[2];
                acts[0] = (float) actuals.atd(i);
                double weight = weights != null ? weights.atd(i) : 1;
                double u = uplift.atd(i);
                _mb.perRow(ds, acts, weight, u,0,null);
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


        // Passed a float[] sized nclasses+1; ds[0] must be a prediction.  ds[1...nclasses-1] must be a class
        // distribution;
        @Override public double[] perRow(double ds[], float[] yact, Model m) {return perRow(
                ds, yact, Double.NaN,1, 0, m);
        }
        @Override public double[] perRow(double ds[], float[] yact, double treatment, double weight, double offset, Model m) {
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
            Vec weight = null;
            Vec uplift = null;
            if (_wcount > 0) {
                if (preds!=null) {
                    if (frameWithExtraColumns == null)
                        frameWithExtraColumns = f;
                    resp = m==null && frameWithExtraColumns.vec(f.numCols()-1).isCategorical() ?
                            frameWithExtraColumns.vec(f.numCols()-1) //work-around for the case where we don't have a model, assume that the last column is the actual response
                            :
                            frameWithExtraColumns.vec(m._parms._response_column);
                    if (resp != null) {
                        weight = m==null?null : frameWithExtraColumns.vec(m._parms._weights_column);
                    }
                    if(m != null && m._parms._treatment_column != null){
                        uplift = frameWithExtraColumns.vec(m._parms._treatment_column);
                    }
                }
            }
            return makeModelMetrics(m, f, preds, resp, weight, uplift);
        }

        private ModelMetrics makeModelMetrics(final Model m, final Frame f, final Frame preds,
                                              final Vec resp, final Vec weight, final Vec uplift) {
            GainsUplift gul = null;
            AUUC auuc = null;
            if (_wcount > 0) {
                if (preds != null) {
                    if (resp != null) {
                        if (_auuc == null) {
                            auuc = new AUUC(preds.vec(0), resp, uplift, m._parms._auuc_type);
                        }
                        final Optional<GainsUplift> optionalGainsUplift = calculateGainsUplift(m, preds, resp, weight, uplift);
                        if (optionalGainsUplift.isPresent()) {
                            gul = optionalGainsUplift.get();
                        }
                    }
                }
            }
            return makeModelMetrics(m, f, auuc, gul);
        }

        private ModelMetrics makeModelMetrics(Model m, Frame f, AUUC auuc, GainsUplift gul) {
            double sigma = Double.NaN;
            if(_wcount > 0 && auuc == null) {
                sigma = weightedSigma();
                auuc = new AUUC(_auuc, m._parms._auuc_type);
            } else if(auuc == null){
                auuc = new AUUC();
            }
            ModelMetricsBinomialUplift mm = new ModelMetricsBinomialUplift(m, f, _count, _domain, sigma, auuc, gul, _customMetric);
            if (m!=null) m.addModelMetrics(mm);
            return mm;
        }

        /**
         * @param m       Model to calculate GainsUplift for
         * @param preds   Predictions
         * @param resp    Actual label
         * @param weights Weights
         * @param treatment  Treatment column               
         * @return An Optional with GainsUplift instance if GainsUplift is not disabled (gainsUplift_bins = 0). Otherwise an
         * empty Optional.
         */
        private Optional<GainsUplift> calculateGainsUplift(Model m, Frame preds, Vec resp, Vec weights, Vec treatment) {
            final GainsUplift gl = new GainsUplift(preds.vec(0), resp, weights, treatment);
            if (m != null && m._parms._gainslift_bins < -1) {
                throw new IllegalArgumentException("Number of G/L bins must be greater or equal than -1.");
            } else if (m != null && (m._parms._gainslift_bins > 0 || m._parms._gainslift_bins == -1)) {
                gl._groups = m._parms._gainslift_bins;
            } else if (m != null){
                return Optional.empty();
            }
            gl.exec(m != null ? m._output._job : null);
            return Optional.of(gl);
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
            if(_wcount == 0) return "empty, no rows";
            return "";
        }
    }
}

package hex.ensemble;

import hex.Model;
import hex.ModelBuilder;
import hex.ensemble.Metalearner.Algorithm;
import hex.genmodel.utils.DistributionFamily;
import hex.glm.GLM;
import hex.glm.GLMModel;
import hex.glm.GLMModel.GLMParameters;
import water.exceptions.H2OIllegalArgumentException;
import water.nbhm.NonBlockingHashMap;
import water.util.ArrayUtils;
import water.util.Log;

import java.util.ServiceLoader;
import java.util.function.Supplier;

/**
 * Entry point class to load and access the supported metalearners.
 * Most of them are defined in this class, but some others can be loaded dynamically from the classpath,
 * this is for example the case with the XGBoostMetalearner.
 */
public class Metalearners {

    static final NonBlockingHashMap<String, MetalearnerProvider> providersByName = new NonBlockingHashMap<>();

    static {
        LocalProvider[] localProviders = new LocalProvider[] {
                new LocalProvider<>(Algorithm.AUTO, AUTOMetalearner::new),
                new LocalProvider<>(Algorithm.deeplearning, DLMetalearner::new),
                new LocalProvider<>(Algorithm.drf, DRFMetalearner::new),
                new LocalProvider<>(Algorithm.gbm, GBMMetalearner::new),
                new LocalProvider<>(Algorithm.glm, GLMMetalearner::new),
                new LocalProvider<>(Algorithm.naivebayes, NaiveBayesMetalearner::new),
        };
        for (MetalearnerProvider provider : localProviders) {
            providersByName.put(provider.getName(), provider);
        }

        ServiceLoader<MetalearnerProvider> extensionProviders = ServiceLoader.load(MetalearnerProvider.class);
        for (MetalearnerProvider provider : extensionProviders) {
            providersByName.put(provider.getName(), provider);
        }
    }

    static Algorithm getActualMetalearnerAlgo(Algorithm algo) {
        assertAvailable(algo.name());
        return algo == Algorithm.AUTO ? Algorithm.glm : algo;
    }

    public static Model.Parameters createParameters(String name) {
        assertAvailable(name);
        return createInstance(name).createBuilder()._parms;
    }

    static Metalearner createInstance(String name) {
        assertAvailable(name);
        return providersByName.get(name).newInstance();
    }

    private static void assertAvailable(String algo) {
        if (!providersByName.containsKey(algo))
            throw new H2OIllegalArgumentException("'"+algo+"' metalearner is not supported or available.");
    }

    /**
     * A local implementation of {@link MetalearnerProvider} to expose the {@link Metalearner}s defined in this class.
     */
    static class LocalProvider<M extends Metalearner> implements MetalearnerProvider<M> {

        private Algorithm _algorithm;
        private Supplier<M> _instanceFactory;

        public LocalProvider(Algorithm algorithm,
                             Supplier<M> instanceFactory) {
            _algorithm = algorithm;
            _instanceFactory = instanceFactory;
        }

        @Override
        public String getName() {
            return _algorithm.name();
        }

        @Override
        public M newInstance() {
            return _instanceFactory.get();
        }
    }

    /**
     * A simple implementation of {@link Metalearner} suitable for any algo; it is just using the algo with its default parameters.
     */
    public static class SimpleMetalearner extends Metalearner {
        private String _algo;

        protected SimpleMetalearner(String algo) {
            _algo = algo;
        }

        @Override
        ModelBuilder createBuilder() {
            return ModelBuilder.make(_algo, _metalearnerJob, _metalearnerKey);
        }

        protected String getAlgo() {
            return _algo;
        }
    }

    static class MetalearnerWithDistribution extends SimpleMetalearner {
        protected DistributionFamily[] _supportedDistributionFamilies;
        protected MetalearnerWithDistribution(String algo) {
            super(algo);
        }
        @Override
        protected void validateParams(Model.Parameters parms) {
            super.validateParams(parms);

            // Check if distribution family is supported and if not pick a basic one
            if (!ArrayUtils.contains(_supportedDistributionFamilies, parms._distribution)) {
                DistributionFamily distribution;
                if (_model._output.nclasses() == 1) {
                    distribution = DistributionFamily.gaussian;
                } else if (_model._output.nclasses() == 2) {
                    distribution = DistributionFamily.bernoulli;
                } else {
                    distribution = DistributionFamily.multinomial;
                }

                Log.warn("Distribution \"" + parms._distribution +
                        "\" is not supported by metalearner algorithm \"" + getAlgo() +
                        "\". Using \"" + distribution + "\" instead.");

                parms._distribution = distribution;
            }
        }
    }

    static class DLMetalearner extends MetalearnerWithDistribution {
        public DLMetalearner() {
            super(Algorithm.deeplearning.name());
            _supportedDistributionFamilies = new DistributionFamily[]{
                    DistributionFamily.AUTO,
                    DistributionFamily.bernoulli,
                    DistributionFamily.multinomial,
                    DistributionFamily.gaussian,
                    DistributionFamily.poisson,
                    DistributionFamily.gamma,
                    DistributionFamily.laplace,
                    DistributionFamily.quantile,
                    DistributionFamily.huber,
                    DistributionFamily.tweedie,
            };
        }

    }

    static class DRFMetalearner extends MetalearnerWithDistribution {
        public DRFMetalearner() {
            super(Algorithm.drf.name());
            _supportedDistributionFamilies = new DistributionFamily[]{
                    DistributionFamily.AUTO,
                    DistributionFamily.bernoulli,
                    DistributionFamily.multinomial,
                    DistributionFamily.gaussian,
            };
        }
    }

    static class GBMMetalearner extends MetalearnerWithDistribution {
        public GBMMetalearner() {
            super(Algorithm.gbm.name());
            _supportedDistributionFamilies = new DistributionFamily[]{
                    DistributionFamily.AUTO,
                    DistributionFamily.bernoulli,
                    DistributionFamily.quasibinomial,
                    DistributionFamily.multinomial,
                    DistributionFamily.gaussian,
                    DistributionFamily.poisson,
                    DistributionFamily.gamma,
                    DistributionFamily.laplace,
                    DistributionFamily.quantile,
                    DistributionFamily.huber,
                    DistributionFamily.tweedie,
                    DistributionFamily.custom,
            };
        }
    }

    static class GLMMetalearner extends Metalearner<GLM, GLMModel, GLMParameters> {
        @Override
        GLM createBuilder() {
            return ModelBuilder.make("GLM", _metalearnerJob, _metalearnerKey);
        }
    }

    static class NaiveBayesMetalearner extends SimpleMetalearner {
        public NaiveBayesMetalearner() {
            super(Algorithm.naivebayes.name());
        }
    }

    static class AUTOMetalearner extends GLMMetalearner {

        @Override
        protected void setCustomParams(GLMParameters parms) {
            //add GLM custom params
            super.setCustomParams(parms);

            parms._generate_scoring_history = true;
            parms._score_iteration_interval = (parms._valid == null) ? 5 : -1;

            //specific to AUTO mode
            parms._non_negative = true;
            //parms._alpha = new double[] {0.0, 0.25, 0.5, 0.75, 1.0};

            // feature columns are already homogeneous (probabilities); when standardization is enabled,
            // there can be information loss if some columns have very low probabilities compared with others for example (bad model)
            // giving more weight than it should to those columns.
            parms._standardize = false;

            // Enable lambda search if a validation frame is passed in to get a better GLM fit.
            // Since we are also using non_negative to true, we should also set early_stopping = false.
            if (parms._valid != null) {
                parms._lambda_search = true;
                parms._early_stopping = false;
            }
        }
    }

}

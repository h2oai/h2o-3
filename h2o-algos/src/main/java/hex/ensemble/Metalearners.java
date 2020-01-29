package hex.ensemble;

import hex.Model;
import hex.ModelBuilder;
import hex.ModelCategory;
import hex.deeplearning.DeepLearning;
import hex.deeplearning.DeepLearningModel;
import hex.deeplearning.DeepLearningModel.DeepLearningParameters;
import hex.ensemble.Metalearner.Algorithm;
import hex.glm.GLM;
import hex.glm.GLMModel;
import hex.glm.GLMModel.GLMParameters;
import hex.naivebayes.NaiveBayes;
import hex.naivebayes.NaiveBayesModel;
import hex.naivebayes.NaiveBayesModel.NaiveBayesParameters;
import hex.psvm.PSVM;
import hex.psvm.PSVMModel;
import hex.psvm.PSVMModel.PSVMParameters;
import hex.tree.drf.DRF;
import hex.tree.drf.DRFModel;
import hex.tree.drf.DRFModel.DRFParameters;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;
import hex.tree.gbm.GBMModel.GBMParameters;
import water.exceptions.H2OIllegalArgumentException;
import water.nbhm.NonBlockingHashMap;

import java.util.ServiceLoader;
import java.util.function.Supplier;

public class Metalearners {

    static final NonBlockingHashMap<String, MetalearnerProvider> providersByName = new NonBlockingHashMap<>();

    static {
        LocalProvider[] localProviders = new LocalProvider[] {
                new LocalProvider<>(Algorithm.AUTO, GLMParameters::new, AUTOMetalearner::new),
                new LocalProvider<>(Algorithm.deeplearning, DeepLearningParameters::new, DLMetalearner::new),
                new LocalProvider<>(Algorithm.drf, DRFParameters::new, DRFMetalearner::new),
                new LocalProvider<>(Algorithm.gbm, GBMParameters::new, GBMMetalearner::new),
                new LocalProvider<>(Algorithm.glm, GLMParameters::new, GLMMetalearner::new),
                new LocalProvider<>(Algorithm.naivebayes, NaiveBayesParameters::new, NaiveBayesMetalearner::new),
                new LocalProvider<>(Algorithm.psvm, PSVMParameters::new, PSVMMetalearner::new),
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
        return algo == Algorithm.AUTO ? Algorithm.glm : algo;
    }

    static Model.Parameters createParameters(String name) {
        if (providersByName.containsKey(name)) {
            return providersByName.get(name).newParameters();
        }
        throw new H2OIllegalArgumentException("'"+name+"' metalearner is not supported or available.");
    }

    static Metalearner createInstance(String name) {
        if (providersByName.containsKey(name)) {
            return providersByName.get(name).newInstance();
        }
        throw new H2OIllegalArgumentException("'"+name+"' metalearner is not supported or available.");
    }

    static class LocalProvider<M extends Metalearner, P extends Model.Parameters> implements MetalearnerProvider<M, P> {

        private Algorithm _algorithm;
        private Supplier<P> _parametersFactory;
        private Supplier<M> _instanceFactory;

        public LocalProvider(Algorithm algorithm,
                             Supplier<P> parametersFactory,
                             Supplier<M> instanceFactory) {
            _algorithm = algorithm;
            _parametersFactory = parametersFactory;
            _instanceFactory = instanceFactory;
        }

        @Override
        public String getName() {
            return _algorithm.name();
        }

        @Override
        public P newParameters() {
            return _parametersFactory.get();
        }

        @Override
        public M newInstance() {
            return _instanceFactory.get();
        }
    }

    static class GenericMetalearner extends Metalearner {
        private String _algo;

        public GenericMetalearner(String algo) {
            _algo = algo;
        }

        @Override
        ModelBuilder createBuilder() {
            return ModelBuilder.make(_algo, _metalearnerJob, _metalearnerKey);
        }
    }

    static class DLMetalearner extends Metalearner<DeepLearning, DeepLearningModel, DeepLearningParameters> {
        @Override
        DeepLearning createBuilder() {
            return ModelBuilder.make("DeepLearning", _metalearnerJob, _metalearnerKey);
        }
    }

    static class DRFMetalearner extends Metalearner<DRF, DRFModel, DRFParameters> {
        @Override
        DRF createBuilder() {
            return ModelBuilder.make("DRF", _metalearnerJob, _metalearnerKey);
        }
    }

    static class GBMMetalearner extends Metalearner<GBM, GBMModel, GBMParameters> {
        @Override
        GBM createBuilder() {
            return ModelBuilder.make("GBM", _metalearnerJob, _metalearnerKey);
        }
    }

    static class GLMMetalearner extends Metalearner<GLM, GLMModel, GLMParameters> {
        @Override
        GLM createBuilder() {
            return ModelBuilder.make("GLM", _metalearnerJob, _metalearnerKey);
        }

        @Override
        protected void setCustomParams(GLMParameters parms) {
            if (_model.modelCategory == ModelCategory.Regression) {
                parms._family = GLMParameters.Family.gaussian;
            } else if (_model.modelCategory == ModelCategory.Binomial) {
                parms._family = GLMParameters.Family.binomial;
            } else if (_model.modelCategory == ModelCategory.Multinomial) {
                parms._family = GLMParameters.Family.multinomial;
            } else {
                throw new H2OIllegalArgumentException("Family " + _model.modelCategory + "  is not supported.");
            }
        }
    }

    static class NaiveBayesMetalearner extends Metalearner<NaiveBayes, NaiveBayesModel, NaiveBayesParameters> {
        @Override
        NaiveBayes createBuilder() {
            return ModelBuilder.make("NaiveBayes", _metalearnerJob, _metalearnerKey);
        }
    }

    static class PSVMMetalearner extends Metalearner<PSVM, PSVMModel, PSVMParameters> {
        @Override
        PSVM createBuilder() {
            return ModelBuilder.make("PSVM", _metalearnerJob, _metalearnerKey);
        }
    }

    static class AUTOMetalearner extends GLMMetalearner {

        @Override
        protected void setCustomParams(GLMParameters parms) {
            //add GLM custom params
            super.setCustomParams(parms);

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

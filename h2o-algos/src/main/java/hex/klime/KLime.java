package hex.klime;

import hex.Model;
import hex.ModelBuilder;
import hex.ModelCategory;
import hex.ModelMetrics;
import hex.ModelMetricsSupervised;
import hex.glm.GLM;
import hex.glm.GLMModel;
import hex.kmeans.KMeans;
import hex.kmeans.KMeansModel;
import water.*;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.FrameUtils;

import java.util.HashSet;
import java.util.Set;

import static hex.kmeans.KMeansModel.KMeansParameters;

public class KLime extends ModelBuilder<KLimeModel, KLimeModel.KLimeParameters, KLimeModel.KLimeOutput> {

  @Override
  public ModelCategory[] can_build() {
    return new ModelCategory[]{ModelCategory.Regression};
  }

  @Override
  public BuilderVisibility builderVisibility() {
    return BuilderVisibility.Experimental;
  }

  @Override
  public boolean isSupervised() {
    return true;
  }

  public KLime(boolean startup_once) { super(new KLimeModel.KLimeParameters(), startup_once); }

  public KLime(KLimeModel.KLimeParameters parms) {
    super(parms);
    init(false);
  }

  @Override
  protected Driver trainModelImpl() {
    return new KLimeDriver();
  }

  private class KLimeDriver extends Driver {
    @Override
    public void computeImpl() {
      KLimeModel model = null;
      Set<Key<Frame>> frameKeys = new HashSet<>();
      try {
        init(true);

        // The model to be built
        model = new KLimeModel(dest(), _parms, new KLimeModel.KLimeOutput(KLime.this));
        model.delete_and_lock(_job);

        Key<Frame> clusteringTrainKey = Key.<Frame>make("klime_clustering_" + _parms._train);
        frameKeys.add(clusteringTrainKey);
        Frame clusteringTrain = new Frame(clusteringTrainKey);
        clusteringTrain.add(train());
        clusteringTrain.remove(_parms._response_column);
        DKV.put(clusteringTrain);

        KMeansParameters kmeansParms = _parms.fillClusteringParms(new KMeansParameters(), clusteringTrain._key);
        KMeans clustering = new KMeans(kmeansParms, _job);
        KMeansModel clusteringModel = clustering.trainModelNested(null);

        Frame clusterLabels = Scope.track(clusteringModel.score(clusteringTrain));

        final int K = clusteringModel._output._k[clusteringModel._output._k.length - 1];
        String[] clusterNames = new String[K];
        for (int i = 0; i < K; i++)
          clusterNames[i] = "cluster" + i;

        clusterLabels.vec(0).setDomain(clusterNames);
        DKV.put(clusterLabels);

        Frame clusterWeights = Scope.track(FrameUtils.categoricalEncoder(clusterLabels, new String[0],
                Model.Parameters.CategoricalEncodingScheme.OneHotExplicit, null));

        ModelBuilder[] localBuilders = new ModelBuilder[K];
        int localBuilderCnt = 0;
        for (int i = 0; i < K; i++) {
          Vec weightVec = clusterWeights.vec(clusterWeights.find("predict." + clusterNames[i]));
          if (weightVec.nzCnt() < _parms._min_cluster_size)
            continue; // do not build a local model for too small clusters

          localBuilderCnt++;

          Key<Frame> key = Key.<Frame>make("klime_train_cluster_" + i + "-" + _parms._train);
          frameKeys.add(key);
          Frame frame = new Frame(key);
          frame.add("__cluster_weights", weightVec);
          frame.add(train());
          DKV.put(frame);

          Key<Model> glmKey = Key.<Model>make("klime_glm_cluster_" + i + "-" + model._key);
          Job glmJob = new Job<>(glmKey, ModelBuilder.javaName("glm"), "k-LIME Regression (GLM, cluster = " + i + ")");
          DKV.put(glmJob);
          Scope.track_generic(glmJob);

          GLM glmBuilder = ModelBuilder.make("GLM", glmJob, glmKey);
          _parms.fillRegressionParms(glmBuilder._parms, key, true);

          localBuilders[i] = glmBuilder;
        }

        Key<Frame> key = Key.<Frame>make("klime_train_global_" + _parms._train);
        frameKeys.add(key);
        DKV.put(key, train());
        Key<Model> globalKey = Key.<Model>make("klime_glm_global_" + model._key);
        Job globalJob = new Job<>(globalKey, ModelBuilder.javaName("glm"), "k-LIME Regression (Global GLM)");
        DKV.put(globalJob);
        Scope.track_generic(globalJob);
        GLM globalBuilder = ModelBuilder.make("GLM", globalJob, globalKey);
        _parms.fillRegressionParms(globalBuilder._parms, key, false);

        ModelBuilder[] allBuilders = new ModelBuilder[localBuilderCnt + 1];
        int localIdx = 0;
        for (ModelBuilder localBuilder : localBuilders) {
          if (localBuilder != null)
            allBuilders[localIdx++] = localBuilder;
        }
        assert localIdx == localBuilderCnt;
        allBuilders[localIdx] = globalBuilder;

        bulkBuildModels(_job, allBuilders, 1);

        GLMModel globalRegressionModel = DKV.getGet(globalBuilder._job._result);
        double global_r2 = ((ModelMetricsSupervised) globalRegressionModel._output._training_metrics).r2();
        GLMModel[] regressionModels = new GLMModel[K];
        for (int i = 0; i < localBuilders.length; i++) {
          if (localBuilders[i] != null) {
            GLMModel localModel = DKV.getGet(localBuilders[i]._job._result);
            double local_r2 = ((ModelMetricsSupervised) localModel._output._training_metrics).r2();
            if (local_r2 > global_r2)
              regressionModels[i] = localModel; // local model is better, keep it
            else
              Scope.track_generic(localModel); // global model is better, delete the local one
          } else
            _job.update(1); // model won't be built
        }

        model._output._clustering = clusteringModel;
        model._output._globalRegressionModel = globalRegressionModel;
        model._output._regressionModels = regressionModels;

        model.score(_parms.train()).delete(); // This scores on the training data and appends a ModelMetrics
        model._output._training_metrics = ModelMetrics.getFromDKV(model, _parms.train());

        model.update(_job);
      } finally {
        if (model != null) model.unlock(_job);
        Futures fs = new Futures();
        for (Key<Frame> k : frameKeys)
          DKV.remove(k, fs);
        fs.blockForPending();
      }
    }
  }

}
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils


def scale_pca_rf_pipe():

  from h2o.transforms.preprocessing import H2OScaler
  from h2o.transforms.decomposition import H2OPCA
  from h2o.estimators.random_forest import H2ORandomForestEstimator
  from sklearn.pipeline import Pipeline

  iris = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))

  # build transformation pipeline using sklearn's Pipeline and H2O transforms
  pipe = Pipeline([("standardize", H2OScaler()),
                   ("pca", H2OPCA(k=2)),
                   ("rf", H2ORandomForestEstimator(seed=42,ntrees=50))])
  pipe.fit(iris[:4],iris[4])
  pca = pipe.named_steps['pca']
  assert pca.model_id == pca._delegate.model_id
  assert pca._model_json == pca._delegate._model_json
  pca.download_pojo()


def scale_pca_rf_pipe_new_import():

  from h2o.transforms.preprocessing import H2OScaler
  from h2o.estimators.pca import H2OPrincipalComponentAnalysisEstimator
  from h2o.estimators.random_forest import H2ORandomForestEstimator
  from sklearn.pipeline import Pipeline

  iris = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))

  # build transformation pipeline using sklearn's Pipeline and H2O estimators without H2OPrincipalComponentAnalysisEstimator.init_for_pipeline()
  # it should fail
  # Note: if you use PCA algo in a different combination of pipeline tasks, it could not fail, for example
  #     if you comment line with H2ORandomForestEstimator task, the fit method doesn't fail because the pipeline doesn't
  #     use _fit_transform_one method thus does not use H2OPrincipalComponentAnalysisEstimator.transform method
  try:
    pipe = Pipeline([
                     ("standardize", H2OScaler()),
                     ("pca", H2OPrincipalComponentAnalysisEstimator(k=2)),
                     ("rf", H2ORandomForestEstimator(seed=42,ntrees=5))
                    ])
    pipe.fit(iris[:4], iris[4])
    assert False, "Pipeline should fail without using H2OPrincipalComponentAnalysisEstimator.init_for_pipeline()"
  except TypeError:
     pass


  # build transformation pipeline using sklearn's Pipeline and H2O estimators with H2OPrincipalComponentAnalysisEstimator.init_for_pipeline()
  pipe = Pipeline([("standardize", H2OScaler()),
                   ("pca", H2OPrincipalComponentAnalysisEstimator(k=2).init_for_pipeline()),
                   ("rf", H2ORandomForestEstimator(seed=42,ntrees=5))])
  pipe.fit(iris[:4], iris[4])
  print(pipe)

  # set H2OPCA transform property
  pca = H2OPrincipalComponentAnalysisEstimator(k=2)
  pca.transform = "standardize"
  pipe = Pipeline([("standardize", H2OScaler()),
                   ("pca", pca.init_for_pipeline()),
                   ("rf", H2ORandomForestEstimator(seed=42,ntrees=5))])
  pipe.fit(iris[:4], iris[4])
  print(pipe)


if __name__ == "__main__":
  pyunit_utils.standalone_test(scale_pca_rf_pipe)
  pyunit_utils.standalone_test(scale_pca_rf_pipe_new_import)
else:
  scale_pca_rf_pipe()
  scale_pca_rf_pipe_new_import()

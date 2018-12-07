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
  print(pipe)


def scale_pca_rf_pipe_new_import():

  from h2o.transforms.preprocessing import H2OScaler
  from h2o.estimators.pca import H2OPrincipalComponentAnalysisEstimator as H2OPCA
  from h2o.estimators.random_forest import H2ORandomForestEstimator
  from sklearn.pipeline import Pipeline

  iris = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))

  # build transformation pipeline using sklearn's Pipeline and H2O estimators without H2OPCA.init_for_pipeline()
  # it should fail
  try:
    pipe = Pipeline([("standardize", H2OScaler()),
                     ("pca", H2OPCA(k=2)),
                     ("rf", H2ORandomForestEstimator(seed=42,ntrees=50))])
    pipe.fit(iris[:4], iris[4])
    print(pipe)
    assert True, "Pipeline should fail without using H2OPCA.init_for_pipeline()"
  except:
    pass

  # build transformation pipeline using sklearn's Pipeline and H2O estimators with H2OPCA.init_for_pipeline()
  pipe = Pipeline([("standardize", H2OScaler()),
                   ("pca", H2OPCA(k=2).init_for_pipeline()),
                   ("rf", H2ORandomForestEstimator(seed=42,ntrees=50))])
  pipe.fit(iris[:4], iris[4])
  print(pipe)

  # set H2OPCA transform property
  pca = H2OPCA(k=2)
  pca.transform = "standardize"
  pipe = Pipeline([("standardize", H2OScaler()),
                   ("pca", pca.init_for_pipeline()),
                   ("rf", H2ORandomForestEstimator(seed=42,ntrees=50))])
  pipe.fit(iris[:4], iris[4])
  print(pipe)


if __name__ == "__main__":
  pyunit_utils.standalone_test(scale_pca_rf_pipe)
  pyunit_utils.standalone_test(scale_pca_rf_pipe_new_import)
else:
  scale_pca_rf_pipe()
  scale_pca_rf_pipe_new_import()

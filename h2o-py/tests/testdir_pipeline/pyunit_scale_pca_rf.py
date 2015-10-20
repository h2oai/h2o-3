



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


scale_pca_rf_pipe()

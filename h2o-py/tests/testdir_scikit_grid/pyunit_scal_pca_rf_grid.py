import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils

from h2o.transforms.preprocessing import H2OScaler
from h2o.estimators.random_forest import H2ORandomForestEstimator
from sklearn.pipeline import Pipeline
from sklearn.model_selection import RandomizedSearchCV
from h2o.cross_validation import H2OKFold
from h2o.model.regression import h2o_r2_score
from sklearn.metrics import make_scorer
from scipy.stats import randint


def scale_pca_rf_pipe():
  from h2o.transforms import H2OPCA
  iris = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))

  # build  transformation pipeline using sklearn's Pipeline and H2O transforms
  pipe = Pipeline([("standardize", H2OScaler()),
                   ("pca", H2OPCA()),
                   ("rf", H2ORandomForestEstimator())])

  params = {"standardize__center":    [True, False],             # Parameters to test
            "standardize__scale":     [True, False],
            "pca__k":                 randint(2, iris[1:].shape[1]),
            "rf__ntrees":             randint(50,60),
            "rf__max_depth":          randint(4,8),
            "rf__min_rows":           randint(5,10),
            "pca__transform":         ["none", "standardize"],
            }

  custom_cv = H2OKFold(iris, n_folds=5, seed=42)
  random_search = RandomizedSearchCV(pipe, params,
                                     n_iter=5,
                                     scoring=make_scorer(h2o_r2_score),
                                     cv=custom_cv,
                                     random_state=42,
                                     n_jobs=1)


  random_search.fit(iris[1:],iris[0])

  print(random_search.best_estimator_)


def scale_pca_rf_pipe_new_import():
  from h2o.estimators.pca import H2OPrincipalComponentAnalysisEstimator
  iris = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))

  # build transformation pipeline using sklearn's Pipeline and H2O transforms
  pipe = Pipeline([
                    ("standardize", H2OScaler()),
                    ("pca", H2OPrincipalComponentAnalysisEstimator().init_for_pipeline()),
                    ("rf", H2ORandomForestEstimator())
                  ])

  params = {"standardize__center":    [True, False],             # Parameters to test
            "standardize__scale":     [True, False],
            "pca__k":                 randint(2, iris[1:].shape[1]),
            "rf__ntrees":             randint(50,60),
            "rf__max_depth":          randint(4,8),
            "rf__min_rows":           randint(5,10),
            "pca__transform":         ["none", "standardize"],
            }

  custom_cv = H2OKFold(iris, n_folds=5, seed=42)
  random_search = RandomizedSearchCV(pipe,
                                     params,
                                     n_iter=5,
                                     scoring=make_scorer(h2o_r2_score),
                                     cv=custom_cv,
                                     random_state=42,
                                     n_jobs=1)

  random_search.fit(iris[1:],iris[0])

  print(random_search.best_estimator_)


if __name__ == "__main__":
    pyunit_utils.standalone_test(scale_pca_rf_pipe)
    pyunit_utils.standalone_test(scale_pca_rf_pipe_new_import)
else:
    scale_pca_rf_pipe()
    scale_pca_rf_pipe_new_import()

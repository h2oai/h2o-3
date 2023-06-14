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


def scale_svd_rf_pipe():
    from h2o.transforms.decomposition import H2OSVD
    
    print("Importing USArrests.csv data...")
    arrests = h2o.upload_file(pyunit_utils.locate("smalldata/pca_test/USArrests.csv"))

    # build  transformation pipeline using sklearn's Pipeline and H2OSVD
    pipe = Pipeline([
        ("standardize", H2OScaler()),
        ("svd", H2OSVD()),
        ("rf", H2ORandomForestEstimator())
    ])

    params = {"standardize__center":    [True, False],
              "standardize__scale":     [True, False],
              "svd__nv":                 [2, 3],
              "rf__ntrees":             randint(50,60),
              "rf__max_depth":          randint(4,8),
              "rf__min_rows":           randint(5,10),
              "svd__transform":         ["none", "standardize"],
              }

    custom_cv = H2OKFold(arrests, n_folds=5, seed=42)
    random_search = RandomizedSearchCV(pipe,
                                       params,
                                       n_iter=5,
                                       scoring=make_scorer(h2o_r2_score),
                                       cv=custom_cv,
                                       random_state=42,
                                       n_jobs=1)

    random_search.fit(arrests[1:],arrests[0])
    print(random_search.best_estimator_)
    

def scale_svd_rf_pipe_new_import():
    from h2o.estimators.svd import H2OSingularValueDecompositionEstimator
    print("Importing USArrests.csv data...")
    arrests = h2o.upload_file(pyunit_utils.locate("smalldata/pca_test/USArrests.csv"))

    print("Compare with SVD")

    # build  transformation pipeline using sklearn's Pipeline and H2OSingularValueDecompositionEstimator
    pipe = Pipeline([
        ("standardize", H2OScaler()),
        # H2OSingularValueDecompositionEstimator() call will fail, you have to call init_for_pipeline method
        ("svd", H2OSingularValueDecompositionEstimator().init_for_pipeline()),
        ("rf", H2ORandomForestEstimator())
    ])

    params = {"standardize__center":    [True, False],
              "standardize__scale":     [True, False],
              "svd__nv":                 [2, 3],
              "rf__ntrees":             randint(50,60),
              "rf__max_depth":          randint(4,8),
              "rf__min_rows":           randint(5,10),
              "svd__transform":         ["none", "standardize"],
              }

    custom_cv = H2OKFold(arrests, n_folds=5, seed=42)
    random_search = RandomizedSearchCV(pipe,
                                       params,
                                       n_iter=5,
                                       scoring=make_scorer(h2o_r2_score),
                                       cv=custom_cv,
                                       random_state=42,
                                       n_jobs=1)

    random_search.fit(arrests[1:], arrests[0])
    print(random_search.best_estimator_)


if __name__ == "__main__":
    pyunit_utils.standalone_test(scale_svd_rf_pipe)
    pyunit_utils.standalone_test(scale_svd_rf_pipe_new_import)
else:
    scale_svd_rf_pipe()
    scale_svd_rf_pipe_new_import()

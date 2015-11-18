
import h2o
h2o.init()

from h2o.estimators.gbm import H2OGradientBoostingEstimator

iris_data_path = h2o.system_file("iris.csv") # load demonstration data
iris_df = h2o.import_file(path=iris_data_path)
iris_df.describe()
gbm_regressor = H2OGradientBoostingEstimator(distribution="gaussian", ntrees=10, max_depth=3, min_rows=2, learn_rate="0.2")
gbm_regressor.train(x=range(1,iris_df.ncol), y=0, training_frame=iris_df)
gbm_regressor

gbm_classifier = H2OGradientBoostingEstimator(distribution="multinomial", ntrees=10, max_depth=3, min_rows=2, learn_rate="0.2")
gbm_classifier.train(x=range(0,iris_df.ncol-1), y=iris_df.ncol-1, training_frame=iris_df)
gbm_classifier

from h2o.estimators.glm import H2OGeneralizedLinearEstimator
prostate_data_path = h2o.system_file("prostate.csv")
prostate_df = h2o.import_file(path=prostate_data_path)
prostate_df["RACE"] = prostate_df["RACE"].asfactor()
prostate_df.describe()
glm_classifier = H2OGeneralizedLinearEstimator(family="binomial", nfolds=10, alpha=0.5)
glm_classifier.train(x=["AGE","RACE","PSA","DCAPS"],y="CAPSULE", training_frame=prostate_df)
glm_classifier

from h2o.estimators.kmeans import H2OKMeansEstimator
cluster_estimator = H2OKMeansEstimator(k=3)
cluster_estimator.train(x=[0,1,2,3], training_frame=iris_df)
cluster_estimator

from h2o.transforms.decomposition import H2OPCA
pca_decomp = H2OPCA(k=2, transform="NONE", pca_method="Power")
pca_decomp.train(x=range(0,4), training_frame=iris_df)
pca_decomp

pred = pca_decomp.predict(iris_df)
pred.head()  # Projection results

# Grid Search

ntrees_opt = [5, 10, 15]
max_depth_opt = [2, 3, 4]
learn_rate_opt = [0.1, 0.2]
hyper_parameters = {"ntrees": ntrees_opt, "max_depth":max_depth_opt, "learn_rate":learn_rate_opt}
from h2o.grid.grid_search import H2OGridSearch
gs = H2OGridSearch(H2OGradientBoostingEstimator(distribution="multinomial"), hyper_params=hyper_parameters)
gs.train(x=range(0,iris_df.ncol-1), y=iris_df.ncol-1, training_frame=iris_df, nfold=10)
print gs.sort_by('logloss', increasing=True)

# Pipeline
from h2o.transforms.preprocessing import H2OScaler
from sklearn.pipeline import Pipeline

# Turn off h2o progress bars
h2o.__PROGRESS_BAR__=False
h2o.no_progress()

# build transformation pipeline using sklearn's Pipeline and H2O transforms
pipeline = Pipeline([("standardize", H2OScaler()),
                 ("pca", H2OPCA(k=2)),
                 ("gbm", H2OGradientBoostingEstimator(distribution="multinomial"))])
pipeline.fit(iris_df[:4],iris_df[4])

# Random CV using H2O and Scikit-learn
from sklearn.grid_search import RandomizedSearchCV
from h2o.cross_validation import H2OKFold
from h2o.model.regression import h2o_r2_score
from sklearn.metrics.scorer import make_scorer
params = {"standardize__center":    [True, False],             # Parameters to test
          "standardize__scale":     [True, False],
          "pca__k":                 [2,3],
          "gbm__ntrees":            [10,20],
          "gbm__max_depth":         [1,2,3],
          "gbm__learn_rate":        [0.1,0.2]}
custom_cv = H2OKFold(iris_df, n_folds=5, seed=42)
pipeline = Pipeline([("standardize", H2OScaler()),
                     ("pca", H2OPCA(k=2)),
                     ("gbm", H2OGradientBoostingEstimator(distribution="gaussian"))])
random_search = RandomizedSearchCV(pipeline, params,
                                   n_iter=5,
                                   scoring=make_scorer(h2o_r2_score),
                                   cv=custom_cv,
                                   random_state=42,
                                   n_jobs=1)
random_search.fit(iris_df[1:], iris_df[0])
print random_search.best_estimator_




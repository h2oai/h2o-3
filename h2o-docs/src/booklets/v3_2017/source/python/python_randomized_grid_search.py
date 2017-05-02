In [57]: from sklearn.grid_search import RandomizedSearchCV

In [58]: from h2o.cross_validation import H2OKFold

In [59]: from h2o.model.regression import h2o_r2_score

In [60]: from sklearn.metrics.scorer import make_scorer

In [61]: from sklearn.metrics.scorer import make_scorer

# Parameters to test
In [62]: params = {"standardize__center":    [True, False],
   ....:           "standardize__scale":     [True, False],
   ....:           "pca__k":                 [2,3],
   ....:           "gbm__ntrees":            [10,20],
   ....:           "gbm__max_depth":         [1,2,3],
   ....:           "gbm__learn_rate":        [0.1,0.2]}

In [63]: custom_cv = H2OKFold(iris_df, n_folds=5, seed=42)

In [64]: pipeline = Pipeline([("standardize", H2OScaler()),
   ....:                      ("pca", H2OPCA(k=2)),
   ....:                      ("gbm", H2OGradientBoostingEstimator(distribution="gaussian"))])

In [65]: random_search = RandomizedSearchCV(pipeline, params,
   ....:                                  n_iter=5,
   ....:                                  scoring=make_scorer(h2o_r2_score),
   ....:                                  cv=custom_cv,
   ....:                                  random_state=42,
   ....:                                  n_jobs=1)
In [66]: random_search.fit(iris_df[1:], iris_df[0])
Out[66]:
RandomizedSearchCV(cv=<h2o.cross_validation.H2OKFold instance at 0x10ba413d0>,
          error_score='raise',
          estimator=Pipeline(steps=[('standardize', <h2o.transforms.preprocessing.H2OScaler object at 0x10c0f18d0>), ('pca', ), ('gbm', )]),
          fit_params={}, iid=True, n_iter=5, n_jobs=1,
          param_distributions={'pca__k': [2, 3], 'gbm__ntrees': [10, 20], 'standardize__scale': [True, False], 'gbm__max_depth': [1, 2, 3], 'standardize__center': [True, False], 'gbm__learn_rate': [0.1, 0.2]},
          pre_dispatch='2*n_jobs', random_state=42, refit=True,
          scoring=make_scorer(h2o_r2_score), verbose=0)

In [67]: print random_search.best_estimator_
Model Details
=============
H2OPCA :  Principal Component Analysis
Model Key:  PCA_model_python_1446220160417_136

Importance of components:
                        pc1       pc2         pc3
----------------------  --------  ----------  ----------
Standard deviation      9.6974  0.091905     0.031356
Proportion of Variance  0.9999  8.98098e-05  1.04541e-05
Cumulative Proportion   0.9999  0.99999      1


ModelMetricsPCA: pca
** Reported on train data. **

MSE: NaN
RMSE: NaN
Model Details
=============
H2OGradientBoostingEstimator :  Gradient Boosting Machine
Model Key:  GBM_model_python_1446220160417_138

Model Summary:
    number_of_trees    number_of_internal_trees   model_size_in_bytes    min_depth    max_depth    mean_depth    min_leaves    max_leaves    mean_leaves
--  -----------------  -------------------------  ---------------------  -----------  -----------  ------------  ------------  ------------  -------------
    20                 20                         2958                   3            3            3             5             8             6.85

ModelMetricsRegression: gbm
** Reported on train data. **

RMSE: 0.193906262445
MAE: 0.155086582663
RMSLE: NaN
Mean Residual Deviance: 0.0375996386155
Scoring History: 

     timestamp            duration    number_of_trees    training_rmse   training_mse    training_deviance
--   -------------------  ----------  -----------------  --------------  --------------  -------------------
     2016-08-25 13:58:15  0.000 sec   0.0                0.683404046309   0.569341466973  0.467041090512
     2016-08-25 13:58:15  0.002 sec   1.0                0.571086656306   0.469106400643  0.326139969011
     2016-08-25 13:58:15  0.003 sec   2.0                0.483508601652   0.395952082872  0.233780567872
     2016-08-25 13:58:15  0.004 sec   3.0                0.414549015095   0.339981133963  0.171850885916
     2016-08-25 13:58:15  0.005 sec   4.0                0.362852508373   0.298212416346  0.131661942833
---  ---                  ---         ---                ---              ---             ---
     2016-08-25 13:58:15  0.017 sec   16.0               0.204549491682   0.164292158112  0.0418404945473
     2016-08-25 13:58:15  0.018 sec   17.0               0.201762323368   0.162030458841  0.0407080351307
     2016-08-25 13:58:15  0.019 sec   18.0               0.199709571992   0.160735480674  0.0398839131454
     2016-08-25 13:58:15  0.019 sec   19.0               0.196739590066   0.158067452484  0.0387064662994
     2016-08-25 13:58:15  0.020 sec   20.0               0.193906262445   0.155086582663  0.0375996386155

Variable Importances:
variable    relative_importance    scaled_importance    percentage
----------  ---------------------  -------------------  ------------
PC1         160.092                1                    0.894701
PC3         14.8175                0.0925562            0.08281
PC2         4.0241                 0.0251361            0.0224893
Pipeline(steps=[('standardize', <h2o.transforms.preprocessing.H2OScaler object at 0x10c1679d0>), ('pca', ), ('gbm', )])

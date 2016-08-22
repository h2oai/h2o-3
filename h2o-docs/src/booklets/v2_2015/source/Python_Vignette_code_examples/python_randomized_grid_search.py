In [57]: from sklearn.grid_search import RandomizedSearchCV

In [58]: from h2o.cross_validation import H2OKFold

In [59]: from h2o.model.regression import h2o_r2_score

In [60]: from sklearn.metrics.scorer import make_scorer

In [61]: from sklearn.metrics.scorer import make_scorer

In [62]: params = {"standardize__center":    [True, False],             # Parameters to test
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
   ....:                                    n_iter=5,
   ....:                                    scoring=make_scorer(h2o_r2_score),
   ....:                                    cv=custom_cv,
   ....:                                    random_state=42,
   ....:                                    n_jobs=1)
In [66]: random_search.fit(iris_df[1:], iris_df[0])
Out[66]:
RandomizedSearchCV(cv=<h2o.cross_validation.H2OKFold instance at 0x108d59200>,
          error_score='raise',
          estimator=Pipeline(steps=[('standardize', <h2o.transforms.preprocessing.H2OScaler object at 0x108d50150>), ('pca', ), ('gbm', )]),
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
Standard deviation      3.16438   0.180179    0.143787
Proportion of Variance  0.994721  0.00322501  0.00205383
Cumulative Proportion   0.994721  0.997946    1


ModelMetricsPCA: pca
** Reported on train data. **

MSE: NaN
Model Details
=============
H2OGradientBoostingEstimator :  Gradient Boosting Machine
Model Key:  GBM_model_python_1446220160417_138

Model Summary:
    number_of_trees    model_size_in_bytes    min_depth    max_depth    mean_depth    min_leaves    max_leaves    mean_leaves
--  -----------------  ---------------------  -----------  -----------  ------------  ------------  ------------  -------------
    20                 2743                   3            3            3             4             8             6.35


ModelMetricsRegression: gbm
** Reported on train data. **

MSE: 0.0566740346323
R^2: 0.916793146878
Mean Residual Deviance: 0.0566740346323

Scoring History:
    timestamp            duration    number_of_trees    training_MSE    training_deviance
--  -------------------  ----------  -----------------  --------------  -------------------
    2015-10-30 09:04:46  0.001 sec   1                  0.477453        0.477453
    2015-10-30 09:04:46  0.002 sec   2                  0.344635        0.344635
    2015-10-30 09:04:46  0.003 sec   3                  0.259176        0.259176
    2015-10-30 09:04:46  0.004 sec   4                  0.200125        0.200125
    2015-10-30 09:04:46  0.005 sec   5                  0.160051        0.160051
    2015-10-30 09:04:46  0.006 sec   6                  0.132315        0.132315
    2015-10-30 09:04:46  0.006 sec   7                  0.114554        0.114554
    2015-10-30 09:04:46  0.007 sec   8                  0.100317        0.100317
    2015-10-30 09:04:46  0.008 sec   9                  0.0890903       0.0890903
    2015-10-30 09:04:46  0.009 sec   10                 0.0810115       0.0810115
    2015-10-30 09:04:46  0.009 sec   11                 0.0760616       0.0760616
    2015-10-30 09:04:46  0.010 sec   12                 0.0725191       0.0725191
    2015-10-30 09:04:46  0.011 sec   13                 0.0694355       0.0694355
    2015-10-30 09:04:46  0.012 sec   14                 0.06741         0.06741
    2015-10-30 09:04:46  0.012 sec   15                 0.0655487       0.0655487
    2015-10-30 09:04:46  0.013 sec   16                 0.0624041       0.0624041
    2015-10-30 09:04:46  0.014 sec   17                 0.0615533       0.0615533
    2015-10-30 09:04:46  0.015 sec   18                 0.058708        0.058708
    2015-10-30 09:04:46  0.015 sec   19                 0.0579205       0.0579205
    2015-10-30 09:04:46  0.016 sec   20                 0.056674        0.056674

Variable Importances:
variable    relative_importance    scaled_importance    percentage
----------  ---------------------  -------------------  ------------
PC1         237.674                1                    0.913474
PC3         12.8597                0.0541066            0.0494249
PC2         9.65329                0.0406157            0.0371014
Pipeline(steps=[('standardize', <h2o.transforms.preprocessing.H2OScaler object at 0x104f2a490>), ('pca', ), ('gbm', )])

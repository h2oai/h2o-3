In [41]: from h2o.transforms.preprocessing import H2OScaler

In [42]: from sklearn.pipeline import Pipeline

In [43]: # Turn off h2o progress bars

In [44]: h2o.__PROGRESS_BAR__=False

In [45]: h2o.no_progress()

In [46]: # build transformation pipeline using sklearn's Pipeline and H2O transforms

In [47]: pipeline = Pipeline([("standardize", H2OScaler()),
   ....:                  ("pca", H2OPCA(k=2)),
   ....:                  ("gbm", H2OGradientBoostingEstimator(distribution="multinomial"))])

In [48]: pipeline.fit(iris_df[:4],iris_df[4])
Out[48]: Model Details
=============
H2OPCA :  Principal Component Analysis
Model Key:  PCA_model_python_1446220160417_32

Importance of components:
                        pc1       pc2
----------------------  --------  ---------
Standard deviation      3.22082   0.34891
Proportion of Variance  0.984534  0.0115538
Cumulative Proportion   0.984534  0.996088


ModelMetricsPCA: pca
** Reported on train data. **

MSE: NaN
RMSE: NaN
Model Details
=============
H2OGradientBoostingEstimator :  Gradient Boosting Machine
Model Key:  GBM_model_python_1446220160417_34

Model Summary:
    number_of_trees    number_of_internal_trees  model_size_in_bytes    min_depth    max_depth    mean_depth    min_leaves    max_leaves    mean_leaves
--  -----------------  ------------------------- ---------------------  -----------  -----------  ------------  ------------  ------------  -------------
    50                 150                         28170                  1            5            4.84          2             13            9.97333


ModelMetricsMultinomial: gbm
** Reported on train data. **

MSE: 0.00162796447355
RMSE: 0.0403480417561
LogLoss: 0.0152718656454
Mean Per-Class Error: 0.0
Confusion Matrix: vertical: actual; across: predicted

Iris-setosa    Iris-versicolor    Iris-virginica    Error    Rate
-------------  -----------------  ----------------  -------  -------
50             0                  0                 0        0 / 50
0              50                 0                 0        0 / 50
0              0                  50                0        0 / 50
50             50                 50                0        0 / 150

Top-3 Hit Ratios:
k    hit_ratio
---  -----------
1    1
2    1
3    1

Scoring History:
     timestamp            duration    number_of_trees    training_rmse      training_logloss    training_classification_error
---  -------------------  ----------  -----------------  ----------------  ------------------  -------------------------------
     2016-08-25 13:50:21  0.006 sec   0.0                0.666666666667   1.09861228867       0.66
     2016-08-25 13:50:21  0.077 sec   1.0                0.603019288754   0.924249463924      0.04
     2016-08-25 13:50:21  0.096 sec   2.0                0.545137025745   0.788619346614      0.04
     2016-08-25 13:50:21  0.110 sec   3.0                0.492902188607   0.679995476522      0.04
     2016-08-25 13:50:21  0.123 sec   4.0                0.446151758168   0.591313596193      0.04
---  ---                  ---         ---                ---              ---                 ---
     2016-08-25 13:50:21  0.419 sec   46.0               0.0489303232171  0.0192767805328     0.0
     2016-08-25 13:50:21  0.424 sec   47.0               0.0462779490149  0.0180720396825     0.0
     2016-08-25 13:50:21  0.429 sec   48.0               0.0444689238255  0.0171428314531     0.0
     2016-08-25 13:50:21  0.434 sec   49.0               0.0423442541538  0.0161938230172     0.0
     2016-08-25 13:50:21  0.438 sec   50.0               0.0403480417561  0.0152718656454     0.0

Variable Importances:
variable    relative_importance    scaled_importance    percentage
----------  ---------------------  -------------------  ------------
PC1         448.958                1                    0.982184
PC2         8.1438                 0.0181393            0.0178162
Pipeline(steps=[('standardize', <h2o.transforms.preprocessing.H2OScaler object at 0x1088c6a50>), ('pca', ), ('gbm', )])
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
Model Details
=============
H2OGradientBoostingEstimator :  Gradient Boosting Machine
Model Key:  GBM_model_python_1446220160417_34

Model Summary:
    number_of_trees    model_size_in_bytes    min_depth    max_depth    mean_depth    min_leaves    max_leaves    mean_leaves
--  -----------------  ---------------------  -----------  -----------  ------------  ------------  ------------  -------------
    150                27014                  1            5            4.84          2             13            9.99333


ModelMetricsMultinomial: gbm
** Reported on train data. **

MSE: 0.00162796438754
R^2: 0.997558053419
LogLoss: 0.0152718654494

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
     timestamp            duration    number_of_trees    training_MSE      training_logloss    training_classification_error
---  -------------------  ----------  -----------------  ----------------  ------------------  -------------------------------
     2015-10-30 09:00:31  0.007 sec   1.0                0.36363226261     0.924249463924      0.04
     2015-10-30 09:00:31  0.011 sec   2.0                0.297174376838    0.788619346614      0.04
     2015-10-30 09:00:31  0.014 sec   3.0                0.242952566898    0.679995475248      0.04
     2015-10-30 09:00:31  0.017 sec   4.0                0.199051390695    0.591313594921      0.04
     2015-10-30 09:00:31  0.021 sec   5.0                0.163730865044    0.517916553872      0.04
---  ---                  ---         ---                ---               ---                 ---
     2015-10-30 09:00:31  0.191 sec   46.0               0.00239417625265  0.0192767794713     0.0
     2015-10-30 09:00:31  0.195 sec   47.0               0.00214164838414  0.0180720391174     0.0
     2015-10-30 09:00:31  0.198 sec   48.0               0.00197748500569  0.0171428309311     0.0
     2015-10-30 09:00:31  0.202 sec   49.0               0.00179303578037  0.0161938228014     0.0
     2015-10-30 09:00:31  0.205 sec   50.0               0.00162796438754  0.0152718654494     0.0

Variable Importances:
variable    relative_importance    scaled_importance    percentage
----------  ---------------------  -------------------  ------------
PC1         448.958                1                    0.982184
PC2         8.1438                 0.0181393            0.0178162
Pipeline(steps=[('standardize', <h2o.transforms.preprocessing.H2OScaler object at 0x1085cec90>), ('pca', ), ('gbm', )])
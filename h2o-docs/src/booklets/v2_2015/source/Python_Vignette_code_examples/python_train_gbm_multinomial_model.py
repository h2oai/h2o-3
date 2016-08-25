In [10]: gbm_classifier = H2OGradientBoostingEstimator(distribution="multinomial", ntrees=10, max_depth=3, min_rows=2, learn_rate="0.2")

In [11]: gbm_classifier.train(x=range(0,iris_df.ncol-1), y=iris_df.ncol-1, training_frame=iris_df)

gbm Model Build Progress: [###################################] 100%

In [12]: gbm_classifier
Out[12]: Model Details
=============
H2OGradientBoostingEstimator :  Gradient Boosting Machine
Model Key:  GBM_model_python_1446220160417_4

Model Summary:
    number_of_trees    model_size_in_bytes    min_depth    max_depth    mean_depth    min_leaves    max_leaves    mean_leaves
--  -----------------  ---------------------  -----------  -----------  ------------  ------------  ------------  -------------
    30                 3933                   1            3            2.93333       2             8             5.86667


ModelMetricsMultinomial: gbm
** Reported on train data. **

MSE: 0.00976685303214
RMSE: 0.0988273900907
LogLoss: 0.0782480973696
Mean Per-Class Error: 0.00666666666667
Confusion Matrix: vertical: actual; across: predicted

Iris-setosa    Iris-versicolor    Iris-virginica    Error       Rate
-------------  -----------------  ----------------  ----------  -------
50             0                  0                 0           0 / 50
0              49                 1                 0.02        1 / 50
0              0                  50                0           0 / 50
50             49                 51                0.00666667  1 / 150

Top-3 Hit Ratios:
k    hit_ratio
---  -----------
1    0.993333
2    1
3    1

Scoring History:
    timestamp            duration    number_of_trees    training_MSE    training_logloss    training_classification_error
--  -------------------  ----------  -----------------  --------------  ------------------  -------------------------------
    2015-10-30 08:51:52  0.047 sec   1                  0.282326        0.758411            0.0266667
    2015-10-30 08:51:52  0.068 sec   2                  0.179214        0.550506            0.0266667
    2015-10-30 08:51:52  0.086 sec   3                  0.114954        0.412173            0.0266667
    2015-10-30 08:51:52  0.100 sec   4                  0.0744726       0.313539            0.02
    2015-10-30 08:51:52  0.112 sec   5                  0.0498319       0.243514            0.02
    2015-10-30 08:51:52  0.131 sec   6                  0.0340885       0.19091             0.00666667
    2015-10-30 08:51:52  0.143 sec   7                  0.0241071       0.151394            0.00666667
    2015-10-30 08:51:52  0.153 sec   8                  0.017606        0.120882            0.00666667
    2015-10-30 08:51:52  0.165 sec   9                  0.0131024       0.0975897           0.00666667
    2015-10-30 08:51:52  0.180 sec   10                 0.00976685      0.0782481           0.00666667

Variable Importances:
variable    relative_importance    scaled_importance    percentage
----------  ---------------------  -------------------  ------------
C4          192.761                1                    0.774374
C3          54.0381                0.280338             0.217086
C1          1.35271                0.00701757           0.00543422
C2          0.773032               0.00401032           0.00310549
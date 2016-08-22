In [1]: import h2o

In [2]: h2o.init()

Java Version: java version "1.8.0_40"
Java(TM) SE Runtime Environment (build 1.8.0_40-b27)
Java HotSpot(TM) 64-Bit Server VM (build 25.40-b25, mixed mode)


Starting H2O JVM and connecting: .................. Connection successful!
--------------------------  --------------------------
H2O cluster uptime:         1 seconds 738 milliseconds
H2O cluster version:        3.5.0.3238
H2O cluster name:           H2O_started_from_python
H2O cluster total nodes:    1
H2O cluster total memory:   3.56 GB
H2O cluster total cores:    4
H2O cluster allowed cores:  4
H2O cluster healthy:        True
H2O Connection ip:          127.0.0.1
H2O Connection port:        54321
--------------------------  --------------------------

In [3]: from h2o.estimators.gbm import H2OGradientBoostingEstimator

In [4]: iris_data_path = "http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris.csv" # load demonstration data

In [5]: iris_df = h2o.import_file(path=iris_data_path)

Parse Progress: [###############################] 100%
Imported /Users/hank/PythonEnvs/h2obleeding/bin/../h2o_data/iris.csv. Parsed 150 rows and 5 cols

In [6]: iris_df.describe()
Rows:150 Cols:5

Chunk compression summary:
chunktype  chunkname  count  count_%  size  size_%
---------  ---------  -----  -------  ----  ------
1-Byte Int   C1         1       20    218B  18.890
1-Byte Flt   C2         4       80    936B  81.109

Frame distribution summary:
                 size  rows  chunks/col  chunks
---------------  ----  ----  ----------  ------
127.0.0.1:54321  1.1KB  150       1        5
mean             1.1KB  150       1        5
min              1.1KB  150       1        5
max              1.1KB  150       1        5
stddev           0  B    0        0        0
total            1.1 KB  150      1        5

In [7]: gbm_regressor = H2OGradientBoostingEstimator(distribution="gaussian", ntrees=10, max_depth=3, min_rows=2, learn_rate="0.2")

In [8]: gbm_regressor.train(x=range(1,iris_df.ncol), y=0, training_frame=iris_df)

gbm Model Build Progress: [#####################] 100%

In [9]: gbm_regressor
Out[9]: Model Details
=============
H2OGradientBoostingEstimator: Gradient Boosting Machine
Model Key:  GBM_model_python_1446220160417_2

Model Summary:
    number_of_trees           |         10
    model_size_in_bytes       |         1535
    min_depth                 |         3
    max_depth                 |         3
    mean_depth                |         3
    min_leaves                |         7
    max_leaves                |         8
    mean_leaves               |         7.8

ModelMetricsRegression: gbm
** Reported on train data. **

MSE: 0.0706936802293
R^2: 0.896209989184
Mean Residual Deviance: 0.0706936802293

Scoring History:
    timestamp            duration    number_of_trees    training_MSE    training_deviance
--  -------------------  ----------  -----------------  --------------  -------------------
    2015-10-30 08:50:00  0.121 sec   1                  0.472445        0.472445
    2015-10-30 08:50:00  0.151 sec   2                  0.334868        0.334868
    2015-10-30 08:50:00  0.162 sec   3                  0.242847        0.242847
    2015-10-30 08:50:00  0.175 sec   4                  0.184128        0.184128
    2015-10-30 08:50:00  0.187 sec   5                  0.14365         0.14365
    2015-10-30 08:50:00  0.197 sec   6                  0.116814        0.116814
    2015-10-30 08:50:00  0.208 sec   7                  0.0992098       0.0992098
    2015-10-30 08:50:00  0.219 sec   8                  0.0864125       0.0864125
    2015-10-30 08:50:00  0.229 sec   9                  0.077629        0.077629
    2015-10-30 08:50:00  0.238 sec   10                 0.0706937       0.0706937

Variable Importances:
variable    relative_importance    scaled_importance    percentage
----------  ---------------------  -------------------  ------------
C3          227.562                1                    0.894699
C2          15.1912                0.0667563            0.0597268
C5          9.50362                0.0417627            0.037365
C4          2.08799                0.00917544           0.00820926
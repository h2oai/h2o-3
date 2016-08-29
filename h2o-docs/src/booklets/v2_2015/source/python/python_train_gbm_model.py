In [1]: import h2o

In [2]: h2o.init()

Checking whether there is an H2O instance running at http://localhost:54321..... not found.
Attempting to start a local H2O server...
  Java Version: java version "1.8.0_25"; Java(TM) SE Runtime Environment (build 1.8.0_25-b17); Java HotSpot(TM) 64-Bit Server VM (build 25.25-b02, mixed mode)
  Starting server from /usr/local/h2o_jar/h2o.jar
  Ice root: /var/folders/yl/cq5nhky53hjcl9wrqxt39kz80000gn/T/tmpHpRzVe
  JVM stdout: /var/folders/yl/cq5nhky53hjcl9wrqxt39kz80000gn/T/tmpHpRzVe/h2o_techwriter_started_from_python.out
  JVM stderr: /var/folders/yl/cq5nhky53hjcl9wrqxt39kz80000gn/T/tmpHpRzVe/h2o_techwriter_started_from_python.err
  Server is running at http://127.0.0.1:54321
Connecting to H2O server at http://127.0.0.1:54321... successful.

In [3]: from h2o.estimators.gbm import H2OGradientBoostingEstimator

In [4]: iris_data_path = "http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris.csv" # load demonstration data

In [5]: iris_df = h2o.import_file(path=iris_data_path)

Parse Progress: [###################################] 100%

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
total            1.1 KB 150       1        5

         C1              C2              C3             C4              C5
-------  --------------  --------------  -------------  --------------  -----------
type     real            real            real           real            enum
mins     4.3             2.0             1.0            0.1             0.0
mean     5.84333333333   3.054           3.75866666667  1.19866666667   NaN
maxs     7.9             4.4             6.9            2.5             2.0
sigma    0.828066127978  0.433594311362  1.76442041995  0.763160741701  NaN
zeros    0               0               0              0               50
missing  0               0               0              0               0


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
RMSE: 0.265882831769
MAE: 0.219981056849
RMSLE: 0.0391855537448
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
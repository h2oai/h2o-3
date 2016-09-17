In [21]: from h2o.estimators.kmeans import H2OKMeansEstimator

In [22]: cluster_estimator = H2OKMeansEstimator(k=3)

In [23]: cluster_estimator.train(x=[0,1,2,3], training_frame=iris_df)

kmeans Model Build Progress: [###################################] 100%

In [24]: cluster_estimator
Out[24]: Model Details
=============
H2OKMeansEstimator :  K-means
Model Key:  K-means_model_python_1446220160417_8

Model Summary:
    number_of_rows    number_of_clusters    number_of_categorical_columns    number_of_iterations    within_cluster_sum_of_squares    total_sum_of_squares    between_cluster_sum_of_squares
--  ----------------  --------------------  -------------------------------  ----------------------  -------------------------------  ----------------------  --------------------------------
    150               3                     0                                4                       190.757                          596                     405.243

ModelMetricsClustering: kmeans
** Reported on train data. **

MSE: NaN
RMSE: NaN
Total Within Cluster Sum of Square Error: 190.756926265
Total Sum of Square Error to Grand Mean: 596.0
Between Cluster Sum of Square Error: 405.243073735

Centroid Statistics:
    centroid    size    within_cluster_sum_of_squares
--  ----------  ------  -------------------------------
    1           96      149.733
    2           32      17.292
    3           22      23.7318

Scoring History:
    timestamp            duration    iteration    avg_change_of_std_centroids    within_cluster_sum_of_squares
--  -------------------  ----------  -----------  -----------------------------  -------------------------------
    2016-08-25 13:03:36  0.005 sec   0            nan                            385.505
    2016-08-25 13:03:36  0.029 sec   1            1.37093                        173.769
    2016-08-25 13:03:36  0.029 sec   2            0.184617                       141.623
    2016-08-25 13:03:36  0.030 sec   3            0.00705735                     140.355
    2016-08-25 13:03:36  0.030 sec   4            0.00122272                     140.162
    2016-08-25 13:03:36  0.031 sec   5            0.000263918                    140.072
    2016-08-25 13:03:36  0.031 sec   6            0.000306555                    140.026
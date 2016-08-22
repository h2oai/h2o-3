In [21]: from h2o.estimators.kmeans import H2OKMeansEstimator

In [22]: cluster_estimator = H2OKMeansEstimator(k=3)

In [23]: cluster_estimator.train(x=[0,1,2,3], training_frame=iris_df)

kmeans Model Build Progress: [##################################################] 100%

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
    2015-10-30 08:54:39  0.011 sec   0            nan                            401.733
    2015-10-30 08:54:39  0.047 sec   1            2.09788                        191.282
    2015-10-30 08:54:39  0.049 sec   2            0.00316006                     190.82
    2015-10-30 08:54:39  0.050 sec   3            0.000846952                    190.757
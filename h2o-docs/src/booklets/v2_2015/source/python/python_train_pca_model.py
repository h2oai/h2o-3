In [25]: from h2o.estimators.pca import H2OPrincipalComponentAnalysisEstimator

In [26]: pca_decomp = H2OPrincipalComponentAnalysisEstimator(k=2, transform="NONE", pca_method="Power", impute_missing=True)

In [27]: pca_decomp.train(x=range(0,4), training_frame=iris_df)

pca Model Build Progress: [#######################################] 100%

In [28]: pca_decomp
Out[28]: Model Details
=============
H2OPCA :  Principal Component Analysis
Model Key:  PCA_model_python_1446220160417_10

ModelMetricsPCA: pca
** Reported on train data. **

MSE: NaN
RMSE: NaN

Scoring History from Power SVD: 
    timestamp            duration    iterations    err          principal_component_
--  -------------------  ----------  ------------  -----------  ----------------------
    2018-01-18 08:35:44  0.002 sec   0             29.6462      1
    2018-01-18 08:35:44  0.002 sec   1             0.733806     1
    2018-01-18 08:35:44  0.002 sec   2             0.0249718    1
    2018-01-18 08:35:44  0.002 sec   3             0.000851969  1
    2018-01-18 08:35:44  0.002 sec   4             2.90753e-05  1
    2018-01-18 08:35:44  0.002 sec   5             1.3487e-06   1
    2018-01-18 08:35:44  0.002 sec   6             nan          1
    2018-01-18 08:35:44  0.003 sec   7             1.02322      2
    2018-01-18 08:35:44  0.003 sec   8             0.0445794    2
    2018-01-18 08:35:44  0.003 sec   9             0.00164307   2
    2018-01-18 08:35:44  0.003 sec   10            6.27379e-05  2
    2018-01-18 08:35:44  0.003 sec   11            2.40329e-06  2
    2018-01-18 08:35:44  0.003 sec   12            9.88431e-08  2
    2018-01-18 08:35:44  0.003 sec   13            nan          2
<bound method H2OPrincipalComponentAnalysisEstimator.train of >

In [29]: pred = pca_decomp.predict(iris_df)

pca prediction progress: [#######################################] 100%

In [30]: pred.head()  # Projection results
Out[30]:
    PC1      PC2
-------  -------
-5.9122   2.30344
-5.57208  1.97383
-5.44648  2.09653
-5.43602  1.87168
-5.87507  2.32935
-6.47699  2.32553
-5.51543  2.07156
-5.85042  2.14948
-5.15851  1.77643
-5.64458  1.99191
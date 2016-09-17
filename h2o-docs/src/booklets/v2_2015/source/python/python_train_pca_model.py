In [25]: from h2o.transforms.decomposition import H2OPCA

In [26]: pca_decomp = H2OPCA(k=2, transform="NONE", pca_method="Power")

In [27]: pca_decomp.train(x=range(0,4), training_frame=iris_df)

pca Model Build Progress: [#######################################] 100%

In [28]: pca_decomp
Out[28]: Model Details
=============
H2OPCA :  Principal Component Analysis
Model Key:  PCA_model_python_1446220160417_10

Importance of components:
                        pc1      pc2
----------------------  -------  --------
Standard deviation      7.86058  1.45192
Proportion of Variance  0.96543  0.032938
Cumulative Proportion   0.96543  0.998368


ModelMetricsPCA: pca
** Reported on train data. **

MSE: NaN
RMSE: NaN

In [29]: pred = pca_decomp.predict(iris_df)

pca prediction progress: [#######################################] 100%

In [30]: pred.head()  # Projection results
Out[30]:
    PC1      PC2
-------  -------
5.9122   2.30344
5.57208  1.97383
5.44648  2.09653
5.43602  1.87168
5.87507  2.32935
6.47699  2.32553
5.51543  2.07156
5.85042  2.14948
5.15851  1.77643
5.64458  1.99191
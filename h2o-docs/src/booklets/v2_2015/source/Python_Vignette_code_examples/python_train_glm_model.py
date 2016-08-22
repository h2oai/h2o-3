In [13]: from h2o.estimators.glm import H2OGeneralizedLinearEstimator

In [14]: prostate_data_path = "http://h2o-public-test-data.s3.amazonaws.com/smalldata/prostate/prostate.csv"

In [15]: prostate_df = h2o.import_file(path=prostate_data_path)

Parse Progress: [##################################################] 100%
Imported /Users/hank/PythonEnvs/h2obleeding/bin/../h2o_data/prostate.csv. Parsed 380 rows and 9 cols

In [16]: prostate_df["RACE"] = prostate_df["RACE"].asfactor()

In [17]: prostate_df.describe()
Rows:380 Cols:9

Chunk compression summary:
chunk_type    chunk_name                 count    count_percentage    size    size_percentage
------------  -------------------------  -------  ------------------  ------  -----------------
CBS           Bits                       1        11.1111             118  B  1.39381
C1N           1-Byte Integers (w/o NAs)  5        55.5556             2.2 KB  26.4588
C2            2-Byte Integers            1        11.1111             828  B  9.7803
CUD           Unique Reals               1        11.1111             2.1 KB  25.6556
C8D           64-bit Reals               1        11.1111             3.0 KB  36.7116

Frame distribution summary:
                 size    number_of_rows    number_of_chunks_per_column    number_of_chunks
---------------  ------  ----------------  -----------------------------  ------------------
127.0.0.1:54321  8.3 KB  380               1                              9
mean             8.3 KB  380               1                              9
min              8.3 KB  380               1                              9
max              8.3 KB  380               1                              9
stddev           0  B    0                 0                              0
total            8.3 KB  380               1                              9



In [18]: glm_classifier = H2OGeneralizedLinearEstimator(family="binomial", nfolds=10, alpha=0.5)

In [19]: glm_classifier.train(x=["AGE","RACE","PSA","DCAPS"],y="CAPSULE", training_frame=prostate_df)

glm Model Build Progress: [##################################################] 100%

In [20]: glm_classifier
Out[20]: Model Details
=============
H2OGeneralizedLinearEstimator :  Generalized Linear Model
Model Key:  GLM_model_python_1446220160417_6

GLM Model: summary

    family    link    regularization                                 number_of_predictors_total    number_of_active_predictors    number_of_iterations    training_frame
--  --------  ------  ---------------------------------------------  ----------------------------  -----------------------------  ----------------------  ----------------
    binomial  logit   Elastic Net (alpha = 0.5, lambda = 3.251E-4 )  6                             6                              6                       py_3


ModelMetricsBinomialGLM: glm
** Reported on train data. **

MSE: 0.202434568594
R^2: 0.158344081513
LogLoss: 0.59112610879
Null degrees of freedom: 379
Residual degrees of freedom: 374
Null deviance: 512.288840185
Residual deviance: 449.25584268
AIC: 461.25584268
AUC: 0.719098211972
Gini: 0.438196423944

Confusion Matrix (Act/Pred) for max f1 @ threshold = 0.28443600654:
       0    1    Error    Rate
-----  ---  ---  -------  -------------
0      80   147  0.6476   (147.0/227.0)
1      19   134  0.1242   (19.0/153.0)
Total  99   281  0.4368   (166.0/380.0)

Maximum Metrics: Maximum metrics at their respective thresholds

metric                      threshold    value     idx
--------------------------  -----------  --------  -----
max f1                      0.284436     0.617512  273
max f2                      0.199001     0.77823   360
max f0point5                0.415159     0.636672  108
max accuracy                0.415159     0.705263  108
max precision               0.998619     1         0
max absolute_MCC            0.415159     0.369123  108
max min_per_class_accuracy  0.33266      0.656388  175

ModelMetricsBinomialGLM: glm
** Reported on cross-validation data. **

MSE: 0.209974707772
R^2: 0.126994679038
LogLoss: 0.609520995116
Null degrees of freedom: 379
Residual degrees of freedom: 373
Null deviance: 515.693473211
Residual deviance: 463.235956288
AIC: 477.235956288
AUC: 0.686706400622
Gini: 0.373412801244

Confusion Matrix (Act/Pred) for max f1 @ threshold = 0.326752491231:
       0    1    Error    Rate
-----  ---  ---  -------  -------------
0      135  92   0.4053   (92.0/227.0)
1      48   105  0.3137   (48.0/153.0)
Total  183  197  0.3684   (140.0/380.0)

Maximum Metrics: Maximum metrics at their respective thresholds

metric                      threshold    value     idx
--------------------------  -----------  --------  -----
max f1                      0.326752     0.6       196
max f2                      0.234718     0.774359  361
max f0point5                0.405529     0.632378  109
max accuracy                0.405529     0.702632  109
max precision               0.999294     1         0
max absolute_MCC            0.405529     0.363357  109
max min_per_class_accuracy  0.336043     0.627451  176

Scoring History:
    timestamp            duration    iteration    log_likelihood    objective
--  -------------------  ----------  -----------  ----------------  -----------
    2015-10-30 08:53:01  0.000 sec   0            256.482           0.674952
    2015-10-30 08:53:01  0.004 sec   1            226.784           0.597118
    2015-10-30 08:53:01  0.005 sec   2            224.716           0.591782
    2015-10-30 08:53:01  0.005 sec   3            224.629           0.59158
    2015-10-30 08:53:01  0.005 sec   4            224.628           0.591579
    2015-10-30 08:53:01  0.006 sec   5            224.628           0.591579
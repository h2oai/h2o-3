In [13]: from h2o.estimators.glm import H2OGeneralizedLinearEstimator

In [14]: prostate_data_path = "http://h2o-public-test-data.s3.amazonaws.com/smalldata/prostate/prostate.csv"

In [15]: prostate_df = h2o.import_file(path=prostate_data_path)

Parse Progress: [###################################] 100%

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

glm Model Build Progress: [###################################] 100%

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

MSE: 0.202442565125
RMSE: 0.449936178947
LogLoss: 0.591121990582
Null degrees of freedom: 379
Residual degrees of freedom: 374
Null deviance: 512.288840185
Residual deviance: 449.252712842
AIC: 461.252712842
AUC: 0.718954248366
Gini: 0.437908496732
Confusion Matrix (Act/Pred) for max f1 @ threshold = 0.282384349078:
       0    1    Error    Rate
-----  ---  ---  -------  -------------
0      80   147  0.6476   (147.0/227.0)
1      19   134  0.1242   (19.0/153.0)
Total  99   281  0.4368   (166.0/380.0)

Maximum Metrics: Maximum metrics at their respective thresholds

metric                      threshold    value     idx
--------------------------  -----------  --------  -----
max f1                       0.282384     0.617849  276
max f2                       0.198777     0.77823   360
max f0point5                 0.415125     0.636672  108
max accuracy                 0.415125     0.705263  108
max precision                0.998613     1         0
max recall                   0.198777     1         360
max specificity              0.998613     1         0
max absolute_mcc             0.415125     0.369123  108
max min_per_class_accuracy   0.332648     0.656388  175
max mean_per_class_accuracy  0.377454     0.67326   123
Gains/Lift Table: Avg response rate: 40.26 %


ModelMetricsBinomialGLM: glm
** Reported on cross-validation data. **

MSE: 0.209698776592
RMSE: 0.457928789871
LogLoss: 0.610086165597
Null degrees of freedom: 379
Residual degrees of freedom: 374
Null deviance: 513.330704712
Residual deviance: 463.665485854
AIC: 475.665485854
AUC: 0.688203622124
Gini: 0.376407244249
Confusion Matrix (Act/Pred) for max f1 @ threshold = 0.339885371023: 
       0    1    Error    Rate
-----  ---  ---  -------  -------------
0      154  73   0.3216   (73.0/227.0)
1      53   100  0.3464   (53.0/153.0)
Total  207  173  0.3316   (126.0/380.0)
Maximum Metrics: Maximum metrics at their respective thresholds

metric                      threshold    value     idx
--------------------------  -----------  --------  -----
max f1                       0.339885     0.613497  172
max f2                       0.172551     0.773509  376
max f0point5                 0.419649     0.615251  105
max accuracy                 0.447491     0.692105  93
max precision                0.998767     1         0
max recall                   0.172551     1         376
max specificity              0.998767     1         0
max absolute_mcc             0.419649     0.338849  105
max min_per_class_accuracy   0.339885     0.653595  172
max mean_per_class_accuracy  0.339885     0.666004  172
Gains/Lift Table: Avg response rate: 40.26 %


Scoring History:
    timestamp            duration    iteration    log_likelihood    objective
--  -------------------  ----------  -----------  ----------------  -----------
    2016-08-25 12:54:20  0.000 sec   0            256.144              0.674064
    2016-08-25 12:54:20  0.055 sec   1            226.961              0.597573
    2016-08-25 12:54:20  0.092 sec   2            224.728              0.591813
    2016-08-25 12:54:20  0.125 sec   3            224.627              0.591578
    2016-08-25 12:54:20  0.157 sec   4            224.626              0.591578
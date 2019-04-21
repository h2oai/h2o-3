import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
# The purpose of this test is to detect a change in the _metric_json of MetricsBase objects. Many of the metric
# accessors require _metric_json to have a particular form.
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
from h2o.estimators.gbm import H2OGradientBoostingEstimator



def metric_json_check():
    df = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))

    # Regression metric json
    reg_mod = H2OGradientBoostingEstimator(distribution="gaussian")
    reg_mod.train(x=list(range(3,df.ncol)), y="CAPSULE", training_frame=df)
    reg_met = reg_mod.model_performance()
    reg_metric_json_keys_have = list(reg_met._metric_json.keys())
    reg_metric_json_keys_desired = [u'model_category',
                                    u'description',
                                    u'r2',
                                    u'frame',
                                    u'model_checksum',
                                    u'MSE',
                                    u'RMSE',
                                    u'mae',
                                    u'rmsle',
                                    u'__meta',
                                    u'_exclude_fields',
                                    u'scoring_time',
                                    u'predictions',
                                    u'model',
                                    u'duration_in_ms',
                                    u'frame_checksum',
                                    u'nobs',
                                    u'mean_residual_deviance',
                                    u'custom_metric_name',
                                    u'custom_metric_value']
    reg_metric_diff = list(set(reg_metric_json_keys_have) - set(reg_metric_json_keys_desired))
    assert not reg_metric_diff, "There's a difference between the current ({0}) and the desired ({1}) regression " \
                                "metric json. The difference is {2}".format(reg_metric_json_keys_have,
                                                                            reg_metric_json_keys_desired,
                                                                            reg_metric_diff)
    # Regression metric json (GLM)
    reg_mod = H2OGeneralizedLinearEstimator(family="gaussian")
    reg_mod.train(x=list(range(3,df.ncol)), y="CAPSULE", training_frame=df)
    reg_met = reg_mod.model_performance()
    reg_metric_json_keys_have = list(reg_met._metric_json.keys())
    reg_metric_json_keys_desired = [u'model_category',
                                    u'description',
                                    u'r2',
                                    u'residual_degrees_of_freedom',
                                    u'frame',
                                    u'model_checksum',
                                    u'MSE',
                                    u'RMSE',
                                    u'mae',
                                    u'rmsle',
                                    u'__meta',
                                    u'_exclude_fields',
                                    u'null_deviance',
                                    u'scoring_time',
                                    u'null_degrees_of_freedom',
                                    u'predictions',
                                    u'AIC',
                                    u'model',
                                    u'duration_in_ms',
                                    u'frame_checksum',
                                    u'nobs',
                                    u'residual_deviance',
                                    u'mean_residual_deviance',
                                    u'custom_metric_name',
                                    u'custom_metric_value']
    reg_metric_diff = list(set(reg_metric_json_keys_have) - set(reg_metric_json_keys_desired))
    assert not reg_metric_diff, "There's a difference between the current ({0}) and the desired ({1}) glm-regression " \
                                "metric json. The difference is {2}".format(reg_metric_json_keys_have,
                                                                            reg_metric_json_keys_desired,
                                                                            reg_metric_diff)

    # Binomial metric json
    bin_mod = H2OGradientBoostingEstimator(distribution="bernoulli")
    df["CAPSULE"] = df["CAPSULE"].asfactor()
    bin_mod.train(x=list(range(3,df.ncol)), y="CAPSULE", training_frame=df)
    bin_met = bin_mod.model_performance()
    bin_metric_json_keys_have = list(bin_met._metric_json.keys())
    bin_metric_json_keys_desired = [u'cm',
                                    u'AUC',
                                    u'Gini',
                                    u'model_category',
                                    u'description',
                                    u'mean_per_class_error',
                                    u'r2',
                                    u'frame',
                                    u'model_checksum',
                                    u'MSE',
                                    u'RMSE',
                                    u'__meta',
                                    u'_exclude_fields',
                                    u'gains_lift_table',
                                    u'logloss',
                                    u'scoring_time',
                                    u'thresholds_and_metric_scores',
                                    u'predictions',
                                    u'max_criteria_and_metric_scores',
                                    u'model',
                                    u'duration_in_ms',
                                    u'frame_checksum',
                                    u'nobs',
                                    u'domain',
                                    u'custom_metric_name',
                                    u'custom_metric_value',
                                    u'pr_auc']
    bin_metric_diff = list(set(bin_metric_json_keys_have) - set(bin_metric_json_keys_desired))
    assert not bin_metric_diff, "There's a difference between the current ({0}) and the desired ({1}) binomial " \
                                "metric json. The difference is {2}".format(bin_metric_json_keys_have,
                                                                            bin_metric_json_keys_desired,
                                                                            bin_metric_diff)

    # Binomial metric json (GLM)
    bin_mod = H2OGeneralizedLinearEstimator(family="binomial")
    bin_mod.train(x=list(range(3,df.ncol)), y="CAPSULE", training_frame=df)
    bin_metric_json_keys_have = list(bin_met._metric_json.keys())
    bin_metric_json_keys_desired = [u'cm',
                                    u'frame',
                                    u'residual_deviance',
                                    u'max_criteria_and_metric_scores',
                                    u'MSE',
                                    u'RMSE',
                                    u'frame_checksum',
                                    u'nobs',
                                    u'AIC',
                                    u'logloss',
                                    u'Gini',
                                    u'predictions',
                                    u'AUC',
                                    u'description',
                                    u'mean_per_class_error',
                                    u'model_checksum',
                                    u'duration_in_ms',
                                    u'model_category',
                                    u'gains_lift_table',
                                    u'r2',
                                    u'residual_degrees_of_freedom',
                                    u'__meta',
                                    u'_exclude_fields',
                                    u'null_deviance',
                                    u'scoring_time',
                                    u'null_degrees_of_freedom',
                                    u'model',
                                    u'thresholds_and_metric_scores',
                                    u'domain',
                                    u'custom_metric_name',
                                    u'custom_metric_value',
                                    u'pr_auc']
    bin_metric_diff = list(set(bin_metric_json_keys_have) - set(bin_metric_json_keys_desired))
    assert not bin_metric_diff, "There's a difference between the current ({0}) and the desired ({1}) glm-binomial " \
                                "metric json. The difference is {2}".format(bin_metric_json_keys_have,
                                                                            bin_metric_json_keys_desired,
                                                                            bin_metric_diff)

    # Multinomial metric json
    df = h2o.import_file(path=pyunit_utils.locate("smalldata/airlines/AirlinesTrain.csv.zip"))
    myX = ["Origin", "Dest", "IsDepDelayed", "UniqueCarrier", "Distance", "fDayofMonth", "fDayOfWeek"]
    myY = "fYear"

    mul_mod = H2OGradientBoostingEstimator(distribution="multinomial")
    mul_mod.train(x=myX, y=myY, training_frame=df)
    mul_met = mul_mod.model_performance()
    mul_metric_json_keys_have = list(mul_met._metric_json.keys())
    mul_metric_json_keys_desired = [u'cm',
                                    u'model_category',
                                    u'description',
                                    u'mean_per_class_error',
                                    u'r2',
                                    u'frame',
                                    u'nobs',
                                    u'model_checksum',
                                    u'MSE',
                                    u'RMSE',
                                    u'__meta',
                                    u'_exclude_fields',
                                    u'logloss',
                                    u'scoring_time',
                                    u'predictions',
                                    u'hit_ratio_table',
                                    u'model',
                                    u'duration_in_ms',
                                    u'frame_checksum',
                                    u'custom_metric_name',
                                    u'custom_metric_value']
    mul_metric_diff = list(set(mul_metric_json_keys_have) - set(mul_metric_json_keys_desired))
    assert not mul_metric_diff, "There's a difference between the current ({0}) and the desired ({1}) multinomial " \
                                "metric json. The difference is {2}".format(mul_metric_json_keys_have,
                                                                            mul_metric_json_keys_desired,
                                                                            mul_metric_diff)

    # Clustering metric json
    df = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris.csv"))
    from h2o.estimators.kmeans import H2OKMeansEstimator
    clus_mod = H2OKMeansEstimator(k=3, standardize=False)
    clus_mod.train(x=list(range(4)), training_frame=df)
    clus_met = clus_mod.model_performance()
    clus_metric_json_keys_have = list(clus_met._metric_json.keys())
    clus_metric_json_keys_desired = [u'tot_withinss',
                                     u'model_category',
                                     u'description',
                                     u'frame',
                                     u'model_checksum',
                                     u'MSE',
                                     u'RMSE',
                                     u'__meta',
                                     u'_exclude_fields',
                                     u'scoring_time',
                                     u'betweenss',
                                     u'predictions',
                                     u'totss',
                                     u'model',
                                     u'duration_in_ms',
                                     u'frame_checksum',
                                     u'nobs',
                                     u'centroid_stats',
                                     u'custom_metric_name',
                                     u'custom_metric_value']
    clus_metric_diff = list(set(clus_metric_json_keys_have) - set(clus_metric_json_keys_desired))
    assert not clus_metric_diff, "There's a difference between the current ({0}) and the desired ({1}) clustering " \
                                "metric json. The difference is {2}".format(clus_metric_json_keys_have,
                                                                            clus_metric_json_keys_desired,
                                                                            clus_metric_diff)

if __name__ == "__main__":
    pyunit_utils.standalone_test(metric_json_check)
else:
    metric_json_check()

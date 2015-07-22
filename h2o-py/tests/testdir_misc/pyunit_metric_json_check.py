# The purpose of this test is to detect a change in the _metric_json of MetricsBase objects. Many of the metric
# accessors require _metric_json to have a particular form.
import sys
sys.path.insert(1, "../../")
import h2o

def metric_json_check(ip, port):
    h2o.init(ip, port)

    df = h2o.import_frame(path=h2o.locate("smalldata/logreg/prostate.csv"))

    # Regression metric json
    reg_mod = h2o.gbm(y=df["CAPSULE"], x=df[3:], training_frame=df, distribution="gaussian")
    reg_met = reg_mod.model_performance()
    reg_metric_json_keys_have = reg_met._metric_json.keys()
    reg_metric_json_keys_desired = [u'model_category',
                                    u'description',
                                    u'r2',
                                    u'frame',
                                    u'model_checksum',
                                    u'MSE',
                                    u'__meta',
                                    u'scoring_time',
                                    u'predictions',
                                    u'model',
                                    u'duration_in_ms',
                                    u'frame_checksum',
                                    u'mean_residual_deviance']
    reg_metric_diff = list(set(reg_metric_json_keys_have) - set(reg_metric_json_keys_desired))
    assert not reg_metric_diff, "There's a difference between the current ({0}) and the desired ({1}) regression " \
                                "metric json. The difference is {2}".format(reg_metric_json_keys_have,
                                                                            reg_metric_json_keys_desired,
                                                                            reg_metric_diff)
    # Regression metric json (GLM)
    reg_mod = h2o.glm(y=df["CAPSULE"], x=df[3:], training_frame=df, family="gaussian")
    reg_met = reg_mod.model_performance()
    reg_metric_json_keys_have = reg_met._metric_json.keys()
    reg_metric_json_keys_desired = [u'model_category',
                                    u'description',
                                    u'r2',
                                    u'residual_degrees_of_freedom',
                                    u'frame',
                                    u'model_checksum',
                                    u'MSE',
                                    u'__meta',
                                    u'null_deviance',
                                    u'scoring_time',
                                    u'null_degrees_of_freedom',
                                    u'predictions',
                                    u'AIC',
                                    u'model',
                                    u'duration_in_ms',
                                    u'frame_checksum',
                                    u'residual_deviance',
                                    u'mean_residual_deviance']
    reg_metric_diff = list(set(reg_metric_json_keys_have) - set(reg_metric_json_keys_desired))
    assert not reg_metric_diff, "There's a difference between the current ({0}) and the desired ({1}) glm-regression " \
                                "metric json. The difference is {2}".format(reg_metric_json_keys_have,
                                                                            reg_metric_json_keys_desired,
                                                                            reg_metric_diff)

    # Binomial metric json
    bin_mod = h2o.gbm(y=df["CAPSULE"].asfactor(), x=df[3:], training_frame=df, distribution="bernoulli")
    bin_met = bin_mod.model_performance()
    bin_metric_json_keys_have = bin_met._metric_json.keys()
    bin_metric_json_keys_desired = [u'AUC',
                                    u'Gini',
                                    u'model_category',
                                    u'description',
                                    u'r2',
                                    u'frame',
                                    u'model_checksum',
                                    u'MSE',
                                    u'__meta',
                                    u'logloss',
                                    u'scoring_time',
                                    u'thresholds_and_metric_scores',
                                    u'predictions',
                                    u'max_criteria_and_metric_scores',
                                    u'model',
                                    u'duration_in_ms',
                                    u'frame_checksum',
                                    u'domain']
    bin_metric_diff = list(set(bin_metric_json_keys_have) - set(bin_metric_json_keys_desired))
    assert not bin_metric_diff, "There's a difference between the current ({0}) and the desired ({1}) binomial " \
                                "metric json. The difference is {2}".format(bin_metric_json_keys_have,
                                                                            bin_metric_json_keys_desired,
                                                                            bin_metric_diff)

    # Binomial metric json (GLM)
    bin_mod = h2o.glm(y=df["CAPSULE"].asfactor(), x=df[3:], training_frame=df, family="binomial")
    bin_met = bin_mod.model_performance()
    bin_metric_json_keys_have = bin_met._metric_json.keys()
    bin_metric_json_keys_desired = [u'frame',
                                    u'residual_deviance',
                                    u'max_criteria_and_metric_scores',
                                    u'MSE',
                                    u'frame_checksum',
                                    u'AIC',
                                    u'logloss',
                                    u'Gini',
                                    u'predictions',
                                    u'AUC',
                                    u'description',
                                    u'model_checksum',
                                    u'duration_in_ms',
                                    u'model_category',
                                    u'r2',
                                    u'residual_degrees_of_freedom',
                                    u'__meta',
                                    u'null_deviance',
                                    u'scoring_time',
                                    u'null_degrees_of_freedom',
                                    u'model',
                                    u'thresholds_and_metric_scores',
                                    u'domain']
    bin_metric_diff = list(set(bin_metric_json_keys_have) - set(bin_metric_json_keys_desired))
    assert not bin_metric_diff, "There's a difference between the current ({0}) and the desired ({1}) glm-binomial " \
                                "metric json. The difference is {2}".format(bin_metric_json_keys_have,
                                                                            bin_metric_json_keys_desired,
                                                                            bin_metric_diff)

    # Multinomial metric json
    df = h2o.import_frame(path=h2o.locate("smalldata/airlines/AirlinesTrain.csv.zip"))
    myX = ["Origin", "Dest", "IsDepDelayed", "UniqueCarrier", "Distance", "fDayofMonth", "fDayOfWeek"]
    myY = "fYear"
    mul_mod = h2o.gbm(x=df[myX], y=df[myY], training_frame=df, distribution="multinomial")
    mul_met = mul_mod.model_performance()
    mul_metric_json_keys_have = mul_met._metric_json.keys()
    mul_metric_json_keys_desired = [u'cm',
                                    u'model_category',
                                    u'description',
                                    u'r2',
                                    u'frame',
                                    u'model_checksum',
                                    u'MSE',
                                    u'__meta',
                                    u'logloss',
                                    u'scoring_time',
                                    u'predictions',
                                    u'hit_ratio_table',
                                    u'model',
                                    u'duration_in_ms',
                                    u'frame_checksum']
    mul_metric_diff = list(set(mul_metric_json_keys_have) - set(mul_metric_json_keys_desired))
    assert not mul_metric_diff, "There's a difference between the current ({0}) and the desired ({1}) multinomial " \
                                "metric json. The difference is {2}".format(mul_metric_json_keys_have,
                                                                            mul_metric_json_keys_desired,
                                                                            mul_metric_diff)

    # Clustering metric json
    df = h2o.import_frame(path=h2o.locate("smalldata/iris/iris.csv"))
    clus_mod = h2o.kmeans(x=df[0:4], k=3, standardize=False)
    clus_met = clus_mod.model_performance()
    clus_metric_json_keys_have = clus_met._metric_json.keys()
    clus_metric_json_keys_desired = [u'tot_withinss',
                                     u'model_category',
                                     u'description',
                                     u'frame',
                                     u'model_checksum',
                                     u'MSE',
                                     u'__meta',
                                     u'scoring_time',
                                     u'betweenss',
                                     u'predictions',
                                     u'totss',
                                     u'model',
                                     u'duration_in_ms',
                                     u'frame_checksum',
                                     u'centroid_stats']
    clus_metric_diff = list(set(clus_metric_json_keys_have) - set(clus_metric_json_keys_desired))
    assert not clus_metric_diff, "There's a difference between the current ({0}) and the desired ({1}) clustering " \
                                "metric json. The difference is {2}".format(clus_metric_json_keys_have,
                                                                            clus_metric_json_keys_desired,
                                                                            clus_metric_diff)

if __name__ == "__main__":
    h2o.run_test(sys.argv, metric_json_check)

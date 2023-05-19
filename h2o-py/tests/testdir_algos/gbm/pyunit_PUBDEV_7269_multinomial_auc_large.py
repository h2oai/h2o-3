import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from tests.pyunit_utils import roc_auc_score


def multinomial_auc_prostate_gbm():
    data = h2o.import_file(pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    response_col = "GLEASON"
    data[response_col] = data[response_col].asfactor()
    
    predictors = ["RACE", "AGE", "PSA", "DPROS", "CAPSULE", "VOL", "DCAPS"]
    distribution = "multinomial"

    # train model
    gbm = H2OGradientBoostingEstimator(ntrees=1, max_depth=2, nfolds=3, distribution=distribution, auc_type="WEIGHTED_OVR")
    gbm.train(x=predictors, y=response_col, training_frame=data)

    gbm.show()

    # get result on training data from h2o
    cm = gbm.confusion_matrix(data)
    h2o_auc_table = gbm.multinomial_auc_table(train=True)
    h2o_aucpr_table = gbm.multinomial_aucpr_table(train=True)

    print(cm)
    print(h2o_auc_table.as_data_frame())
    print(h2o_aucpr_table.as_data_frame())

    h2o_ovr_macro_auc = h2o_auc_table[3][7]
    h2o_ovr_weighted_auc = h2o_auc_table[3][8]
    h2o_ovo_macro_auc = h2o_auc_table[3][30]
    h2o_ovo_weighted_auc = h2o_auc_table[3][31]

    h2o_ovr_weighted_aucpr = h2o_aucpr_table[3][8]

    h2o_default_auc = gbm.auc()
    h2o_default_aucpr = gbm.aucpr()

    print("default vs. table AUC "+str(h2o_ovr_weighted_auc)+" "+str(h2o_default_auc))
    print("default vs. table PR AUC "+str(h2o_ovr_weighted_aucpr)+" "+str(h2o_default_aucpr))

    # default should be ovr weighted 
    assert h2o_ovr_weighted_auc == h2o_default_auc, "default vs. table AUC "+str(h2o_ovr_weighted_auc)+" != "+str(h2o_default_auc)
    assert h2o_ovr_weighted_aucpr == h2o_default_aucpr, "default vs. table PR AUC "+str(h2o_ovr_weighted_aucpr)+" != "+str(h2o_default_aucpr)

    # transform data for sklearn
    prediction = gbm.predict(data).as_data_frame().iloc[:,1:]
    actual = data[response_col].as_data_frame().iloc[:, 0].tolist()

    # get result on training data from sklearn
    sklearn_ovr_macro_auc = roc_auc_score(actual, prediction, multi_class="ovr", average='macro')
    sklearn_ovr_weighted_auc = roc_auc_score(actual, prediction, multi_class="ovr", average='weighted')
    sklearn_ovo_macro_auc = roc_auc_score(actual, prediction, multi_class="ovo", average='macro')
    sklearn_ovo_weighted_auc = roc_auc_score(actual, prediction, multi_class="ovo", average='weighted')

    print("sklearn vs. h2o ovr macro:    "+str(sklearn_ovr_macro_auc)+" "+str(h2o_ovr_macro_auc))
    print("sklearn vs. h2o ovr weighted: "+str(sklearn_ovr_weighted_auc)+" "+str(h2o_ovr_weighted_auc))
    print("sklearn vs. h2o ovo macro:    "+str(sklearn_ovo_macro_auc)+" "+str(h2o_ovo_macro_auc))
    print("sklearn vs. h2o ovo weighted: "+str(sklearn_ovo_weighted_auc)+" "+str(h2o_ovo_weighted_auc))

    # compare results h2o vs sklearn
    precision = 1e-7
    assert abs(h2o_ovr_macro_auc - sklearn_ovr_macro_auc) < precision, "sklearn vs. h2o ovr macro: "+str(sklearn_ovr_macro_auc)+" != "+str(h2o_ovr_macro_auc)
    assert abs(h2o_ovr_weighted_auc - sklearn_ovr_weighted_auc) < precision, "sklearn vs. h2o ovr weighted: "+str(sklearn_ovr_weighted_auc)+" != "+str(h2o_ovr_weighted_auc)
    assert abs(h2o_ovo_macro_auc - sklearn_ovo_macro_auc) < precision, "sklearn vs. h2o ovo macro: "+str(sklearn_ovo_macro_auc)+" != "+str(h2o_ovo_macro_auc)
    assert abs(h2o_ovo_weighted_auc - sklearn_ovo_weighted_auc) < precision, "sklearn vs. h2o ovo weighted: "+str(sklearn_ovo_weighted_auc)+" != "+str(h2o_ovo_weighted_auc)

    # set auc_type 
    gbm = H2OGradientBoostingEstimator(ntrees=1, max_depth=2, nfolds=3, distribution=distribution, auc_type="MACRO_OVR")
    gbm.train(x=predictors, y=response_col, training_frame=data, validation_frame=data)

    h2o_auc_table = gbm.multinomial_auc_table(train=True)
    h2o_aucpr_table = gbm.multinomial_aucpr_table(train=True)

    h2o_ovr_macro_auc = h2o_auc_table[3][7]
    h2o_ovr_macro_aucpr = h2o_aucpr_table[3][7]

    h2o_default_auc = gbm.auc()
    h2o_default_aucpr = gbm.aucpr()

    assert abs(h2o_ovr_macro_auc - h2o_default_auc) < precision, "default auc vs. h2o ovr macro auc: "+str(sklearn_ovr_macro_auc)+" != "+str(h2o_default_auc)
    assert abs(h2o_ovr_macro_aucpr - h2o_default_aucpr) < precision, "default aucpr vs. h2o ovr macro aucpr: "+str(h2o_ovr_macro_aucpr)+" != "+str(h2o_default_aucpr)

    # test early stopping
    ntrees = 100
    gbm2 = H2OGradientBoostingEstimator(ntrees=ntrees, max_depth=2, nfolds=3, distribution=distribution, score_each_iteration=True, auc_type="MACRO_OVR", stopping_metric="AUC", stopping_rounds=3)
    gbm2.train(x=predictors, y=response_col, training_frame=data, validation_frame=data)
    assert ntrees > gbm2.score_history().shape[0], "Test early stopping: Training should start early."

    # test performance with different auc type
    perf2 = gbm.model_performance(data, auc_type="WEIGHTED_OVO")
    perf2_auc = perf2.auc()
    assert abs(h2o_ovo_weighted_auc - perf2_auc) < precision, "h2o ovo weighted vs. h2o performance ovo weighted: "+str(h2o_ovo_weighted_auc)+" != "+str(perf2_auc)
    
    # test peformance with no data and auc_type is set
    ntrees = 2
    gbm3 = H2OGradientBoostingEstimator(ntrees=ntrees, max_depth=2, nfolds=3, distribution=distribution)
    gbm3.train(x=predictors, y=response_col, training_frame=data, validation_frame=data)
    perf3 = gbm3.model_performance(train=True, auc_type="WEIGHTED_OVO")
    perf3_auc = perf3.auc()
    assert perf3_auc == "NaN", "AUC should be \"NaN\" because it is not set in model parameters and test_data is None"
    
    # test aucpr is not in cv summary
    print(gbm._model_json["output"]["cv_scoring_history"][0]._col_header)
    assert not "aucpr" in gbm.cross_validation_metrics_summary()[0], "The aucpr should not be in cross-validation metrics summary."
    assert "pr_auc" in gbm.cross_validation_metrics_summary()[0], "The pr_auc should be in cross-validation metrics summary."
    

if __name__ == "__main__":
    pyunit_utils.standalone_test(multinomial_auc_prostate_gbm)
else:
    multinomial_auc_prostate_gbm()

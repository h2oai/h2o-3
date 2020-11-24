from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from sklearn.metrics import roc_auc_score


def multinomial_auc_prostate_gbm():
    h2o.rapids('(setproperty "sys.ai.h2o.auc.maxClasses" "10")')
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
    h2o_auc_table = gbm.multinomial_auc_table(data)
    h2o_aucpr_table = gbm.multinomial_aucpr_table(data)

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

    h2o_auc_table = gbm.multinomial_auc_table(data)
    h2o_aucpr_table = gbm.multinomial_aucpr_table(data)

    h2o_ovr_macro_auc = h2o_auc_table[3][7]
    h2o_ovr_macro_aucpr = h2o_aucpr_table[3][7]

    h2o_default_auc = gbm.auc()
    h2o_default_aucpr = gbm.aucpr()

    print("default vs. table AUC "+str(h2o_ovr_macro_auc)+" "+str(h2o_default_auc))
    print("default vs. table PR AUC "+str(h2o_ovr_macro_aucpr)+" "+str(h2o_default_aucpr))
    

def multinomial_auc_cars_gbm():
    h2o.rapids('(setproperty "sys.ai.h2o.auc.maxClasses" "10")')
    data = h2o.import_file(pyunit_utils.locate("smalldata/junit/cars_20mpg.csv"))
    response_col = "cylinders"
    data[response_col] = data[response_col].asfactor()
    
    predictors = ["displacement", "power", "weight", "acceleration", "year"]
    distribution = "multinomial"
    
    # train model
    gbm = H2OGradientBoostingEstimator(ntrees=1, max_depth=2, nfolds=3, distribution=distribution, auc_type="WEIGHTED_OVR")
    gbm.train(x=predictors, y=response_col, training_frame=data, validation_frame=data)
    
    gbm.show()
    
    # get result on training data from h2o
    cm = gbm.confusion_matrix(data)
    h2o_auc_table = gbm.multinomial_auc_table(data)
    h2o_aucpr_table = gbm.multinomial_aucpr_table(data)

    print(cm)
    print(h2o_auc_table)
    print(h2o_aucpr_table)
    
    h2o_ovr_macro_auc = h2o_auc_table[3][5]
    h2o_ovr_weighted_auc = h2o_auc_table[3][6]
    h2o_ovo_macro_auc = h2o_auc_table[3][17]
    h2o_ovo_weighted_auc = h2o_auc_table[3][18]

    h2o_ovr_weighted_aucpr = h2o_aucpr_table[3][6]

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
    precision = 1e-8
    assert abs(h2o_ovr_macro_auc - sklearn_ovr_macro_auc) < precision, "sklearn vs. h2o ovr macro: "+str(sklearn_ovr_macro_auc)+" != "+str(h2o_ovr_macro_auc)
    assert abs(h2o_ovr_weighted_auc - sklearn_ovr_weighted_auc) < precision, "sklearn vs. h2o ovr weighted: "+str(sklearn_ovr_weighted_auc)+" != "+str(h2o_ovr_weighted_auc)
    assert abs(h2o_ovo_macro_auc - sklearn_ovo_macro_auc) < precision, "sklearn vs. h2o ovo macro: "+str(sklearn_ovo_macro_auc)+" != "+str(h2o_ovo_macro_auc)
    assert abs(h2o_ovo_weighted_auc - sklearn_ovo_weighted_auc) < precision, "sklearn vs. h2o ovo weighted: "+str(sklearn_ovo_weighted_auc)+" != "+str(h2o_ovo_weighted_auc)
    
    # set auc_type 
    gbm = H2OGradientBoostingEstimator(ntrees=1, max_depth=2, nfolds=3, distribution=distribution, auc_type="MACRO_OVR")
    gbm.train(x=predictors, y=response_col, training_frame=data)

    h2o_auc_table = gbm.multinomial_auc_table(data)
    h2o_aucpr_table = gbm.multinomial_aucpr_table(data)

    h2o_ovr_macro_auc = h2o_auc_table[3][5]
    h2o_ovr_macro_aucpr = h2o_aucpr_table[3][5]

    h2o_default_auc = gbm.auc()
    h2o_default_aucpr = gbm.aucpr()

    print("default vs. table AUC "+str(h2o_ovr_macro_auc)+" "+str(h2o_default_auc))
    print("default vs. table PR AUC "+str(h2o_ovr_macro_aucpr)+" "+str(h2o_default_aucpr))


if __name__ == "__main__":
    pyunit_utils.standalone_test(multinomial_auc_prostate_gbm)
    pyunit_utils.standalone_test(multinomial_auc_cars_gbm)
else:
    multinomial_auc_prostate_gbm()
    multinomial_auc_cars_gbm()

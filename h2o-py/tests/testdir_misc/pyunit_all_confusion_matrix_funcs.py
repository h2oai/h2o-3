from __future__ import print_function
from builtins import range
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils



import random

def all_confusion_matrix_funcs():
    
    

    metrics = ["recall", "specificity", "min_per_class_accuracy", "absolute_MCC", "precision", "accuracy", "f0point5", "f2", "f1"]
    train = [True, False]
    valid = [True, False]

    print("PARSING TRAINING DATA")
    air_train = h2o.import_file(path=pyunit_utils.locate("smalldata/airlines/AirlinesTrain.csv.zip"))

    print("PARSING TESTING DATA")
    air_test = h2o.import_file(path=pyunit_utils.locate("smalldata/airlines/AirlinesTest.csv.zip"))

    print()
    print("RUNNING FIRST GBM: ")
    print()
    gbm_bin = h2o.gbm(x=air_train[["Origin", "Dest", "Distance", "UniqueCarrier", "fMonth", "fDayofMonth","fDayOfWeek"]],
                      y=air_train["IsDepDelayed"].asfactor(),
                      validation_x=air_test[["Origin", "Dest", "Distance", "UniqueCarrier", "fMonth", "fDayofMonth",
                                         "fDayOfWeek"]],
                      validation_y=air_test["IsDepDelayed"].asfactor(),
                      distribution="bernoulli")

    print()
    print("RUNNING SECOND GBM: ")
    print()
    gbm_mult = h2o.gbm(x=air_train[["Origin", "Dest", "Distance", "UniqueCarrier", "IsDepDelayed", "fDayofMonth",
                                    "fMonth"]],
                      y=air_train["fDayOfWeek"].asfactor(),
                      validation_x=air_test[["Origin", "Dest", "Distance", "UniqueCarrier", "IsDepDelayed", "fDayofMonth",
                                             "fMonth"]],
                      validation_y=air_test["fDayOfWeek"].asfactor(),
                      distribution="multinomial")

    def dim_check(cm, m, t, v):
        assert len(cm) == 2 and len(cm[0]) == 2 and len(cm[1]) == 2, "incorrect confusion matrix dimensions " \
                                                                     "for metric/thresh: {0}, train: {1}, valid: " \
                                                                     "{2}".format(m, t, v)

    def type_check(cm, m, t, v):
        assert isinstance(cm[0][0], (int, float)) and isinstance(cm[0][1], (int, float)) and \
               isinstance(cm[1][0], (int, float)) and isinstance(cm[0][0], (int, float)), \
            "confusion matrix entries should be integers or floats but got {0}, {1}, {2}, {3}. metric/thresh: {4}, " \
            "train: {5}, valid: {6}".format(type(cm[0][0]), type(cm[0][1]), type(cm[1][0]), type(cm[1][1]), m,
                                            t, v)

    def count_check(cm, m, t, v):
        if v:
            assert cm[0][0] + cm[0][1] + cm[1][0] + cm[1][1] == air_test.nrow, \
                "incorrect confusion matrix elements: {0}, {1}, {2}, {3}. Should sum " \
                "to {4}. metric/thresh: {5}, train: {6}, valid: {7}".format(cm[0][0], cm[0][1], cm[1][0], cm[1][1],
                                                                     air_test.nrow, m, t, v)
        else:
            assert cm[0][0] + cm[0][1] + cm[1][0] + cm[1][1] == air_train.nrow, \
                "incorrect confusion matrix elements: {0}, {1}, {2}, {3}. Should sum " \
                "to {4}. metric/thresh: {5}, train: {6}, valid: {7}".format(cm[0][0], cm[0][1], cm[1][0], cm[1][1],
                                                                     air_train.nrow, m, t, v)

    # H2OBinomialModel.confusion_matrix()
    for m in metrics:
        for t in train:
            for v in valid:
                if t and v: continue
                cm = gbm_bin.confusion_matrix(metrics=m, train=t, valid=v)
                if cm:
                    cm = cm.to_list()
                    dim_check(cm, m, t, v)
                    type_check(cm, m, t, v)
                    count_check(cm, m, t, v)

    # H2OBinomialModel.confusion_matrix()
    for x in range(10):
        for t in train:
            for v in valid:
                if t and v: continue
                thresholds = [gbm_bin.find_threshold_by_max_metric(m,t,v) for m in
                              random.sample(metrics,random.randint(1,len(metrics)))]
                cms = gbm_bin.confusion_matrix(thresholds=thresholds, train=t, valid=v)
                if not isinstance(cms, list): cms = [cms]
                for idx, cm in enumerate(cms):
                    cm = cm.to_list()
                    dim_check(cm, thresholds[idx], t, v)
                    type_check(cm, thresholds[idx], t, v)
                    count_check(cm, thresholds[idx], t, v)

    # H2OMultinomialModel.confusion_matrix()
    cm = gbm_mult.confusion_matrix(data=air_test)
    cm_count = 0
    for r in range(7):
        for c in range(7):
            cm_count += cm.cell_values[r][c]
    assert cm_count == air_test.nrow, "incorrect confusion matrix elements. Should sum to {0}, but got {1}".\
        format(air_test.nrow, cm_count)

    # H2OBinomialModelMetrics.confusion_matrix()
    bin_perf = gbm_bin.model_performance(valid=True)
    for metric in metrics:
        cm = bin_perf.confusion_matrix(metrics=metric).to_list()
        dim_check(cm, metric, False, True)
        type_check(cm, metric, False, True)
        count_check(cm, metric, False, True)

    # H2OBinomialModelMetrics.confusion_matrix()
    bin_perf = gbm_bin.model_performance(train=True)
    for x in range(10):
        thresholds = [gbm_bin.find_threshold_by_max_metric(m,t,v) for m in
                      random.sample(metrics,random.randint(1,len(metrics)))]
        cms = bin_perf.confusion_matrix(thresholds=thresholds)
        if not isinstance(cms, list): cms = [cms]
        for idx, cm in enumerate(cms):
            cm = cm.to_list()
            dim_check(cm, thresholds[idx], True, False)
            type_check(cm, thresholds[idx], True, False)
            count_check(cm, thresholds[idx], True, False)

    # H2OMultinomialModelMetrics.confusion_matrix()
    mult_perf = gbm_mult.model_performance(valid=True)
    cm = mult_perf.confusion_matrix()
    cm_count = 0
    for r in range(7):
        for c in range(7):
            cm_count += cm.cell_values[r][c]
    assert cm_count == air_test.nrow, "incorrect confusion matrix elements. Should sum to {0}, but got {1}". \
        format(air_test.nrow, cm_count)



if __name__ == "__main__":
    pyunit_utils.standalone_test(all_confusion_matrix_funcs)
else:
    all_confusion_matrix_funcs()

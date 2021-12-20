import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator



def metric_accessors():

    cars = h2o.import_file(path=pyunit_utils.locate("smalldata/junit/cars_20mpg.csv"))
    r = cars[0].runif()
    train = cars[r > .2]
    valid = cars[r <= .2]

    # regression
    response_col = "economy"
    distribution = "gaussian"
    predictors = ["displacement","power","weight","acceleration","year"]

    gbm = H2OGradientBoostingEstimator(nfolds=3,
                                       distribution=distribution,
                                       fold_assignment="Random")
    gbm.train(x=predictors, y=response_col, training_frame=train, validation_frame=valid)

    # using list from http://docs.h2o.ai/h2o/latest-stable/h2o-docs/performance-and-prediction.html#regression
    for metric in ['r2', 'mse', 'rmse', 'rmsle', 'mae']:
        val = getattr(gbm, metric)()
        assert isinstance(val, float), "expected a float for metric {} but got {}".format(metric, val)

    #   mse
    mse1 = gbm.mse(train=True,  valid=False, xval=False)
    assert isinstance(mse1, float)

    mse2 = gbm.mse(train=False, valid=True,  xval=False)
    assert isinstance(mse2, float)

    mse3 = gbm.mse(train=False, valid=False, xval=True)
    assert isinstance(mse3, float)

    mse = gbm.mse(train=True,  valid=True,  xval=False)
    assert "train" in list(mse.keys()) and "valid" in list(mse.keys()), "expected training and validation metrics to be returned, but got {0}".format(list(mse.keys()))
    assert len(mse) == 2, "expected only training and validation metrics to be returned, but got {0}".format(list(mse.keys()))
    assert isinstance(mse["train"], float) and isinstance(mse["valid"], float), "expected training and validation metrics to be floats, but got {0} and {1}".format(type(mse["train"]), type(mse["valid"]))
    assert mse["valid"] == mse2

    mse = gbm.mse(train=True,  valid=False, xval=True)
    assert "train" in list(mse.keys()) and "xval" in list(mse.keys()), "expected training and cross validation metrics to be returned, but got {0}".format(list(mse.keys()))
    assert len(mse) == 2, "expected only training and cross validation metrics to be returned, but got {0}".format(list(mse.keys()))
    assert isinstance(mse["train"], float) and isinstance(mse["xval"], float), "expected training and cross validation metrics to be floats, but got {0} and {1}".format(type(mse["train"]), type(mse["xval"]))
    assert mse["xval"] == mse3

    mse = gbm.mse(train=True,  valid=True,  xval=True)
    assert "train" in list(mse.keys()) and "valid" in list(mse.keys()) and "xval" in list(mse.keys()), "expected training, validation, and cross validation metrics to be returned, but got {0}".format(list(mse.keys()))
    assert len(mse) == 3, "expected training, validation and cross validation metrics to be returned, but got {0}".format(list(mse.keys()))
    assert isinstance(mse["train"], float) and isinstance(mse["valid"], float) and isinstance(mse["xval"], float), "expected training, validation, and cross validation metrics to be floats, but got {0}, {1}, and {2}".format(type(mse["train"]), type(mse["valid"]), type(mse["xval"]))

    mse = gbm.mse(train=False, valid=False, xval=False) # default: return training metrics
    assert isinstance(mse, float)
    assert mse == mse1

    mse = gbm.mse(train=False, valid=True,  xval=True)
    assert "valid" in list(mse.keys()) and "xval" in list(mse.keys()), "expected validation and cross validation metrics to be returned, but got {0}".format(list(mse.keys()))
    assert len(mse) == 2, "expected validation and cross validation metrics to be returned, but got {0}".format(list(mse.keys()))
    assert isinstance(mse["valid"], float) and isinstance(mse["xval"], float), "validation and cross validation metrics to be floats, but got {0} and {1}".format(type(mse["valid"]), type(mse["xval"]))

    #   mean_residual_deviance
    mean_residual_deviance1 = gbm.mean_residual_deviance(train=True,  valid=False, xval=False)
    assert isinstance(mean_residual_deviance1, float)

    mean_residual_deviance2 = gbm.mean_residual_deviance(train=False, valid=True,  xval=False)
    assert isinstance(mean_residual_deviance2, float)

    mean_residual_deviance3 = gbm.mean_residual_deviance(train=False, valid=False, xval=True)
    assert isinstance(mean_residual_deviance3, float)

    mean_residual_deviance = gbm.mean_residual_deviance(train=True,  valid=True,  xval=False)
    assert "train" in list(mean_residual_deviance.keys()) and "valid" in list(mean_residual_deviance.keys()), "expected training and validation metrics to be returned, but got {0}".format(list(mean_residual_deviance.keys()))
    assert len(mean_residual_deviance) == 2, "expected only training and validation metrics to be returned, but got {0}".format(list(mean_residual_deviance.keys()))
    assert isinstance(mean_residual_deviance["train"], float) and isinstance(mean_residual_deviance["valid"], float), "expected training and validation metrics to be floats, but got {0} and {1}".format(type(mean_residual_deviance["train"]), type(mean_residual_deviance["valid"]))
    assert mean_residual_deviance["valid"] == mean_residual_deviance2

    mean_residual_deviance = gbm.mean_residual_deviance(train=True,  valid=False, xval=True)
    assert "train" in list(mean_residual_deviance.keys()) and "xval" in list(mean_residual_deviance.keys()), "expected training and cross validation metrics to be returned, but got {0}".format(list(mean_residual_deviance.keys()))
    assert len(mean_residual_deviance) == 2, "expected only training and cross validation metrics to be returned, but got {0}".format(list(mean_residual_deviance.keys()))
    assert isinstance(mean_residual_deviance["train"], float) and isinstance(mean_residual_deviance["xval"], float), "expected training and cross validation metrics to be floats, but got {0} and {1}".format(type(mean_residual_deviance["train"]), type(mean_residual_deviance["xval"]))
    assert mean_residual_deviance["xval"] == mean_residual_deviance3

    mean_residual_deviance = gbm.mean_residual_deviance(train=True,  valid=True,  xval=True)
    assert "train" in list(mean_residual_deviance.keys()) and "valid" in list(mean_residual_deviance.keys()) and "xval" in list(mean_residual_deviance.keys()), "expected training, validation, and cross validation metrics to be returned, but got {0}".format(list(mean_residual_deviance.keys()))
    assert len(mean_residual_deviance) == 3, "expected training, validation and cross validation metrics to be returned, but got {0}".format(list(mean_residual_deviance.keys()))
    assert isinstance(mean_residual_deviance["train"], float) and isinstance(mean_residual_deviance["valid"], float) and isinstance(mean_residual_deviance["xval"], float), "expected training, validation, and cross validation metrics to be floats, but got {0}, {1}, and {2}".format(type(mean_residual_deviance["train"]), type(mean_residual_deviance["valid"]), type(mean_residual_deviance["xval"]))

    mean_residual_deviance = gbm.mean_residual_deviance(train=False, valid=False, xval=False) # default: return training metrics
    assert isinstance(mean_residual_deviance, float)
    assert mean_residual_deviance == mean_residual_deviance1

    mean_residual_deviance = gbm.mean_residual_deviance(train=False, valid=True,  xval=True)
    assert "valid" in list(mean_residual_deviance.keys()) and "xval" in list(mean_residual_deviance.keys()), "expected validation and cross validation metrics to be returned, but got {0}".format(list(mean_residual_deviance.keys()))
    assert len(mean_residual_deviance) == 2, "expected validation and cross validation metrics to be returned, but got {0}".format(list(mean_residual_deviance.keys()))
    assert isinstance(mean_residual_deviance["valid"], float) and isinstance(mean_residual_deviance["xval"], float), "validation and cross validation metrics to be floats, but got {0} and {1}".format(type(mean_residual_deviance["valid"]), type(mean_residual_deviance["xval"]))


    # binomial
    cars = h2o.import_file(path=pyunit_utils.locate("smalldata/junit/cars_20mpg.csv"))
    cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
    r = cars[0].runif()
    train = cars[r > .2]
    valid = cars[r <= .2]
    response_col = "economy_20mpg"
    distribution = "bernoulli"
    predictors = ["displacement","power","weight","acceleration","year"]
    gbm = H2OGradientBoostingEstimator(nfolds=3, distribution=distribution, fold_assignment="Random")
    gbm.train(y=response_col, x=predictors, validation_frame=valid, training_frame=train)

    # using list from http://docs.h2o.ai/h2o/latest-stable/h2o-docs/performance-and-prediction.html#classification
    # + common ones
    for metric in ['gini', 'logloss', 'auc', 'aucpr', 'mse', 'rmse']:
        val = getattr(gbm, metric)()
        assert isinstance(val, float), "expected a float for metric {} but got {}".format(metric, val)

    for metric in ['mcc', 'F1', 'F0point5', 'F2', 'accuracy', 'mean_per_class_error']:
        val = getattr(gbm, metric)()[0][1]
        assert isinstance(val, float), "expected a float for metric {} but got {}".format(metric, val)

    #   auc
    auc1 = gbm.auc(train=True,  valid=False, xval=False)
    assert isinstance(auc1, float)

    auc2 = gbm.auc(train=False, valid=True,  xval=False)
    assert isinstance(auc2, float)

    auc3 = gbm.auc(train=False, valid=False, xval=True)
    assert isinstance(auc3, float)

    auc = gbm.auc(train=True,  valid=True,  xval=False)
    assert "train" in list(auc.keys()) and "valid" in list(auc.keys()), "expected training and validation metrics to be returned, but got {0}".format(list(auc.keys()))
    assert len(auc) == 2, "expected only training and validation metrics to be returned, but got {0}".format(list(auc.keys()))
    assert isinstance(auc["train"], float) and isinstance(auc["valid"], float), "expected training and validation metrics to be floats, but got {0} and {1}".format(type(auc["train"]), type(auc["valid"]))
    assert auc["valid"] == auc2

    auc = gbm.auc(train=True,  valid=False, xval=True)
    assert "train" in list(auc.keys()) and "xval" in list(auc.keys()), "expected training and cross validation metrics to be returned, but got {0}".format(list(auc.keys()))
    assert len(auc) == 2, "expected only training and cross validation metrics to be returned, but got {0}".format(list(auc.keys()))
    assert isinstance(auc["train"], float) and isinstance(auc["xval"], float), "expected training and cross validation metrics to be floats, but got {0} and {1}".format(type(auc["train"]), type(auc["xval"]))
    assert auc["xval"] == auc3

    auc = gbm.auc(train=True,  valid=True,  xval=True)
    assert "train" in list(auc.keys()) and "valid" in list(auc.keys()) and "xval" in list(auc.keys()), "expected training, validation, and cross validation metrics to be returned, but got {0}".format(list(auc.keys()))
    assert len(auc) == 3, "expected training, validation and cross validation metrics to be returned, but got {0}".format(list(auc.keys()))
    assert isinstance(auc["train"], float) and isinstance(auc["valid"], float) and isinstance(auc["xval"], float), "expected training, validation, and cross validation metrics to be floats, but got {0}, {1}, and {2}".format(type(auc["train"]), type(auc["valid"]), type(auc["xval"]))

    auc = gbm.auc(train=False, valid=False, xval=False) # default: return training metrics
    assert isinstance(auc, float)
    assert auc == auc1

    auc = gbm.auc(train=False, valid=True,  xval=True)
    assert "valid" in list(auc.keys()) and "xval" in list(auc.keys()), "expected validation and cross validation metrics to be returned, but got {0}".format(list(auc.keys()))
    assert len(auc) == 2, "expected validation and cross validation metrics to be returned, but got {0}".format(list(auc.keys()))
    assert isinstance(auc["valid"], float) and isinstance(auc["xval"], float), "validation and cross validation metrics to be floats, but got {0} and {1}".format(type(auc["valid"]), type(auc["xval"]))

    # roc
    (fprs1, tprs1) = gbm.roc(train=True,  valid=False, xval=False)
    assert isinstance(fprs1, list)
    assert isinstance(tprs1, list)

    (fprs2, tprs2) = gbm.roc(train=False, valid=True,  xval=False)
    assert isinstance(fprs2, list)
    assert isinstance(tprs2, list)

    (fprs3, tprs3) = gbm.roc(train=False, valid=False, xval=True)
    assert isinstance(fprs3, list)
    assert isinstance(tprs3, list)

    roc = gbm.roc(train=True,  valid=True,  xval=False)
    assert "train" in list(roc.keys()) and "valid" in list(roc.keys()), "expected training and validation metrics to be returned, but got {0}".format(list(roc.keys()))
    assert len(roc) == 2, "expected only training and validation metrics to be returned, but got {0}".format(list(roc.keys()))
    assert isinstance(roc["train"], tuple) and isinstance(roc["valid"], tuple), "expected training and validation metrics to be tuples, but got {0} and {1}".format(type(roc["train"]), type(roc["valid"]))
    assert roc["valid"][0] == fprs2
    assert roc["valid"][1] == tprs2

    roc = gbm.roc(train=True,  valid=False, xval=True)
    assert "train" in list(roc.keys()) and "xval" in list(roc.keys()), "expected training and cross validation metrics to be returned, but got {0}".format(list(roc.keys()))
    assert len(roc) == 2, "expected only training and cross validation metrics to be returned, but got {0}".format(list(roc.keys()))
    assert isinstance(roc["train"], tuple) and isinstance(roc["xval"], tuple), "expected training and cross validation metrics to be tuples, but got {0} and {1}".format(type(roc["train"]), type(roc["xval"]))
    assert roc["xval"][0] == fprs3
    assert roc["xval"][1] == tprs3

    roc = gbm.roc(train=True,  valid=True,  xval=True)
    assert "train" in list(roc.keys()) and "valid" in list(roc.keys()) and "xval" in list(roc.keys()), "expected training, validation, and cross validation metrics to be returned, but got {0}".format(list(roc.keys()))
    assert len(roc) == 3, "expected training, validation and cross validation metrics to be returned, but got {0}".format(list(roc.keys()))
    assert isinstance(roc["train"], tuple) and isinstance(roc["valid"], tuple) and isinstance(roc["xval"], tuple), "expected training, validation, and cross validation metrics to be tuples, but got {0}, {1}, and {2}".format(type(roc["train"]), type(roc["valid"]), type(roc["xval"]))

    (fprs, tprs) = gbm.roc(train=False, valid=False, xval=False) # default: return training metrics
    assert isinstance(fprs, list)
    assert isinstance(tprs, list)
    assert fprs == fprs1
    assert tprs == tprs1

    roc = gbm.roc(train=False, valid=True,  xval=True)
    assert "valid" in list(roc.keys()) and "xval" in list(roc.keys()), "expected validation and cross validation metrics to be returned, but got {0}".format(list(roc.keys()))
    assert len(roc) == 2, "expected validation and cross validation metrics to be returned, but got {0}".format(list(roc.keys()))
    assert isinstance(roc["valid"], tuple) and isinstance(roc["xval"], tuple), "validation and cross validation metrics to be tuples, but got {0} and {1}".format(type(roc["valid"]), type(roc["xval"]))

    #   logloss
    logloss1 = gbm.logloss(train=True,  valid=False, xval=False)
    assert isinstance(logloss1, float)

    logloss2 = gbm.logloss(train=False, valid=True,  xval=False)
    assert isinstance(logloss2, float)

    logloss3 = gbm.logloss(train=False, valid=False, xval=True)
    assert isinstance(logloss3, float)

    logloss = gbm.logloss(train=True,  valid=True,  xval=False)
    assert "train" in list(logloss.keys()) and "valid" in list(logloss.keys()), "expected training and validation metrics to be returned, but got {0}".format(list(logloss.keys()))
    assert len(logloss) == 2, "expected only training and validation metrics to be returned, but got {0}".format(list(logloss.keys()))
    assert isinstance(logloss["train"], float) and isinstance(logloss["valid"], float), "expected training and validation metrics to be floats, but got {0} and {1}".format(type(logloss["train"]), type(logloss["valid"]))
    assert logloss["valid"] == logloss2

    logloss = gbm.logloss(train=True,  valid=False, xval=True)
    assert "train" in list(logloss.keys()) and "xval" in list(logloss.keys()), "expected training and cross validation metrics to be returned, but got {0}".format(list(logloss.keys()))
    assert len(logloss) == 2, "expected only training and cross validation metrics to be returned, but got {0}".format(list(logloss.keys()))
    assert isinstance(logloss["train"], float) and isinstance(logloss["xval"], float), "expected training and cross validation metrics to be floats, but got {0} and {1}".format(type(logloss["train"]), type(logloss["xval"]))
    assert logloss["xval"] == logloss3

    logloss = gbm.logloss(train=True,  valid=True,  xval=True)
    assert "train" in list(logloss.keys()) and "valid" in list(logloss.keys()) and "xval" in list(logloss.keys()), "expected training, validation, and cross validation metrics to be returned, but got {0}".format(list(logloss.keys()))
    assert len(logloss) == 3, "expected training, validation and cross validation metrics to be returned, but got {0}".format(list(logloss.keys()))
    assert isinstance(logloss["train"], float) and isinstance(logloss["valid"], float) and isinstance(logloss["xval"], float), "expected training, validation, and cross validation metrics to be floats, but got {0}, {1}, and {2}".format(type(logloss["train"]), type(logloss["valid"]), type(logloss["xval"]))

    logloss = gbm.logloss(train=False, valid=False, xval=False) # default: return training metrics
    assert isinstance(logloss, float)
    assert logloss == logloss1

    logloss = gbm.logloss(train=False, valid=True,  xval=True)
    assert "valid" in list(logloss.keys()) and "xval" in list(logloss.keys()), "expected validation and cross validation metrics to be returned, but got {0}".format(list(logloss.keys()))
    assert len(logloss) == 2, "expected validation and cross validation metrics to be returned, but got {0}".format(list(logloss.keys()))
    assert isinstance(logloss["valid"], float) and isinstance(logloss["xval"], float), "validation and cross validation metrics to be floats, but got {0} and {1}".format(type(logloss["valid"]), type(logloss["xval"]))

    #   gini
    gini1 = gbm.gini(train=True,  valid=False, xval=False)
    assert isinstance(gini1, float)

    gini2 = gbm.gini(train=False, valid=True,  xval=False)
    assert isinstance(gini2, float)

    gini3 = gbm.gini(train=False, valid=False, xval=True)
    assert isinstance(gini3, float)

    gini = gbm.gini(train=True,  valid=True,  xval=False)
    assert "train" in list(gini.keys()) and "valid" in list(gini.keys()), "expected training and validation metrics to be returned, but got {0}".format(list(gini.keys()))
    assert len(gini) == 2, "expected only training and validation metrics to be returned, but got {0}".format(list(gini.keys()))
    assert isinstance(gini["train"], float) and isinstance(gini["valid"], float), "expected training and validation metrics to be floats, but got {0} and {1}".format(type(gini["train"]), type(gini["valid"]))
    assert gini["valid"] == gini2

    gini = gbm.gini(train=True,  valid=False, xval=True)
    assert "train" in list(gini.keys()) and "xval" in list(gini.keys()), "expected training and cross validation metrics to be returned, but got {0}".format(list(gini.keys()))
    assert len(gini) == 2, "expected only training and cross validation metrics to be returned, but got {0}".format(list(gini.keys()))
    assert isinstance(gini["train"], float) and isinstance(gini["xval"], float), "expected training and cross validation metrics to be floats, but got {0} and {1}".format(type(gini["train"]), type(gini["xval"]))
    assert gini["xval"] == gini3

    gini = gbm.gini(train=True,  valid=True,  xval=True)
    assert "train" in list(gini.keys()) and "valid" in list(gini.keys()) and "xval" in list(gini.keys()), "expected training, validation, and cross validation metrics to be returned, but got {0}".format(list(gini.keys()))
    assert len(gini) == 3, "expected training, validation and cross validation metrics to be returned, but got {0}".format(list(gini.keys()))
    assert isinstance(gini["train"], float) and isinstance(gini["valid"], float) and isinstance(gini["xval"], float), "expected training, validation, and cross validation metrics to be floats, but got {0}, {1}, and {2}".format(type(gini["train"]), type(gini["valid"]), type(gini["xval"]))

    gini = gbm.gini(train=False, valid=False, xval=False) # default: return training metrics
    assert isinstance(gini, float)
    assert gini == gini1

    gini = gbm.gini(train=False, valid=True,  xval=True)
    assert "valid" in list(gini.keys()) and "xval" in list(gini.keys()), "expected validation and cross validation metrics to be returned, but got {0}".format(list(gini.keys()))
    assert len(gini) == 2, "expected validation and cross validation metrics to be returned, but got {0}".format(list(gini.keys()))
    assert isinstance(gini["valid"], float) and isinstance(gini["xval"], float), "validation and cross validation metrics to be floats, but got {0} and {1}".format(type(gini["valid"]), type(gini["xval"]))

    #   F1
    F11 = gbm.F1(train=True,  valid=False, xval=False)
    F12 = gbm.F1(train=False, valid=True,  xval=False)
    F13 = gbm.F1(train=False, valid=False, xval=True)
    F1 = gbm.F1(train=True,  valid=True,  xval=False)
    F1 = gbm.F1(train=True,  valid=False, xval=True)
    F1 = gbm.F1(train=True,  valid=True,  xval=True)
    F1 = gbm.F1(train=False, valid=False, xval=False) # default: return training metrics
    F1 = gbm.F1(train=False, valid=True,  xval=True)

    #   F0point5
    F0point51 = gbm.F0point5(train=True,  valid=False, xval=False)
    F0point52 = gbm.F0point5(train=False, valid=True,  xval=False)
    F0point53 = gbm.F0point5(train=False, valid=False, xval=True)
    F0point5 = gbm.F0point5(train=True,  valid=True,  xval=False)
    F0point5 = gbm.F0point5(train=True,  valid=False, xval=True)
    F0point5 = gbm.F0point5(train=True,  valid=True,  xval=True)
    F0point5 = gbm.F0point5(train=False, valid=False, xval=False) # default: return training metrics
    F0point5 = gbm.F0point5(train=False, valid=True,  xval=True)

    #   F2
    F21 = gbm.F2(train=True,  valid=False, xval=False)
    F22 = gbm.F2(train=False, valid=True,  xval=False)
    F23 = gbm.F2(train=False, valid=False, xval=True)
    F2 = gbm.F2(train=True,  valid=True,  xval=False)
    F2 = gbm.F2(train=True,  valid=False, xval=True)
    F2 = gbm.F2(train=True,  valid=True,  xval=True)
    F2 = gbm.F2(train=False, valid=False, xval=False) # default: return training metrics
    F2 = gbm.F2(train=False, valid=True,  xval=True)

    #   accuracy
    accuracy1 = gbm.accuracy(train=True,  valid=False, xval=False)
    accuracy2 = gbm.accuracy(train=False, valid=True,  xval=False)
    accuracy3 = gbm.accuracy(train=False, valid=False, xval=True)
    accuracy = gbm.accuracy(train=True,  valid=True,  xval=False)
    accuracy = gbm.accuracy(train=True,  valid=False, xval=True)
    accuracy = gbm.accuracy(train=True,  valid=True,  xval=True)
    accuracy = gbm.accuracy(train=False, valid=False, xval=False) # default: return training metrics
    accuracy = gbm.accuracy(train=False, valid=True,  xval=True)

    #   error
    error1 = gbm.error(train=True,  valid=False, xval=False)
    error2 = gbm.error(train=False, valid=True,  xval=False)
    error3 = gbm.error(train=False, valid=False, xval=True)
    error = gbm.error(train=True,  valid=True,  xval=False)
    error = gbm.error(train=True,  valid=False, xval=True)
    error = gbm.error(train=True,  valid=True,  xval=True)
    error = gbm.error(train=False, valid=False, xval=False) # default: return training metrics
    error = gbm.error(train=False, valid=True,  xval=True)

    #   precision
    precision1 = gbm.precision(train=True,  valid=False, xval=False)
    precision2 = gbm.precision(train=False, valid=True,  xval=False)
    precision3 = gbm.precision(train=False, valid=False, xval=True)
    precision = gbm.precision(train=True,  valid=True,  xval=False)
    precision = gbm.precision(train=True,  valid=False, xval=True)
    precision = gbm.precision(train=True,  valid=True,  xval=True)
    precision = gbm.precision(train=False, valid=False, xval=False) # default: return training metrics
    precision = gbm.precision(train=False, valid=True,  xval=True)

    #   mcc
    mcc1 = gbm.mcc(train=True,  valid=False, xval=False)
    mcc2 = gbm.mcc(train=False, valid=True,  xval=False)
    mcc3 = gbm.mcc(train=False, valid=False, xval=True)
    mcc = gbm.mcc(train=True,  valid=True,  xval=False)
    mcc = gbm.mcc(train=True,  valid=False, xval=True)
    mcc = gbm.mcc(train=True,  valid=True,  xval=True)
    mcc = gbm.mcc(train=False, valid=False, xval=False) # default: return training metrics
    mcc = gbm.mcc(train=False, valid=True,  xval=True)

    #   max_per_class_error
    max_per_class_error1 = gbm.max_per_class_error(train=True,  valid=False, xval=False)
    max_per_class_error2 = gbm.max_per_class_error(train=False, valid=True,  xval=False)
    max_per_class_error3 = gbm.max_per_class_error(train=False, valid=False, xval=True)
    max_per_class_error = gbm.max_per_class_error(train=True,  valid=True,  xval=False)
    max_per_class_error = gbm.max_per_class_error(train=True,  valid=False, xval=True)
    max_per_class_error = gbm.max_per_class_error(train=True,  valid=True,  xval=True)
    max_per_class_error = gbm.max_per_class_error(train=False, valid=False, xval=False) # default: return training metrics
    max_per_class_error = gbm.max_per_class_error(train=False, valid=True,  xval=True)

    #   mean_per_class_error
    mean_per_class_error1 = gbm.mean_per_class_error(train=True,  valid=False, xval=False)
    mean_per_class_error2 = gbm.mean_per_class_error(train=False, valid=True,  xval=False)
    mean_per_class_error3 = gbm.mean_per_class_error(train=False, valid=False, xval=True)
    mean_per_class_error = gbm.mean_per_class_error(train=True,  valid=True,  xval=False)
    mean_per_class_error = gbm.mean_per_class_error(train=True,  valid=False, xval=True)
    mean_per_class_error = gbm.mean_per_class_error(train=True,  valid=True,  xval=True)
    mean_per_class_error = gbm.mean_per_class_error(train=False, valid=False, xval=False) # default: return training metrics
    mean_per_class_error = gbm.mean_per_class_error(train=False, valid=True,  xval=True)

    #   confusion_matrix
    confusion_matrix1 = gbm.confusion_matrix(train=True,  valid=False, xval=False)
    confusion_matrix2 = gbm.confusion_matrix(train=False, valid=True,  xval=False)
    confusion_matrix3 = gbm.confusion_matrix(train=False, valid=False, xval=True)
    confusion_matrix = gbm.confusion_matrix(train=True,  valid=True,  xval=False)
    confusion_matrix = gbm.confusion_matrix(train=True,  valid=False, xval=True)
    confusion_matrix = gbm.confusion_matrix(train=True,  valid=True,  xval=True)
    confusion_matrix = gbm.confusion_matrix(train=False, valid=False, xval=False) # default: return training metrics
    confusion_matrix = gbm.confusion_matrix(train=False, valid=True,  xval=True)


    # #   plot
    # plot1 = gbm.plot(train=True,  valid=False, xval=False)
    # plot2 = gbm.plot(train=False, valid=True,  xval=False)
    # plot3 = gbm.plot(train=False, valid=False, xval=True)
    # plot = gbm.plot(train=True,  valid=True,  xval=False)
    # plot = gbm.plot(train=True,  valid=False, xval=True)
    # plot = gbm.plot(train=True,  valid=True,  xval=True)
    # plot = gbm.plot(train=False, valid=False, xval=False) # default: return training metrics
    # plot = gbm.plot(train=False, valid=True,  xval=True)

    # #   tpr
    tpr1 = gbm.tpr(train=True,  valid=False, xval=False)
    tpr2 = gbm.tpr(train=False, valid=True,  xval=False)
    tpr3 = gbm.tpr(train=False, valid=False, xval=True)
    tpr = gbm.tpr(train=True,  valid=True,  xval=False)
    tpr = gbm.tpr(train=True,  valid=False, xval=True)
    tpr = gbm.tpr(train=True,  valid=True,  xval=True)
    tpr = gbm.tpr(train=False, valid=False, xval=False) # default: return training metrics
    tpr = gbm.tpr(train=False, valid=True,  xval=True)
    #
    # #   tnr
    tnr1 = gbm.tnr(train=True,  valid=False, xval=False)
    tnr2 = gbm.tnr(train=False, valid=True,  xval=False)
    tnr3 = gbm.tnr(train=False, valid=False, xval=True)
    tnr = gbm.tnr(train=True,  valid=True,  xval=False)
    tnr = gbm.tnr(train=True,  valid=False, xval=True)
    tnr = gbm.tnr(train=True,  valid=True,  xval=True)
    tnr = gbm.tnr(train=False, valid=False, xval=False) # default: return training metrics
    tnr = gbm.tnr(train=False, valid=True,  xval=True)
    #
    # #   fnr
    fnr1 = gbm.fnr(train=True,  valid=False, xval=False)
    fnr2 = gbm.fnr(train=False, valid=True,  xval=False)
    fnr3 = gbm.fnr(train=False, valid=False, xval=True)
    fnr = gbm.fnr(train=True,  valid=True,  xval=False)
    fnr = gbm.fnr(train=True,  valid=False, xval=True)
    fnr = gbm.fnr(train=True,  valid=True,  xval=True)
    fnr = gbm.fnr(train=False, valid=False, xval=False) # default: return training metrics
    fnr = gbm.fnr(train=False, valid=True,  xval=True)
    #
    # #   fpr
    fpr1 = gbm.fpr(train=True,  valid=False, xval=False)
    fpr2 = gbm.fpr(train=False, valid=True,  xval=False)
    fpr3 = gbm.fpr(train=False, valid=False, xval=True)
    fpr = gbm.fpr(train=True,  valid=True,  xval=False)
    fpr = gbm.fpr(train=True,  valid=False, xval=True)
    fpr = gbm.fpr(train=True,  valid=True,  xval=True)
    fpr = gbm.fpr(train=False, valid=False, xval=False) # default: return training metrics
    fpr = gbm.fpr(train=False, valid=True,  xval=True)



    # multinomial
    cars = h2o.import_file(path=pyunit_utils.locate("smalldata/junit/cars_20mpg.csv"))
    cars["cylinders"] = cars["cylinders"].asfactor()
    r = cars[0].runif()
    train = cars[r > .2]
    valid = cars[r <= .2]
    response_col = "cylinders"
    distribution = "multinomial"
    predictors = ["displacement","power","weight","acceleration","year"]
    gbm = H2OGradientBoostingEstimator(nfolds=3, distribution=distribution, fold_assignment="Random")
    gbm.train(x=predictors,y=response_col, training_frame=train, validation_frame=valid)

    # using list from http://docs.h2o.ai/h2o/latest-stable/h2o-docs/performance-and-prediction.html#classification
    # + common ones
    for metric in ['logloss', 'mse', 'rmse', 'mean_per_class_error']:
        val = getattr(gbm, metric)()
        assert isinstance(val, float), "expected a float for metric {} but got {}".format(metric, val)

    # for metric in []:
    #     val = getattr(gbm, metric)()[0][1]
    #     assert isinstance(val, float), "expected a float for metric {} but got {}".format(metric, val)

    #   mse
    mse1 = gbm.mse(train=True,  valid=False, xval=False)
    assert isinstance(mse1, float)

    mse2 = gbm.mse(train=False, valid=True,  xval=False)
    assert isinstance(mse2, float)

    mse3 = gbm.mse(train=False, valid=False, xval=True)
    assert isinstance(mse3, float)

    mse = gbm.mse(train=True,  valid=True,  xval=False)
    assert "train" in list(mse.keys()) and "valid" in list(mse.keys()), "expected training and validation metrics to be returned, but got {0}".format(list(mse.keys()))
    assert len(mse) == 2, "expected only training and validation metrics to be returned, but got {0}".format(list(mse.keys()))
    assert isinstance(mse["train"], float) and isinstance(mse["valid"], float), "expected training and validation metrics to be floats, but got {0} and {1}".format(type(mse["train"]), type(mse["valid"]))
    assert mse["valid"] == mse2

    mse = gbm.mse(train=True,  valid=False, xval=True)
    assert "train" in list(mse.keys()) and "xval" in list(mse.keys()), "expected training and cross validation metrics to be returned, but got {0}".format(list(mse.keys()))
    assert len(mse) == 2, "expected only training and cross validation metrics to be returned, but got {0}".format(list(mse.keys()))
    assert isinstance(mse["train"], float) and isinstance(mse["xval"], float), "expected training and cross validation metrics to be floats, but got {0} and {1}".format(type(mse["train"]), type(mse["xval"]))
    assert mse["xval"] == mse3

    mse = gbm.mse(train=True,  valid=True,  xval=True)
    assert "train" in list(mse.keys()) and "valid" in list(mse.keys()) and "xval" in list(mse.keys()), "expected training, validation, and cross validation metrics to be returned, but got {0}".format(list(mse.keys()))
    assert len(mse) == 3, "expected training, validation and cross validation metrics to be returned, but got {0}".format(list(mse.keys()))
    assert isinstance(mse["train"], float) and isinstance(mse["valid"], float) and isinstance(mse["xval"], float), "expected training, validation, and cross validation metrics to be floats, but got {0}, {1}, and {2}".format(type(mse["train"]), type(mse["valid"]), type(mse["xval"]))

    mse = gbm.mse(train=False, valid=False, xval=False) # default: return training metrics
    assert isinstance(mse, float)
    assert mse == mse1

    mse = gbm.mse(train=False, valid=True,  xval=True)
    assert "valid" in list(mse.keys()) and "xval" in list(mse.keys()), "expected validation and cross validation metrics to be returned, but got {0}".format(list(mse.keys()))
    assert len(mse) == 2, "expected validation and cross validation metrics to be returned, but got {0}".format(list(mse.keys()))
    assert isinstance(mse["valid"], float) and isinstance(mse["xval"], float), "validation and cross validation metrics to be floats, but got {0} and {1}".format(type(mse["valid"]), type(mse["xval"]))

    #   logloss
    logloss1 = gbm.logloss(train=True,  valid=False, xval=False)
    assert isinstance(logloss1, float)

    logloss2 = gbm.logloss(train=False, valid=True,  xval=False)
    assert isinstance(logloss2, float)

    logloss3 = gbm.logloss(train=False, valid=False, xval=True)
    assert isinstance(logloss3, float)

    logloss = gbm.logloss(train=True,  valid=True,  xval=False)
    assert "train" in list(logloss.keys()) and "valid" in list(logloss.keys()), "expected training and validation metrics to be returned, but got {0}".format(list(logloss.keys()))
    assert len(logloss) == 2, "expected only training and validation metrics to be returned, but got {0}".format(list(logloss.keys()))
    assert isinstance(logloss["train"], float) and isinstance(logloss["valid"], float), "expected training and validation metrics to be floats, but got {0} and {1}".format(type(logloss["train"]), type(logloss["valid"]))
    assert logloss["valid"] == logloss2

    logloss = gbm.logloss(train=True,  valid=False, xval=True)
    assert "train" in list(logloss.keys()) and "xval" in list(logloss.keys()), "expected training and cross validation metrics to be returned, but got {0}".format(list(logloss.keys()))
    assert len(logloss) == 2, "expected only training and cross validation metrics to be returned, but got {0}".format(list(logloss.keys()))
    assert isinstance(logloss["train"], float) and isinstance(logloss["xval"], float), "expected training and cross validation metrics to be floats, but got {0} and {1}".format(type(logloss["train"]), type(logloss["xval"]))
    assert logloss["xval"] == logloss3

    logloss = gbm.logloss(train=True,  valid=True,  xval=True)
    assert "train" in list(logloss.keys()) and "valid" in list(logloss.keys()) and "xval" in list(logloss.keys()), "expected training, validation, and cross validation metrics to be returned, but got {0}".format(list(logloss.keys()))
    assert len(logloss) == 3, "expected training, validation and cross validation metrics to be returned, but got {0}".format(list(logloss.keys()))
    assert isinstance(logloss["train"], float) and isinstance(logloss["valid"], float) and isinstance(logloss["xval"], float), "expected training, validation, and cross validation metrics to be floats, but got {0}, {1}, and {2}".format(type(logloss["train"]), type(logloss["valid"]), type(logloss["xval"]))

    logloss = gbm.logloss(train=False, valid=False, xval=False) # default: return training metrics
    assert isinstance(logloss, float)
    assert logloss == logloss1

    logloss = gbm.logloss(train=False, valid=True,  xval=True)
    assert "valid" in list(logloss.keys()) and "xval" in list(logloss.keys()), "expected validation and cross validation metrics to be returned, but got {0}".format(list(logloss.keys()))
    assert len(logloss) == 2, "expected validation and cross validation metrics to be returned, but got {0}".format(list(logloss.keys()))
    assert isinstance(logloss["valid"], float) and isinstance(logloss["xval"], float), "validation and cross validation metrics to be floats, but got {0} and {1}".format(type(logloss["valid"]), type(logloss["xval"]))

    #   hit_ratio_table
    hit_ratio_table1 = gbm.hit_ratio_table(train=True,  valid=False, xval=False)
    hit_ratio_table2 = gbm.hit_ratio_table(train=False, valid=True,  xval=False)
    hit_ratio_table3 = gbm.hit_ratio_table(train=False, valid=False, xval=True)
    hit_ratio_table = gbm.hit_ratio_table(train=True,  valid=True,  xval=False)
    hit_ratio_table = gbm.hit_ratio_table(train=True,  valid=False, xval=True)
    hit_ratio_table = gbm.hit_ratio_table(train=True,  valid=True,  xval=True)
    hit_ratio_table = gbm.hit_ratio_table(train=False, valid=False, xval=False) # default: return training metrics
    hit_ratio_table = gbm.hit_ratio_table(train=False, valid=True,  xval=True)

    #   mean_per_class_error
    mean_per_class_error1 = gbm.mean_per_class_error(train=True,  valid=False, xval=False)
    mean_per_class_error2 = gbm.mean_per_class_error(train=False, valid=True,  xval=False)
    mean_per_class_error3 = gbm.mean_per_class_error(train=False, valid=False, xval=True)
    mean_per_class_error = gbm.mean_per_class_error(train=True,  valid=True,  xval=False)
    mean_per_class_error = gbm.mean_per_class_error(train=True,  valid=False, xval=True)
    mean_per_class_error = gbm.mean_per_class_error(train=True,  valid=True,  xval=True)
    mean_per_class_error = gbm.mean_per_class_error(train=False, valid=False, xval=False) # default: return training metrics
    mean_per_class_error = gbm.mean_per_class_error(train=False, valid=True,  xval=True)

    # clustering
    iris = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris.csv"))
    from h2o.estimators.kmeans import H2OKMeansEstimator
    km = H2OKMeansEstimator(k=3, nfolds=3)
    km.train(x=list(range(4)), training_frame=iris)

    #   betweenss
    betweenss1 = km.betweenss(train=True,  valid=False, xval=False)
    assert isinstance(betweenss1, float)

    betweenss3 = km.betweenss(train=False, valid=False, xval=True)
    assert isinstance(betweenss3, float)

    betweenss = km.betweenss(train=True,  valid=False, xval=True)
    assert "train" in list(betweenss.keys()) and "xval" in list(betweenss.keys()), "expected training and cross validation metrics to be returned, but got {0}".format(list(betweenss.keys()))
    assert len(betweenss) == 2, "expected only training and cross validation metrics to be returned, but got {0}".format(list(betweenss.keys()))
    assert isinstance(betweenss["train"], float) and isinstance(betweenss["xval"], float), "expected training and cross validation metrics to be floats, but got {0} and {1}".format(type(betweenss["train"]), type(betweenss["xval"]))
    assert betweenss["xval"] == betweenss3

    betweenss = km.betweenss(train=False, valid=False, xval=False) # default: return training metrics
    assert isinstance(betweenss, float)
    assert betweenss == betweenss1

    #   totss
    totss1 = km.totss(train=True,  valid=False, xval=False)
    assert isinstance(totss1, float)

    totss3 = km.totss(train=False, valid=False, xval=True)
    assert isinstance(totss3, float)

    totss = km.totss(train=True,  valid=False, xval=True)
    assert "train" in list(totss.keys()) and "xval" in list(totss.keys()), "expected training and cross validation metrics to be returned, but got {0}".format(list(totss.keys()))
    assert len(totss) == 2, "expected only training and cross validation metrics to be returned, but got {0}".format(list(totss.keys()))
    assert isinstance(totss["train"], float) and isinstance(totss["xval"], float), "expected training and cross validation metrics to be floats, but got {0} and {1}".format(type(totss["train"]), type(totss["xval"]))
    assert totss["xval"] == totss3

    totss = km.totss(train=False, valid=False, xval=False) # default: return training metrics
    assert isinstance(totss, float)
    assert totss == totss1

    #   tot_withinss
    tot_withinss1 = km.tot_withinss(train=True,  valid=False, xval=False)
    assert isinstance(tot_withinss1, float)

    tot_withinss3 = km.tot_withinss(train=False, valid=False, xval=True)
    assert isinstance(tot_withinss3, float)

    tot_withinss = km.tot_withinss(train=True,  valid=False, xval=True)
    assert "train" in list(tot_withinss.keys()) and "xval" in list(tot_withinss.keys()), "expected training and cross validation metrics to be returned, but got {0}".format(list(tot_withinss.keys()))
    assert len(tot_withinss) == 2, "expected only training and cross validation metrics to be returned, but got {0}".format(list(tot_withinss.keys()))
    assert isinstance(tot_withinss["train"], float) and isinstance(tot_withinss["xval"], float), "expected training and cross validation metrics to be floats, but got {0} and {1}".format(type(tot_withinss["train"]), type(tot_withinss["xval"]))
    assert tot_withinss["xval"] == tot_withinss3

    tot_withinss = km.tot_withinss(train=False, valid=False, xval=False) # default: return training metrics
    assert isinstance(tot_withinss, float)
    assert tot_withinss == tot_withinss1

    #   withinss
    withinss1 = km.withinss(train=True,  valid=False)
    withinss2 = km.withinss(train=True,  valid=True)
    withinss3 = km.withinss(train=False, valid=False)  # default: return training metrics
    assert withinss1 == withinss3
    assert withinss1 != withinss2

    #   centroid_stats
    centroid_stats1 = km.centroid_stats(train=True,  valid=False)
    centroid_stats2 = km.centroid_stats(train=True,  valid=True)
    centroid_stats3 = km.centroid_stats(train=False, valid=False)  # default: return training metrics
    assert centroid_stats1 == centroid_stats3
    assert centroid_stats1 != centroid_stats2

    #   size
    size1 = km.size(train=True,  valid=False)
    size2 = km.size(train=True,  valid=True)
    size3 = km.size(train=False, valid=False)  # default: return training metrics
    assert size1 == size3
    assert size1 != size2

if __name__ == "__main__":
    pyunit_utils.standalone_test(metric_accessors)
else:
    metric_accessors()

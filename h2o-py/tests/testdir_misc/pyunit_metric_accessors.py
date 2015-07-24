import sys
sys.path.insert(1, "../../")
import h2o

def metric_accessors(ip,port):

    cars = h2o.import_frame(path=h2o.locate("smalldata/junit/cars_20mpg.csv"))
    r = cars[0].runif()
    train = cars[r > .2]
    valid = cars[r <= .2]

    # regression
    response_col = "economy"
    distribution = "gaussian"
    predictors = ["displacement","power","weight","acceleration","year"]
    gbm = h2o.gbm(y=train[response_col],
                  x=train[predictors],
                  validation_y=valid[response_col],
                  validation_x=valid[predictors],
                  nfolds=3,
                  distribution=distribution,
                  fold_assignment="Random")

    #   mse
    mse1 = gbm.mse(train=True,  valid=False, xval=False)
    assert isinstance(mse1, float)

    mse2 = gbm.mse(train=False, valid=True,  xval=False)
    assert isinstance(mse2, float)

    mse3 = gbm.mse(train=False, valid=False, xval=True)
    assert isinstance(mse3, float)

    mse = gbm.mse(train=True,  valid=True,  xval=False)
    assert "train" in mse.keys() and "valid" in mse.keys(), "expected training and validation metrics to be returned, but got {0}".format(mse.keys())
    assert len(mse) == 2, "expected only training and validation metrics to be returned, but got {0}".format(mse.keys())
    assert isinstance(mse["train"], float) and isinstance(mse["valid"], float), "expected training and validation metrics to be floats, but got {0} and {1}".format(type(mse["train"]), type(mse["valid"]))
    assert mse["valid"] == mse2

    mse = gbm.mse(train=True,  valid=False, xval=True)
    assert "train" in mse.keys() and "xval" in mse.keys(), "expected training and cross validation metrics to be returned, but got {0}".format(mse.keys())
    assert len(mse) == 2, "expected only training and cross validation metrics to be returned, but got {0}".format(mse.keys())
    assert isinstance(mse["train"], float) and isinstance(mse["xval"], float), "expected training and cross validation metrics to be floats, but got {0} and {1}".format(type(mse["train"]), type(mse["xval"]))
    assert mse["xval"] == mse3

    mse = gbm.mse(train=True,  valid=True,  xval=True)
    assert "train" in mse.keys() and "valid" in mse.keys() and "xval" in mse.keys(), "expected training, validation, and cross validation metrics to be returned, but got {0}".format(mse.keys())
    assert len(mse) == 3, "expected training, validation and cross validation metrics to be returned, but got {0}".format(mse.keys())
    assert isinstance(mse["train"], float) and isinstance(mse["valid"], float) and isinstance(mse["xval"], float), "expected training, validation, and cross validation metrics to be floats, but got {0}, {1}, and {2}".format(type(mse["train"]), type(mse["valid"]), type(mse["xval"]))

    mse = gbm.mse(train=False, valid=False, xval=False) # default: return training metrics
    assert isinstance(mse, float)
    assert mse == mse1

    mse = gbm.mse(train=False, valid=True,  xval=True)
    assert "valid" in mse.keys() and "xval" in mse.keys(), "expected validation and cross validation metrics to be returned, but got {0}".format(mse.keys())
    assert len(mse) == 2, "expected validation and cross validation metrics to be returned, but got {0}".format(mse.keys())
    assert isinstance(mse["valid"], float) and isinstance(mse["xval"], float), "validation and cross validation metrics to be floats, but got {0} and {1}".format(type(mse["valid"]), type(mse["xval"]))

    #   r2
    r21 = gbm.r2(train=True,  valid=False, xval=False)
    assert isinstance(r21, float)

    r22 = gbm.r2(train=False, valid=True,  xval=False)
    assert isinstance(r22, float)

    r23 = gbm.r2(train=False, valid=False, xval=True)
    assert isinstance(r23, float)

    r2 = gbm.r2(train=True,  valid=True,  xval=False)
    assert "train" in r2.keys() and "valid" in r2.keys(), "expected training and validation metrics to be returned, but got {0}".format(r2.keys())
    assert len(r2) == 2, "expected only training and validation metrics to be returned, but got {0}".format(r2.keys())
    assert isinstance(r2["train"], float) and isinstance(r2["valid"], float), "expected training and validation metrics to be floats, but got {0} and {1}".format(type(r2["train"]), type(r2["valid"]))
    assert r2["valid"] == r22

    r2 = gbm.r2(train=True,  valid=False, xval=True)
    assert "train" in r2.keys() and "xval" in r2.keys(), "expected training and cross validation metrics to be returned, but got {0}".format(r2.keys())
    assert len(r2) == 2, "expected only training and cross validation metrics to be returned, but got {0}".format(r2.keys())
    assert isinstance(r2["train"], float) and isinstance(r2["xval"], float), "expected training and cross validation metrics to be floats, but got {0} and {1}".format(type(r2["train"]), type(r2["xval"]))
    assert r2["xval"] == r23

    r2 = gbm.r2(train=True,  valid=True,  xval=True)
    assert "train" in r2.keys() and "valid" in r2.keys() and "xval" in r2.keys(), "expected training, validation, and cross validation metrics to be returned, but got {0}".format(r2.keys())
    assert len(r2) == 3, "expected training, validation and cross validation metrics to be returned, but got {0}".format(r2.keys())
    assert isinstance(r2["train"], float) and isinstance(r2["valid"], float) and isinstance(r2["xval"], float), "expected training, validation, and cross validation metrics to be floats, but got {0}, {1}, and {2}".format(type(r2["train"]), type(r2["valid"]), type(r2["xval"]))

    r2 = gbm.r2(train=False, valid=False, xval=False) # default: return training metrics
    assert isinstance(r2, float)
    assert r2 == r21

    r2 = gbm.r2(train=False, valid=True,  xval=True)
    assert "valid" in r2.keys() and "xval" in r2.keys(), "expected validation and cross validation metrics to be returned, but got {0}".format(r2.keys())
    assert len(r2) == 2, "expected validation and cross validation metrics to be returned, but got {0}".format(r2.keys())
    assert isinstance(r2["valid"], float) and isinstance(r2["xval"], float), "validation and cross validation metrics to be floats, but got {0} and {1}".format(type(r2["valid"]), type(r2["xval"]))

    #   mean_residual_deviance
    mean_residual_deviance1 = gbm.mean_residual_deviance(train=True,  valid=False, xval=False)
    assert isinstance(mean_residual_deviance1, float)

    mean_residual_deviance2 = gbm.mean_residual_deviance(train=False, valid=True,  xval=False)
    assert isinstance(mean_residual_deviance2, float)

    mean_residual_deviance3 = gbm.mean_residual_deviance(train=False, valid=False, xval=True)
    assert isinstance(mean_residual_deviance3, float)

    mean_residual_deviance = gbm.mean_residual_deviance(train=True,  valid=True,  xval=False)
    assert "train" in mean_residual_deviance.keys() and "valid" in mean_residual_deviance.keys(), "expected training and validation metrics to be returned, but got {0}".format(mean_residual_deviance.keys())
    assert len(mean_residual_deviance) == 2, "expected only training and validation metrics to be returned, but got {0}".format(mean_residual_deviance.keys())
    assert isinstance(mean_residual_deviance["train"], float) and isinstance(mean_residual_deviance["valid"], float), "expected training and validation metrics to be floats, but got {0} and {1}".format(type(mean_residual_deviance["train"]), type(mean_residual_deviance["valid"]))
    assert mean_residual_deviance["valid"] == mean_residual_deviance2

    mean_residual_deviance = gbm.mean_residual_deviance(train=True,  valid=False, xval=True)
    assert "train" in mean_residual_deviance.keys() and "xval" in mean_residual_deviance.keys(), "expected training and cross validation metrics to be returned, but got {0}".format(mean_residual_deviance.keys())
    assert len(mean_residual_deviance) == 2, "expected only training and cross validation metrics to be returned, but got {0}".format(mean_residual_deviance.keys())
    assert isinstance(mean_residual_deviance["train"], float) and isinstance(mean_residual_deviance["xval"], float), "expected training and cross validation metrics to be floats, but got {0} and {1}".format(type(mean_residual_deviance["train"]), type(mean_residual_deviance["xval"]))
    assert mean_residual_deviance["xval"] == mean_residual_deviance3

    mean_residual_deviance = gbm.mean_residual_deviance(train=True,  valid=True,  xval=True)
    assert "train" in mean_residual_deviance.keys() and "valid" in mean_residual_deviance.keys() and "xval" in mean_residual_deviance.keys(), "expected training, validation, and cross validation metrics to be returned, but got {0}".format(mean_residual_deviance.keys())
    assert len(mean_residual_deviance) == 3, "expected training, validation and cross validation metrics to be returned, but got {0}".format(mean_residual_deviance.keys())
    assert isinstance(mean_residual_deviance["train"], float) and isinstance(mean_residual_deviance["valid"], float) and isinstance(mean_residual_deviance["xval"], float), "expected training, validation, and cross validation metrics to be floats, but got {0}, {1}, and {2}".format(type(mean_residual_deviance["train"]), type(mean_residual_deviance["valid"]), type(mean_residual_deviance["xval"]))

    mean_residual_deviance = gbm.mean_residual_deviance(train=False, valid=False, xval=False) # default: return training metrics
    assert isinstance(mean_residual_deviance, float)
    assert mean_residual_deviance == mean_residual_deviance1

    mean_residual_deviance = gbm.mean_residual_deviance(train=False, valid=True,  xval=True)
    assert "valid" in mean_residual_deviance.keys() and "xval" in mean_residual_deviance.keys(), "expected validation and cross validation metrics to be returned, but got {0}".format(mean_residual_deviance.keys())
    assert len(mean_residual_deviance) == 2, "expected validation and cross validation metrics to be returned, but got {0}".format(mean_residual_deviance.keys())
    assert isinstance(mean_residual_deviance["valid"], float) and isinstance(mean_residual_deviance["xval"], float), "validation and cross validation metrics to be floats, but got {0} and {1}".format(type(mean_residual_deviance["valid"]), type(mean_residual_deviance["xval"]))


    # binomial
    cars = h2o.import_frame(path=h2o.locate("smalldata/junit/cars_20mpg.csv"))
    cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
    r = cars[0].runif()
    train = cars[r > .2]
    valid = cars[r <= .2]
    response_col = "economy_20mpg"
    distribution = "bernoulli"
    predictors = ["displacement","power","weight","acceleration","year"]
    gbm = h2o.gbm(y=train[response_col], x=train[predictors], validation_y=valid[response_col], validation_x=valid[predictors], nfolds=3, distribution=distribution, fold_assignment="Random")

    #   auc
    auc1 = gbm.auc(train=True,  valid=False, xval=False)
    assert isinstance(auc1, float)

    auc2 = gbm.auc(train=False, valid=True,  xval=False)
    assert isinstance(auc2, float)

    auc3 = gbm.auc(train=False, valid=False, xval=True)
    assert isinstance(auc3, float)

    auc = gbm.auc(train=True,  valid=True,  xval=False)
    assert "train" in auc.keys() and "valid" in auc.keys(), "expected training and validation metrics to be returned, but got {0}".format(auc.keys())
    assert len(auc) == 2, "expected only training and validation metrics to be returned, but got {0}".format(auc.keys())
    assert isinstance(auc["train"], float) and isinstance(auc["valid"], float), "expected training and validation metrics to be floats, but got {0} and {1}".format(type(auc["train"]), type(auc["valid"]))
    assert auc["valid"] == auc2

    auc = gbm.auc(train=True,  valid=False, xval=True)
    assert "train" in auc.keys() and "xval" in auc.keys(), "expected training and cross validation metrics to be returned, but got {0}".format(auc.keys())
    assert len(auc) == 2, "expected only training and cross validation metrics to be returned, but got {0}".format(auc.keys())
    assert isinstance(auc["train"], float) and isinstance(auc["xval"], float), "expected training and cross validation metrics to be floats, but got {0} and {1}".format(type(auc["train"]), type(auc["xval"]))
    assert auc["xval"] == auc3

    auc = gbm.auc(train=True,  valid=True,  xval=True)
    assert "train" in auc.keys() and "valid" in auc.keys() and "xval" in auc.keys(), "expected training, validation, and cross validation metrics to be returned, but got {0}".format(auc.keys())
    assert len(auc) == 3, "expected training, validation and cross validation metrics to be returned, but got {0}".format(auc.keys())
    assert isinstance(auc["train"], float) and isinstance(auc["valid"], float) and isinstance(auc["xval"], float), "expected training, validation, and cross validation metrics to be floats, but got {0}, {1}, and {2}".format(type(auc["train"]), type(auc["valid"]), type(auc["xval"]))

    auc = gbm.auc(train=False, valid=False, xval=False) # default: return training metrics
    assert isinstance(auc, float)
    assert auc == auc1

    auc = gbm.auc(train=False, valid=True,  xval=True)
    assert "valid" in auc.keys() and "xval" in auc.keys(), "expected validation and cross validation metrics to be returned, but got {0}".format(auc.keys())
    assert len(auc) == 2, "expected validation and cross validation metrics to be returned, but got {0}".format(auc.keys())
    assert isinstance(auc["valid"], float) and isinstance(auc["xval"], float), "validation and cross validation metrics to be floats, but got {0} and {1}".format(type(auc["valid"]), type(auc["xval"]))

    #   logloss
    logloss1 = gbm.logloss(train=True,  valid=False, xval=False)
    assert isinstance(logloss1, float)

    logloss2 = gbm.logloss(train=False, valid=True,  xval=False)
    assert isinstance(logloss2, float)

    logloss3 = gbm.logloss(train=False, valid=False, xval=True)
    assert isinstance(logloss3, float)

    logloss = gbm.logloss(train=True,  valid=True,  xval=False)
    assert "train" in logloss.keys() and "valid" in logloss.keys(), "expected training and validation metrics to be returned, but got {0}".format(logloss.keys())
    assert len(logloss) == 2, "expected only training and validation metrics to be returned, but got {0}".format(logloss.keys())
    assert isinstance(logloss["train"], float) and isinstance(logloss["valid"], float), "expected training and validation metrics to be floats, but got {0} and {1}".format(type(logloss["train"]), type(logloss["valid"]))
    assert logloss["valid"] == logloss2

    logloss = gbm.logloss(train=True,  valid=False, xval=True)
    assert "train" in logloss.keys() and "xval" in logloss.keys(), "expected training and cross validation metrics to be returned, but got {0}".format(logloss.keys())
    assert len(logloss) == 2, "expected only training and cross validation metrics to be returned, but got {0}".format(logloss.keys())
    assert isinstance(logloss["train"], float) and isinstance(logloss["xval"], float), "expected training and cross validation metrics to be floats, but got {0} and {1}".format(type(logloss["train"]), type(logloss["xval"]))
    assert logloss["xval"] == logloss3

    logloss = gbm.logloss(train=True,  valid=True,  xval=True)
    assert "train" in logloss.keys() and "valid" in logloss.keys() and "xval" in logloss.keys(), "expected training, validation, and cross validation metrics to be returned, but got {0}".format(logloss.keys())
    assert len(logloss) == 3, "expected training, validation and cross validation metrics to be returned, but got {0}".format(logloss.keys())
    assert isinstance(logloss["train"], float) and isinstance(logloss["valid"], float) and isinstance(logloss["xval"], float), "expected training, validation, and cross validation metrics to be floats, but got {0}, {1}, and {2}".format(type(logloss["train"]), type(logloss["valid"]), type(logloss["xval"]))

    logloss = gbm.logloss(train=False, valid=False, xval=False) # default: return training metrics
    assert isinstance(logloss, float)
    assert logloss == logloss1

    logloss = gbm.logloss(train=False, valid=True,  xval=True)
    assert "valid" in logloss.keys() and "xval" in logloss.keys(), "expected validation and cross validation metrics to be returned, but got {0}".format(logloss.keys())
    assert len(logloss) == 2, "expected validation and cross validation metrics to be returned, but got {0}".format(logloss.keys())
    assert isinstance(logloss["valid"], float) and isinstance(logloss["xval"], float), "validation and cross validation metrics to be floats, but got {0} and {1}".format(type(logloss["valid"]), type(logloss["xval"]))

    #   giniCoef
    giniCoef1 = gbm.giniCoef(train=True,  valid=False, xval=False)
    assert isinstance(giniCoef1, float)

    giniCoef2 = gbm.giniCoef(train=False, valid=True,  xval=False)
    assert isinstance(giniCoef2, float)

    giniCoef3 = gbm.giniCoef(train=False, valid=False, xval=True)
    assert isinstance(giniCoef3, float)

    giniCoef = gbm.giniCoef(train=True,  valid=True,  xval=False)
    assert "train" in giniCoef.keys() and "valid" in giniCoef.keys(), "expected training and validation metrics to be returned, but got {0}".format(giniCoef.keys())
    assert len(giniCoef) == 2, "expected only training and validation metrics to be returned, but got {0}".format(giniCoef.keys())
    assert isinstance(giniCoef["train"], float) and isinstance(giniCoef["valid"], float), "expected training and validation metrics to be floats, but got {0} and {1}".format(type(giniCoef["train"]), type(giniCoef["valid"]))
    assert giniCoef["valid"] == giniCoef2

    giniCoef = gbm.giniCoef(train=True,  valid=False, xval=True)
    assert "train" in giniCoef.keys() and "xval" in giniCoef.keys(), "expected training and cross validation metrics to be returned, but got {0}".format(giniCoef.keys())
    assert len(giniCoef) == 2, "expected only training and cross validation metrics to be returned, but got {0}".format(giniCoef.keys())
    assert isinstance(giniCoef["train"], float) and isinstance(giniCoef["xval"], float), "expected training and cross validation metrics to be floats, but got {0} and {1}".format(type(giniCoef["train"]), type(giniCoef["xval"]))
    assert giniCoef["xval"] == giniCoef3

    giniCoef = gbm.giniCoef(train=True,  valid=True,  xval=True)
    assert "train" in giniCoef.keys() and "valid" in giniCoef.keys() and "xval" in giniCoef.keys(), "expected training, validation, and cross validation metrics to be returned, but got {0}".format(giniCoef.keys())
    assert len(giniCoef) == 3, "expected training, validation and cross validation metrics to be returned, but got {0}".format(giniCoef.keys())
    assert isinstance(giniCoef["train"], float) and isinstance(giniCoef["valid"], float) and isinstance(giniCoef["xval"], float), "expected training, validation, and cross validation metrics to be floats, but got {0}, {1}, and {2}".format(type(giniCoef["train"]), type(giniCoef["valid"]), type(giniCoef["xval"]))

    giniCoef = gbm.giniCoef(train=False, valid=False, xval=False) # default: return training metrics
    assert isinstance(giniCoef, float)
    assert giniCoef == giniCoef1

    giniCoef = gbm.giniCoef(train=False, valid=True,  xval=True)
    assert "valid" in giniCoef.keys() and "xval" in giniCoef.keys(), "expected validation and cross validation metrics to be returned, but got {0}".format(giniCoef.keys())
    assert len(giniCoef) == 2, "expected validation and cross validation metrics to be returned, but got {0}".format(giniCoef.keys())
    assert isinstance(giniCoef["valid"], float) and isinstance(giniCoef["xval"], float), "validation and cross validation metrics to be floats, but got {0} and {1}".format(type(giniCoef["valid"]), type(giniCoef["xval"]))

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
    # tpr1 = gbm.tpr(train=True,  valid=False, xval=False)
    # tpr2 = gbm.tpr(train=False, valid=True,  xval=False)
    # tpr3 = gbm.tpr(train=False, valid=False, xval=True)
    # tpr = gbm.tpr(train=True,  valid=True,  xval=False)
    # tpr = gbm.tpr(train=True,  valid=False, xval=True)
    # tpr = gbm.tpr(train=True,  valid=True,  xval=True)
    # tpr = gbm.tpr(train=False, valid=False, xval=False) # default: return training metrics
    # tpr = gbm.tpr(train=False, valid=True,  xval=True)
    #
    # #   tnr
    # tnr1 = gbm.tnr(train=True,  valid=False, xval=False)
    # tnr2 = gbm.tnr(train=False, valid=True,  xval=False)
    # tnr3 = gbm.tnr(train=False, valid=False, xval=True)
    # tnr = gbm.tnr(train=True,  valid=True,  xval=False)
    # tnr = gbm.tnr(train=True,  valid=False, xval=True)
    # tnr = gbm.tnr(train=True,  valid=True,  xval=True)
    # tnr = gbm.tnr(train=False, valid=False, xval=False) # default: return training metrics
    # tnr = gbm.tnr(train=False, valid=True,  xval=True)
    #
    # #   fnr
    # fnr1 = gbm.fnr(train=True,  valid=False, xval=False)
    # fnr2 = gbm.fnr(train=False, valid=True,  xval=False)
    # fnr3 = gbm.fnr(train=False, valid=False, xval=True)
    # fnr = gbm.fnr(train=True,  valid=True,  xval=False)
    # fnr = gbm.fnr(train=True,  valid=False, xval=True)
    # fnr = gbm.fnr(train=True,  valid=True,  xval=True)
    # fnr = gbm.fnr(train=False, valid=False, xval=False) # default: return training metrics
    # fnr = gbm.fnr(train=False, valid=True,  xval=True)
    #
    # #   fpr
    # fpr1 = gbm.fpr(train=True,  valid=False, xval=False)
    # fpr2 = gbm.fpr(train=False, valid=True,  xval=False)
    # fpr3 = gbm.fpr(train=False, valid=False, xval=True)
    # fpr = gbm.fpr(train=True,  valid=True,  xval=False)
    # fpr = gbm.fpr(train=True,  valid=False, xval=True)
    # fpr = gbm.fpr(train=True,  valid=True,  xval=True)
    # fpr = gbm.fpr(train=False, valid=False, xval=False) # default: return training metrics
    # fpr = gbm.fpr(train=False, valid=True,  xval=True)


    # multinomial
    cars = h2o.import_frame(path=h2o.locate("smalldata/junit/cars_20mpg.csv"))
    cars["cylinders"] = cars["cylinders"].asfactor()
    r = cars[0].runif()
    train = cars[r > .2]
    valid = cars[r <= .2]
    response_col = "cylinders"
    distribution = "multinomial"
    predictors = ["displacement","power","weight","acceleration","year"]
    gbm = h2o.gbm(y=train[response_col],
                  x=train[predictors],
                  validation_y=valid[response_col],
                  validation_x=valid[predictors],
                  nfolds=3,
                  distribution=distribution,
                  fold_assignment="Random")

    #   mse
    mse1 = gbm.mse(train=True,  valid=False, xval=False)
    assert isinstance(mse1, float)

    mse2 = gbm.mse(train=False, valid=True,  xval=False)
    assert isinstance(mse2, float)

    mse3 = gbm.mse(train=False, valid=False, xval=True)
    assert isinstance(mse3, float)

    mse = gbm.mse(train=True,  valid=True,  xval=False)
    assert "train" in mse.keys() and "valid" in mse.keys(), "expected training and validation metrics to be returned, but got {0}".format(mse.keys())
    assert len(mse) == 2, "expected only training and validation metrics to be returned, but got {0}".format(mse.keys())
    assert isinstance(mse["train"], float) and isinstance(mse["valid"], float), "expected training and validation metrics to be floats, but got {0} and {1}".format(type(mse["train"]), type(mse["valid"]))
    assert mse["valid"] == mse2

    mse = gbm.mse(train=True,  valid=False, xval=True)
    assert "train" in mse.keys() and "xval" in mse.keys(), "expected training and cross validation metrics to be returned, but got {0}".format(mse.keys())
    assert len(mse) == 2, "expected only training and cross validation metrics to be returned, but got {0}".format(mse.keys())
    assert isinstance(mse["train"], float) and isinstance(mse["xval"], float), "expected training and cross validation metrics to be floats, but got {0} and {1}".format(type(mse["train"]), type(mse["xval"]))
    assert mse["xval"] == mse3

    mse = gbm.mse(train=True,  valid=True,  xval=True)
    assert "train" in mse.keys() and "valid" in mse.keys() and "xval" in mse.keys(), "expected training, validation, and cross validation metrics to be returned, but got {0}".format(mse.keys())
    assert len(mse) == 3, "expected training, validation and cross validation metrics to be returned, but got {0}".format(mse.keys())
    assert isinstance(mse["train"], float) and isinstance(mse["valid"], float) and isinstance(mse["xval"], float), "expected training, validation, and cross validation metrics to be floats, but got {0}, {1}, and {2}".format(type(mse["train"]), type(mse["valid"]), type(mse["xval"]))

    mse = gbm.mse(train=False, valid=False, xval=False) # default: return training metrics
    assert isinstance(mse, float)
    assert mse == mse1

    mse = gbm.mse(train=False, valid=True,  xval=True)
    assert "valid" in mse.keys() and "xval" in mse.keys(), "expected validation and cross validation metrics to be returned, but got {0}".format(mse.keys())
    assert len(mse) == 2, "expected validation and cross validation metrics to be returned, but got {0}".format(mse.keys())
    assert isinstance(mse["valid"], float) and isinstance(mse["xval"], float), "validation and cross validation metrics to be floats, but got {0} and {1}".format(type(mse["valid"]), type(mse["xval"]))

    #   logloss
    logloss1 = gbm.logloss(train=True,  valid=False, xval=False)
    assert isinstance(logloss1, float)

    logloss2 = gbm.logloss(train=False, valid=True,  xval=False)
    assert isinstance(logloss2, float)

    logloss3 = gbm.logloss(train=False, valid=False, xval=True)
    assert isinstance(logloss3, float)

    logloss = gbm.logloss(train=True,  valid=True,  xval=False)
    assert "train" in logloss.keys() and "valid" in logloss.keys(), "expected training and validation metrics to be returned, but got {0}".format(logloss.keys())
    assert len(logloss) == 2, "expected only training and validation metrics to be returned, but got {0}".format(logloss.keys())
    assert isinstance(logloss["train"], float) and isinstance(logloss["valid"], float), "expected training and validation metrics to be floats, but got {0} and {1}".format(type(logloss["train"]), type(logloss["valid"]))
    assert logloss["valid"] == logloss2

    logloss = gbm.logloss(train=True,  valid=False, xval=True)
    assert "train" in logloss.keys() and "xval" in logloss.keys(), "expected training and cross validation metrics to be returned, but got {0}".format(logloss.keys())
    assert len(logloss) == 2, "expected only training and cross validation metrics to be returned, but got {0}".format(logloss.keys())
    assert isinstance(logloss["train"], float) and isinstance(logloss["xval"], float), "expected training and cross validation metrics to be floats, but got {0} and {1}".format(type(logloss["train"]), type(logloss["xval"]))
    assert logloss["xval"] == logloss3

    logloss = gbm.logloss(train=True,  valid=True,  xval=True)
    assert "train" in logloss.keys() and "valid" in logloss.keys() and "xval" in logloss.keys(), "expected training, validation, and cross validation metrics to be returned, but got {0}".format(logloss.keys())
    assert len(logloss) == 3, "expected training, validation and cross validation metrics to be returned, but got {0}".format(logloss.keys())
    assert isinstance(logloss["train"], float) and isinstance(logloss["valid"], float) and isinstance(logloss["xval"], float), "expected training, validation, and cross validation metrics to be floats, but got {0}, {1}, and {2}".format(type(logloss["train"]), type(logloss["valid"]), type(logloss["xval"]))

    logloss = gbm.logloss(train=False, valid=False, xval=False) # default: return training metrics
    assert isinstance(logloss, float)
    assert logloss == logloss1

    logloss = gbm.logloss(train=False, valid=True,  xval=True)
    assert "valid" in logloss.keys() and "xval" in logloss.keys(), "expected validation and cross validation metrics to be returned, but got {0}".format(logloss.keys())
    assert len(logloss) == 2, "expected validation and cross validation metrics to be returned, but got {0}".format(logloss.keys())
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


    # clustering
    iris = h2o.import_frame(path=h2o.locate("smalldata/iris/iris.csv"))
    km = h2o.kmeans(x=iris[0:4],
                    nfolds=3,
                    k=3)

    #   betweenss
    betweenss1 = km.betweenss(train=True,  valid=False, xval=False)
    assert isinstance(betweenss1, float)

    betweenss3 = km.betweenss(train=False, valid=False, xval=True)
    assert isinstance(betweenss3, float)

    betweenss = km.betweenss(train=True,  valid=False, xval=True)
    assert "train" in betweenss.keys() and "xval" in betweenss.keys(), "expected training and cross validation metrics to be returned, but got {0}".format(betweenss.keys())
    assert len(betweenss) == 2, "expected only training and cross validation metrics to be returned, but got {0}".format(betweenss.keys())
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
    assert "train" in totss.keys() and "xval" in totss.keys(), "expected training and cross validation metrics to be returned, but got {0}".format(totss.keys())
    assert len(totss) == 2, "expected only training and cross validation metrics to be returned, but got {0}".format(totss.keys())
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
    assert "train" in tot_withinss.keys() and "xval" in tot_withinss.keys(), "expected training and cross validation metrics to be returned, but got {0}".format(tot_withinss.keys())
    assert len(tot_withinss) == 2, "expected only training and cross validation metrics to be returned, but got {0}".format(tot_withinss.keys())
    assert isinstance(tot_withinss["train"], float) and isinstance(tot_withinss["xval"], float), "expected training and cross validation metrics to be floats, but got {0} and {1}".format(type(tot_withinss["train"]), type(tot_withinss["xval"]))
    assert tot_withinss["xval"] == tot_withinss3

    tot_withinss = km.tot_withinss(train=False, valid=False, xval=False) # default: return training metrics
    assert isinstance(tot_withinss, float)
    assert tot_withinss == tot_withinss1

    #   withinss
    withinss1 = km.withinss(train=True,  valid=False, xval=False)
    assert isinstance(withinss1, float)

    withinss3 = km.withinss(train=False, valid=False, xval=True)
    assert isinstance(withinss3, float)

    withinss = km.withinss(train=True,  valid=False, xval=True)
    assert "train" in withinss.keys() and "xval" in withinss.keys(), "expected training and cross validation metrics to be returned, but got {0}".format(withinss.keys())
    assert len(withinss) == 2, "expected only training and cross validation metrics to be returned, but got {0}".format(withinss.keys())
    assert isinstance(withinss["train"], float) and isinstance(withinss["xval"], float), "expected training and cross validation metrics to be floats, but got {0} and {1}".format(type(withinss["train"]), type(withinss["xval"]))
    assert withinss["xval"] == withinss3

    withinss = km.withinss(train=False, valid=False, xval=False) # default: return training metrics
    assert isinstance(withinss, float)
    assert withinss == withinss1

    #   centroid_stats
    centroid_stats1 = km.centroid_stats(train=True,  valid=False, xval=False)
    centroid_stats3 = km.centroid_stats(train=False, valid=False, xval=True)
    centroid_stats = km.centroid_stats(train=True,  valid=False, xval=True)
    centroid_stats = km.centroid_stats(train=False, valid=False, xval=False) # default: return training metrics

    #   size
    size1 = km.size(train=True,  valid=False, xval=False)
    size3 = km.size(train=False, valid=False, xval=True)
    size = km.size(train=True,  valid=False, xval=True)
    size = km.size(train=False, valid=False, xval=False) # default: return training metrics

if __name__ == "__main__":
    h2o.run_test(sys.argv, metric_accessors)
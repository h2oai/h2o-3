#!/usr/bin/env python
# -*- encoding: utf-8 -*-
from __future__ import print_function
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator


def testOrdinalLogit():
    Dtrain = h2o.import_file(pyunit_utils.locate("bigdata/laptop/glm_ordinal_logit/ordinal_ordinal_20_training_set.csv"))
    Dtrain["C21"] = Dtrain["C21"].asfactor()
    Dtest = h2o.import_file(pyunit_utils.locate("bigdata/laptop/glm_ordinal_logit/ordinal_ordinal_20_test_set.csv"))
    Dtest["C21"] = Dtest["C21"].asfactor()

    print("Fit model on dataset")
    regL = [1.0/Dtrain.nrow, 1.0/(10*Dtrain.nrow), 1.0/(100*Dtrain.nrow)]
    lambdaL = regL
    alphaL = [0.8]
    bestAccLH = 0.0
    bestAccSQERR = 0.0

    for reg in regL:
        for lAmbda in lambdaL:
            for alpha in alphaL:
                model = H2OGeneralizedLinearEstimator(family="ordinal", alpha=alpha, lambda_=lAmbda, obj_reg = reg,
                                          max_iterations=1000, beta_epsilon=1e-8, objective_epsilon=1e-8, seed=12345)
                model.train(x=list(range(0,20)), y="C21", training_frame=Dtrain)
                predH2O = model.predict(Dtest)
                acc = calAcc(predH2O["predict"].as_data_frame(use_pandas=False), Dtest["C21"].as_data_frame(use_pandas=False))
                if (acc > bestAccLH):
                    bestAccLH = acc

                model2 = H2OGeneralizedLinearEstimator(family="ordinal", alpha=alpha, lambda_=lAmbda,
                                                       obj_reg = reg, max_iterations=1000, beta_epsilon=1e-8,
                                                       solver="GRADIENT_DESCENT_SQERR", objective_epsilon=1e-8, seed=12345)
                model2.train(x=list(range(0,20)), y="C21", training_frame=Dtrain)
                predH2O2 = model2.predict(Dtest)
                acc = calAcc(Dtest["C21"].as_data_frame(use_pandas=False), predH2O2['predict'].as_data_frame(use_pandas=False))
                if (bestAccSQERR < acc):
                    bestAccSQERR = acc


    print("Best accuracy for GRADIENT_DESCENT_LH is {0} and best accuracy for GRADIENT_DESCENT_SQERR is {1}".format(bestAccLH, bestAccSQERR))
    assert bestAccSQERR >= bestAccLH, "Ordinal regression default solver performs better than new solver."

def calAcc(f1, f2):
    acc = 0
    for index in range(1,len(f1)):
        if (f1[index][0]==f2[index][0]):
            acc=acc+1.0
    return (acc*1.0/(len(f1)-1.0))

if __name__ == "__main__":
    pyunit_utils.standalone_test(testOrdinalLogit)
else:
    testOrdinalLogit()

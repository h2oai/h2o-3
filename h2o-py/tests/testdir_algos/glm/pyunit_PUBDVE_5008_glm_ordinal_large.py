#!/usr/bin/env python
# -*- encoding: utf-8 -*-
from __future__ import print_function
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator


def testOrdinalLogit():
    Dtrain = h2o.import_file(pyunit_utils.locate("bigdata/laptop/glm_ordinal_logit/ordinal_multinomial_training_set.csv"))
    Dtrain["C11"] = Dtrain["C11"].asfactor()
    Dtest = h2o.import_file(pyunit_utils.locate("bigdata/laptop/glm_ordinal_logit/ordinal_multinomial_test_set.csv"))
    Dtest["C11"] = Dtest["C11"].asfactor()

    print("Fit model on dataset")
    model = H2OGeneralizedLinearEstimator(family="ordinal", alpha=1.0, lambda_=0.000000001, obj_reg = 0.00001,
                                          max_iterations=1000, beta_epsilon=1e-8, objective_epsilon=1e-10)
    model.train(x=list(range(0,10)), y="C11", training_frame=Dtrain)
    predH2O = model.predict(Dtest)
    if len(predH2O[0].levels()[0])<=len(Dtest["C11"].levels()[0]):
        print("Simulation result with bad seed.")
    assert len(predH2O[0].levels()[0])==len(Dtest["C11"].levels()[0]), "Your ordinal regression model fail to " \
                                                                       "capture all classes in the test dataset."


if __name__ == "__main__":
    pyunit_utils.standalone_test(testOrdinalLogit)
else:
    testOrdinalLogit()

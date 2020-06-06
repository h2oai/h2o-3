#!/usr/bin/env python
# -*- encoding: utf-8 -*-
from __future__ import print_function
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator as glm

def testOrdinalLogit():
    Dtrain = h2o.import_file(pyunit_utils.locate("bigdata/laptop/glm_ordinal_logit/ordinal_ordinal_20_training_set.csv"))
    Dtrain["C21"] = Dtrain["C21"].asfactor()

    print("Fit model on dataset")
    model = glm(family="ordinal", alpha=[0.5], lambda_= [0.001],max_iterations=1000, beta_epsilon=1e-8, objective_epsilon=1e-8)
    model.train(x=list(range(0,20)), y="C21", training_frame=Dtrain)
    predH2O = model.predict(Dtrain)
    r = glm.getGLMRegularizationPath(model)
    m2 = glm.makeGLMModel(model=model,coefs=r['coefficients'][0]) # model generated from setting coefficients to model
    f2 = m2.predict(Dtrain)
    pyunit_utils.compare_frames_local(predH2O, f2, prob=1)
    coefs = r['coefficients'][0]
    coefs['h2o_dream'] = 3.1415

    try:
        glm.makeGLMModel(model=model, coefs=coefs)
        assert False, "Should have thrown an exception!"
    except Exception as ex:
        print(ex)
        temp = str(ex)
        assert ("Server error java.lang.IllegalArgumentException:" in temp) and \
               ("model coefficient length 189 is different from coefficient provided by user ") in temp, \
            "Wrong exception was received."
        print("coefficient test passed!")    

if __name__ == "__main__":
    pyunit_utils.standalone_test(testOrdinalLogit)
else:
    testOrdinalLogit()

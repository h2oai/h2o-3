#!/usr/bin/env python
# -*- encoding: utf-8 -*-
import h2o
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
from tests import pyunit_utils

# test binomial family with generate_scoring_history on, alpha arrays, lambda search on and off
def test_binomial_alpha():
    training_data = h2o.import_file(pyunit_utils.locate("smalldata/logreg/benign.csv"))
    Y = 3
    X = [0, 1, 2, 4, 5, 6, 7, 8, 9, 10]

    # test with lambda search on, generate_scoring_history on and off
    model1 = H2OGeneralizedLinearEstimator(family="binomial", alpha=[0,0.2,0.5,0.8,1], lambda_search=True, 
                                           generate_scoring_history=True)
    model1.train(x=X, y=Y, training_frame=training_data)
    model2 = H2OGeneralizedLinearEstimator(family="binomial", alpha=[0,0.2,0.5,0.8,1], lambda_search=True,
                                           generate_scoring_history=True)
    model2.train(x=X, y=Y, training_frame=training_data)
    pyunit_utils.assertCoefDictEqual(model1.coef(), model2.coef())

    # test with lambda search off, generate_scoring_history on and off
    model1 = H2OGeneralizedLinearEstimator(family="binomial", alpha=[0,0.2,0.5,0.8,1], lambda_search=False,
                                           generate_scoring_history=True, Lambda=[0, 0.1, 0.01, 0.001])
    model1.train(x=X, y=Y, training_frame=training_data)
    model2 = H2OGeneralizedLinearEstimator(family="binomial", alpha=[0,0.2,0.5,0.8,1], lambda_search=False,
                                           generate_scoring_history=True, Lambda=[0, 0.1, 0.01, 0.001])
    model2.train(x=X, y=Y, training_frame=training_data)
    pyunit_utils.assertCoefDictEqual(model1.coef(), model2.coef())

    # test with lambda search on, generate_scoring_history on and off, cv on
    model1 = H2OGeneralizedLinearEstimator(family="binomial", alpha=[0,0.2,0.5,0.8,1], lambda_search=True,
                                           generate_scoring_history=True, nfolds=2, seed=12345)
    model1.train(x=X, y=Y, training_frame=training_data)
    model2 = H2OGeneralizedLinearEstimator(family="binomial", alpha=[0,0.2,0.5,0.8,1], lambda_search=True,
                                           generate_scoring_history=True, nfolds=2, seed=12345)
    model2.train(x=X, y=Y, training_frame=training_data)
    pyunit_utils.assertCoefDictEqual(model1.coef(), model2.coef())

    # test with lambda search off, generate_scoring_history on and off, cv on
    model1 = H2OGeneralizedLinearEstimator(family="binomial", alpha=[0,0.2,0.5,0.8,1], lambda_search=False,
                                           generate_scoring_history=True, nfolds=2, seed=12345, 
                                           Lambda=[0, 0.1, 0.01, 0.001])
    model1.train(x=X, y=Y, training_frame=training_data)
    model2 = H2OGeneralizedLinearEstimator(family="binomial", alpha=[0,0.2,0.5,0.8,1], lambda_search=False,
                                           generate_scoring_history=True, nfolds=2, seed=12345, 
                                           Lambda=[0, 0.1, 0.01, 0.001])
    model2.train(x=X, y=Y, training_frame=training_data)
    pyunit_utils.assertCoefDictEqual(model1.coef(), model2.coef())


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_binomial_alpha)
else:
    test_binomial_alpha()

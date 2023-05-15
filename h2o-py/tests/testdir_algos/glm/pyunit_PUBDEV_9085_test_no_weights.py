#!/usr/bin/env python
# -*- encoding: utf-8 -*-
import h2o
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
from tests import pyunit_utils


def test_weights_warning():
    """
    In this test, I will introduce a validation frame with no weight but training frame with weights.  There should be
    a warning about this.
    """
    training_data = h2o.import_file(pyunit_utils.locate("smalldata/logreg/benign.csv"))
    test_data = h2o.import_file(pyunit_utils.locate("smalldata/logreg/benign.csv"))

    Y = 3
    X = [0, 1, 2, 4, 5, 6, 7, 8, 9, 10]
    
    weight = (pyunit_utils.random_dataset_real_only(nrow=training_data.nrows, ncol=1, misFrac=0)).abs()
    weight.set_name(0, "weight_columns")
    training_data = training_data.cbind(weight)
    
    model = H2OGeneralizedLinearEstimator(family="binomial", alpha=0, lambda_=1e-5, weights_column="weight_columns")
    model.train(x=X, y=Y, training_frame=training_data, validation_frame=test_data)

    pyunit_utils.checkLogWeightWarning("weight_columns", wantWarnMessage=True)


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_weights_warning)
else:
    test_weights_warning()

#!/usr/bin/env python
# -*- encoding: utf-8 -*-
import h2o
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
from tests import pyunit_utils


def test_benign():
    training_data = h2o.import_file(pyunit_utils.locate("smalldata/logreg/benign.csv"))

    Y = 3
    X = [0, 1, 2, 4, 5, 6, 7, 8, 9, 10]

    model = H2OGeneralizedLinearEstimator(family="binomial", alpha=0, Lambda=1e-5)
    model = H2OGeneralizedLinearEstimator(family="binomial", alpha=0, lambda_=1e-5)
    model.train(x=X, y=Y, training_frame=training_data)

    in_names = [training_data.names[i] for i in X]
    out_names = [model._model_json['output']['coefficients_table'].cell_values[c][0] for c in range(len(X) + 1)]
    assert in_names == out_names[1:]


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_benign)
else:
    test_benign()

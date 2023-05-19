#!/usr/bin/env python
# -*- encoding: utf-8 -*-
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator


def perfect_separation_balanced():
    print("Read in synthetic balanced dataset")
    data = h2o.import_file(pyunit_utils.locate("smalldata/synthetic_perfect_separation/balanced.csv"),
                           col_names=["y", "x1", "x2"])
    print(data)

    print("Fit model on dataset")
    model = H2OGeneralizedLinearEstimator(family="binomial", lambda_search=True, alpha=0.5, lambda_=1e-8)
    model.train(x=["x1", "x2"], y="y", training_frame=data)

    print("Extract model coefficients and assert reasonable values (ie. no greater than 50)")
    print("Balanced dataset")
    coef = [c[1] for c in model._model_json["output"]["coefficients_table"].cell_values if c[0] != "Intercept"]
    for c in coef:
        assert c < 50, "coefficient is too large"



if __name__ == "__main__":
    pyunit_utils.standalone_test(perfect_separation_balanced)
else:
    perfect_separation_balanced()

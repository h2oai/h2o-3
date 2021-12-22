#!/usr/bin/env python
# -*- encoding: utf-8 -*-
from __future__ import print_function
import sys
sys.path.insert(1, "../../../")
from tests import pyunit_utils
import h2o
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
from h2o.grid.grid_search import H2OGridSearch


# Test to make sure that gridsearch runs with Ordinal regression.  There is no assert at the end of the test and it
# is okay in this case.  Need it to just run to completion
def test_ordinal_gridsearch():
    yindex = 'C11'
    Dtrain = h2o.import_file(pyunit_utils.locate("bigdata/laptop/glm_ordinal_logit/ordinal_multinomial_training_set.csv"))
    Dtrain[yindex] = Dtrain[yindex].asfactor()

    hyper_parameters = {
        'alpha': [0.01, 0.3, 0.5],
        'lambda': [1e-5, 1e-6, 1e-7, 1e-8]
    }
    gs = H2OGridSearch(
        H2OGeneralizedLinearEstimator(family='ordinal'),
        grid_id='ordinal_grid',
        hyper_params=hyper_parameters
    )
    gs.train(x=list(range(0, 10)), y="C11", training_frame=Dtrain)
    gs.show()


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_ordinal_gridsearch)
else:
    test_ordinal_gridsearch()

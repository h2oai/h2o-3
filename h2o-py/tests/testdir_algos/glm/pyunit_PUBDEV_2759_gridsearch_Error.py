from __future__ import print_function

import sys
from builtins import range

sys.path.insert(1, "../../../")

import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
from h2o.grid.grid_search import H2OGridSearch

def test_glm_binomial():
    """
    PUBDEV-2759: Details: ERRR on field: _fold_assignment: Fold assignment is only allowed for cross-validation.
    was not passed over to Python unit test.  This test is to make sure that the error message are passed over
    from Java to Python client and an empty grid model was returned which will generate error.
    """
    try:
        prostate = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
        hyper_parameters = {'alpha': [0, 0.5, 0.99], 'fold_assignment':['Random']}    # set hyper_parameters for grid search

        # train H2O GLM model with grid search
        model_h2o_grid_search = H2OGridSearch(H2OGeneralizedLinearEstimator(family="Binomial", Lambda=0.5),
                                              hyper_parameters)
        model_h2o_grid_search.train(x=list(range(2,9)), y=1, training_frame=prostate)
    except ValueError as e:
        assert "Details: ERRR on field: _fold_assignment: Fold assignment" in e.args[0], "Wrong error messages " \
                                                                                         "received."

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_glm_binomial)
else:
    test_glm_binomial()

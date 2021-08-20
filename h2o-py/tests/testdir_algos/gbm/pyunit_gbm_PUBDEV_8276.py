from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from h2o.exceptions import H2OResponseError


# PUBDEV-8276 
def test_weights_column_not_in_train():
    try:
        df = h2o.import_file(pyunit_utils.locate("smalldata/prostate/prostate.csv"))
        gbm = H2OGradientBoostingEstimator(seed=1234, weights_column='foo')
        gbm.train(y=-1, training_frame=df)
        assert False, "Model building should fail."
    except H2OResponseError as e:
        assert "ERRR on field: _weights_column" in e.__str__(), "Model building should fail with this in message."
        
        
if __name__ == "__main__":
    pyunit_utils.standalone_test(test_weights_column_not_in_train)
else:
    test_weights_column_not_in_train()

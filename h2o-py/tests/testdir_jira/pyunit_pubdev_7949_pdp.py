from __future__ import print_function
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator


def test_pdp_user_splits_no_cardinality_check():
    data = h2o.import_file(pyunit_utils.locate('smalldata/prostate/prostate.csv'))

    x = ['AGE', 'RACE']
    y = 'CAPSULE'
    data[y] = data[y].asfactor()
    data['RACE'] = data['RACE'].asfactor()
    data['AGE'] = data['AGE'].asfactor()

    gbm_model = H2OGradientBoostingEstimator(ntrees=50, learn_rate=0.05)
    gbm_model.train(x=x, y=y, training_frame=data)

    user_splits = {
        "AGE": ["64", "75"]
    }
    pdp = gbm_model.partial_plot(data=data, cols=['AGE'], user_splits=user_splits, plot=False)
    assert len(pdp[0].cell_values) == 2


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_pdp_user_splits_no_cardinality_check)
else:
    test_pdp_user_splits_no_cardinality_check()

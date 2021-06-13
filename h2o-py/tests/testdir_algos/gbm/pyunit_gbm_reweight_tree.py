from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from tests import pyunit_utils
from pandas.testing import assert_frame_equal


def gbm_reweight_tree():
    prostate_frame = h2o.import_file(path=pyunit_utils.locate("smalldata/prostate/prostate.csv"))
    prostate_frame["RACE"] = prostate_frame["RACE"].asfactor()
    prostate_frame["CAPSULE"] = prostate_frame["CAPSULE"].asfactor()

    x = ["AGE", "RACE", "GLEASON", "DCAPS", "PSA", "VOL", "DPROS"]
    y = 'CAPSULE'

    gbm_model = H2OGradientBoostingEstimator()
    gbm_model.train(x=x, y=y, training_frame=prostate_frame)

    # 1. Get original contributions
    contribs_original = gbm_model.predict_contributions(prostate_frame)
    assert contribs_original.col_names == [u'AGE', u'RACE', u'DPROS', u'DCAPS', u'PSA', u'VOL', u'GLEASON', u'BiasTerm']

    # 2. Scale weights => contributions should stay the same 
    prostate_frame["weights"] = 2
    h2o.rapids('(tree.update.weights {} {} "{}")'.format(gbm_model.model_id, prostate_frame.frame_id, "weights"))
    contribs_reweighted = gbm_model.predict_contributions(prostate_frame)
    assert_frame_equal(contribs_reweighted.as_data_frame(), contribs_original.as_data_frame())

    # 3. Reweight based on small subset of the data => contributions are expected to change
    prostate_subset = prostate_frame.head(10)
    h2o.rapids('(tree.update.weights {} {} "{}")'.format(gbm_model.model_id, prostate_subset.frame_id, "weights"))
    contribs_subset = gbm_model.predict_contributions(prostate_subset)
    assert contribs_subset["BiasTerm"].min() != contribs_original["BiasTerm"].min()


if __name__ == "__main__":
    pyunit_utils.standalone_test(gbm_reweight_tree)
else:
    gbm_reweight_tree()

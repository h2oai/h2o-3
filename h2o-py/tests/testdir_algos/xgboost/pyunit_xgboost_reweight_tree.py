from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from h2o.estimators.xgboost import H2OXGBoostEstimator
from tests import pyunit_utils
from pandas.testing import assert_frame_equal
import json
import math


def xgboost_reweight_tree():
    prostate_frame = h2o.import_file(path=pyunit_utils.locate("smalldata/prostate/prostate.csv"))
    prostate_frame["RACE"] = prostate_frame["RACE"].asfactor()
    prostate_frame["CAPSULE"] = prostate_frame["CAPSULE"].asfactor()

    x = ["AGE", "RACE", "GLEASON", "DCAPS", "PSA", "VOL", "DPROS"]
    y = 'CAPSULE'

    xgb_model = H2OXGBoostEstimator()
    xgb_model.train(x=x, y=y, training_frame=prostate_frame)

    # 0. Save original MOJO
    oring_mojo_path = xgb_model.download_mojo()
    orig_mojo_str = h2o.print_mojo(oring_mojo_path)

    # 1. Get original contributions
    contribs_original = xgb_model.predict_contributions(prostate_frame)
    assert contribs_original.col_names == [
        u'RACE.0', u'RACE.1', u'RACE.2', u'RACE.missing(NA)', u'AGE', u'DPROS', u'DCAPS', u'PSA', u'VOL', u'GLEASON', 
        u'BiasTerm'
    ]

    # 2. Scale weights => contributions should stay the same
    weights_scale = 2
    prostate_frame["weights"] = weights_scale
    h2o.rapids('(tree.update.weights {} {} "{}")'.format(xgb_model.model_id, prostate_frame.frame_id, "weights"))
    contribs_reweighted = xgb_model.predict_contributions(prostate_frame)
    assert_frame_equal(contribs_reweighted.as_data_frame(), contribs_original.as_data_frame(), check_less_precise=3)

    # 3. Reweight based on small subset of the data => contributions are expected to change
    prostate_subset = prostate_frame.head(10)
    h2o.rapids('(tree.update.weights {} {} "{}")'.format(xgb_model.model_id, prostate_subset.frame_id, "weights"))
    contribs_subset = xgb_model.predict_contributions(prostate_subset)
    assert contribs_subset["BiasTerm"].min() != contribs_original["BiasTerm"].min()

    # 4. Save modified mojo
    reweighted_mojo_path = xgb_model.download_mojo()
    reweighted_mojo_str = h2o.print_mojo(reweighted_mojo_path)

    # Sanity check
    assert orig_mojo_str != reweighted_mojo_str

    # Check first tree weight
    init_f = 1 / (1 + math.exp(0))
    hess_coef = init_f * (1 - init_f)
    orig_trees = json.loads(orig_mojo_str)
    assert orig_trees["trees"][0]["root"]["weight"] == prostate_frame.nrow * hess_coef
    
    reweighted_trees = json.loads(reweighted_mojo_str)
    assert reweighted_trees["trees"][0]["root"]["weight"] == prostate_subset.nrow * hess_coef * weights_scale


if __name__ == "__main__":
    pyunit_utils.standalone_test(xgboost_reweight_tree)
else:
    xgboost_reweight_tree()

from __future__ import print_function
import h2o
import sys, os
sys.path.insert(1, os.path.join("..","..",".."))
from tests import pyunit_utils
from h2o.estimators.isolation_forest import H2OIsolationForestEstimator


def isolation_forest_varsplits():
    print("Isolation Forest Variable Splits Test")

    prostate_hex = h2o.import_file(pyunit_utils.locate("smalldata/testng/prostate.csv"))

    model = H2OIsolationForestEstimator()
    model.train(training_frame=prostate_hex)

    splits = model.varsplits()
    assert len(splits) == prostate_hex.ncol 

    splits_pf = model.varsplits(use_pandas=True)
    assert splits_pf.shape == (prostate_hex.ncol, 4)
    assert (splits_pf["variable"] == prostate_hex.col_names).all()

if __name__ == "__main__":
    pyunit_utils.standalone_test(isolation_forest_varsplits)
else:
    isolation_forest_varsplits()

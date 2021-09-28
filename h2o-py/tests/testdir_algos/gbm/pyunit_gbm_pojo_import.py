from __future__ import print_function
import sys

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from pandas.util.testing import assert_frame_equal


def prostate_pojo_import():
    prostate = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    prostate = prostate.drop("ID")
    prostate['CAPSULE'] = prostate['CAPSULE'].asfactor()

    model = H2OGradientBoostingEstimator()
    model.train(
        y="CAPSULE",
        training_frame=prostate
    )
    
    sandbox_dir = pyunit_utils.locate("results")
    pojo_path = h2o.download_pojo(model, path=sandbox_dir)

    model_imported = h2o.import_mojo(pojo_path)
    print(model_imported)

    # 1. check scoring
    preds_original = model.predict(prostate)
    preds_imported = model_imported.predict(prostate)
    assert_frame_equal(preds_original.as_data_frame(), preds_imported.as_data_frame())

    # 2. check we can get PDPs
    pdp_original = model.partial_plot(data=prostate, cols=['AGE'], server=True, plot=False)
    pdp_imported = model_imported.partial_plot(data=prostate, cols=['AGE'], server=True, plot=False)
    assert_frame_equal(pdp_original[0].as_data_frame(), pdp_imported[0].as_data_frame())


if __name__ == "__main__":
    pyunit_utils.standalone_test(prostate_pojo_import)
else:
    prostate_pojo_import()

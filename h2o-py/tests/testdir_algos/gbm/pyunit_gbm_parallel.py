import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator


def test_gbm_bulk_parallel():
    prostate = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    prostate["CAPSULE"] = prostate["CAPSULE"].asfactor()

    prostate_gbm = H2OGradientBoostingEstimator(min_rows=2, ntrees=4, seed=42)
    models = prostate_gbm.bulk_train(y="CAPSULE", ignored_columns=["ID"], training_frame=prostate,
                                     segments=["RACE"], parallelism=2)

    models_list = models.as_frame()
    assert models_list.nrow == 3


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_gbm_bulk_parallel)
else:
    test_gbm_bulk_parallel()

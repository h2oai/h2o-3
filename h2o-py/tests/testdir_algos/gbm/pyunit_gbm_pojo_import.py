import sys

sys.path.insert(1, "../../../")
import h2o
import unittest
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from pandas.util.testing import assert_frame_equal


class TestGBMPojoImport(unittest.TestCase):
    def test(self):
        try:
            h2o.init(strict_version_check=False, jvm_custom_args=["-Dsys.ai.h2o.pojo.import.enabled=true", ])
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
            pdp_original = model.partial_plot(frame=prostate, cols=['AGE'], server=True, plot=False)
            pdp_imported = model_imported.partial_plot(frame=prostate, cols=['AGE'], server=True, plot=False)
            assert_frame_equal(pdp_original[0].as_data_frame(), pdp_imported[0].as_data_frame())
        finally:
            h2o.cluster().shutdown()


suite = unittest.TestLoader().loadTestsFromTestCase(TestGBMPojoImport)
unittest.TextTestRunner().run(suite)

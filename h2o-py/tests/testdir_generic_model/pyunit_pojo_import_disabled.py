import os
import sys
import unittest
import h2o
from h2o.backend import H2OLocalServer
from h2o.estimators import H2OGradientBoostingEstimator
from tests import pyunit_utils

sys.path.insert(1,"../../")

class TestJavaImportDisabled(unittest.TestCase):
    def test(self):
        try:
            h2o.init(strict_version_check=False)
            airlines = h2o.import_file(path=pyunit_utils.locate("smalldata/testng/airlines_train.csv"))
            gbm = H2OGradientBoostingEstimator(ntrees=1, nfolds=3)
            gbm.train(x=["Origin", "Dest"], y="IsDepDelayed", training_frame=airlines, validation_frame=airlines)
            
            pojo_path = gbm.download_pojo(path=os.path.join(pyunit_utils.locate("results"), gbm.model_id + ".java"))
            
            with self.assertRaises(OSError) as err:
                h2o.import_mojo(pojo_path)
            assert "POJO import is disabled since it brings a security risk." in str(err.exception)
    
            with self.assertRaises(OSError) as err:
                h2o.upload_mojo(pojo_path)
            assert "POJO import is disabled since it brings a security risk." in str(err.exception)
        finally:
            h2o.cluster().shutdown()


suite = unittest.TestLoader().loadTestsFromTestCase(TestJavaImportDisabled)
unittest.TextTestRunner().run(suite)

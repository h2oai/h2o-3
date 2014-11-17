import unittest, sys, time
sys.path.extend(['.','..','../..','py'])
import h2o, h2o_cmd, h2o_import as h2i
from h2o_test import dump_json, verboseprint

class Basic(unittest.TestCase):
    def tearDown(self):
        h2o.check_sandbox_for_errors()

    @classmethod
    def setUpClass(cls):
        h2o.init(1, java_heap_GB=4)

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()

    def test_GBM_covtype_train_test(self):

        bucket = 'home-0xdiag-datasets'
        importFolderPath = 'standard'
        trainFilename = 'covtype.shuffled.90pct.data'
        train_key = 'covtype.train.hex'
        model_key = 'GBMModelKey'
        timeoutSecs = 1800

        parseTrainResult = h2i.import_parse(bucket=bucket, path=importFolderPath + "/" + trainFilename, schema='local',
            hex_key=train_key, timeoutSecs=timeoutSecs)

        parameters = {
            'validation_frame': train_key,
            'ignored_columns': None,
            'score_each_iteration': True,
            'response_column': 'C55',
            'do_classification': True,
            # 'balance_classes':
            # 'max_after_balance_size':
            'ntrees': 4,
            'max_depth': 20,
            'min_rows': 3,
            'nbins': 40,
            'learn_rate': 0.2,
            # FIX! doesn't like it?
            # 'loss': 'Bernoulli',
            'variable_importance': True,
            # 'seed': 
        }

        kmeansResult = h2o.n0.build_model(
            algo='gbm',
            destination_key=model_key,
            training_frame=train_key,
            parameters=parameters,
            timeoutSecs=10)

        modelResult = h2o.n0.models(key=model_key)
        print "gbm modelResult:", dump_json(modelResult)

if __name__ == '__main__':
    h2o.unit_main()

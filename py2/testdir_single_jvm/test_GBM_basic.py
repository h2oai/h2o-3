import unittest, sys, time
sys.path.extend(['.','..','../..','py'])
import h2o, h2o_cmd, h2o_import as h2i
from h2o_test import dump_json, verboseprint, OutputObj

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
        csvPathname = importFolderPath + "/" + trainFilename

        parseResult = h2i.import_parse(bucket=bucket, path=csvPathname, schema='local',
            hex_key=train_key, timeoutSecs=timeoutSecs)
        numRows, numCols, parse_key = h2o_cmd.infoFromParse(parseResult)
        inspectResult = h2o_cmd.runInspect(key=parse_key)
        missingList, labelList, numRows, numCols = h2o_cmd.infoFromInspect(inspectResult)

        labelListUsed = list(labelList)
        numColsUsed = numCols

        parameters = {
            'validation_frame': train_key,
            'ignored_columns': None,
            'score_each_iteration': True,
            'response_column': 'C55',
            'do_classification': True,
            # 'balance_classes':
            # 'max_after_balance_size':
            'ntrees': 2,
            'max_depth': 10,
            'min_rows': 3,
            'nbins': 40,
            'learn_rate': 0.2,
            # FIX! doesn't like it?
            # 'loss': 'Bernoulli',
            # FIX..no variable importance for GBM yet?
            'variable_importance': False,
            # 'seed': 
        }

        model_key = 'benign_gbm.hex'
        bmResult = h2o.n0.build_model(
            algo='gbm',
            destination_key=model_key,
            training_frame=parse_key,
            parameters=parameters,
            timeoutSecs=60)
        bm = OutputObj(bmResult, 'bm')

        modelResult = h2o.n0.models(key=model_key)
        model = OutputObj(modelResult['models'][0]['output'], 'model')

        cmmResult = h2o.n0.compute_model_metrics(model=model_key, frame=parse_key, timeoutSecs=60)
        cmm = OutputObj(cmmResult, 'cmm')

        mmResult = h2o.n0.model_metrics(model=model_key, frame=parse_key, timeoutSecs=60)
        mm = OutputObj(mmResult, 'mm')

        print "Skipping predict"
        prResult = h2o.n0.predict(model=model_key, frame=parse_key, timeoutSecs=60)
        pr = OutputObj(prResult['model_metrics'][0]['predictions'], 'pr')

        # too slow!
        # h2o_cmd.runStoreView()

if __name__ == '__main__':
    h2o.unit_main()

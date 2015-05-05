import unittest, sys, time
sys.path.extend(['.','..','../..','py'])
import h2o2 as h2o
import h2o_cmd, h2o_import as h2i
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

    def test_GBM_basic_regress(self):
        bucket = 'home-0xdiag-datasets'
        importFolderPath = 'standard'
        trainFilename = 'covtype.shuffled.90pct.data'
        train_key = 'covtype.train.hex'
        model_key = 'GBMModelKey'
        timeoutSecs = 1800
        csvPathname = importFolderPath + "/" + trainFilename

        parseResult = h2i.import_parse(bucket=bucket, path=csvPathname, schema='local',
            hex_key=train_key, timeoutSecs=timeoutSecs)
        pA = h2o_cmd.ParseObj(parseResult)
        iA = h2o_cmd.InspectObj(pA.parse_key)
        parse_key = pA.parse_key
        numRows = iA.numRows
        numCols = iA.numCols
        labelList = iA.labelList


        labelListUsed = list(labelList)
        numColsUsed = numCols

        parameters = {
            'validation_frame': train_key,
            'ignored_columns': None,
            'response_column': 'C55',
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
            # 'variable_importance': False,
            # 'seed': 
        }

        model_key = 'covtype_gbm.hex'
        bmResult = h2o.n0.build_model(
            algo='gbm',
            model_id=model_key,
            training_frame=parse_key,
            parameters=parameters,
            timeoutSecs=60)
        bm = OutputObj(bmResult, 'bm')

        modelResult = h2o.n0.models(key=model_key)
        model = OutputObj(modelResult['models'][0]['output'], 'model')

        cmmResult = h2o.n0.compute_model_metrics(model=model_key, frame=parse_key, timeoutSecs=60)
        cmm = OutputObj(cmmResult, 'cmm')
        # just check that it's something non-zero
        # assert cmm.cm['prediction_error']!=0.0

        mmResult = h2o.n0.model_metrics(model=model_key, frame=parse_key, timeoutSecs=60)
        mmResultShort = mmResult['model_metrics'][0]
        del mmResultShort['frame'] # too much!
        mm = OutputObj(mmResultShort, 'mm')


        prResult = h2o.n0.predict(model=model_key, frame=parse_key, timeoutSecs=60)
        pr = OutputObj(prResult['model_metrics'][0]['predictions'], 'pr')

        # too slow!
        # h2o_cmd.runStoreView()

if __name__ == '__main__':
    h2o.unit_main()

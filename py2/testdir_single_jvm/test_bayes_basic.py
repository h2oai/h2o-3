import unittest, sys, time 
sys.path.extend(['.','..','../..','py'])
import h2o2 as h2o
import h2o_cmd, h2o_import as h2i
from h2o_test import dump_json, verboseprint, OutputObj
from tabulate import tabulate
from h2o_xl import Key, Assign

class Basic(unittest.TestCase):
    def tearDown(self):
        h2o.check_sandbox_for_errors()

    @classmethod
    def setUpClass(cls):
        h2o.init(3, java_heap_GB=4)

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()

    def test_bayes_basic(self):
        bucket = 'home-0xdiag-datasets'
        importFolderPath = 'standard'
        trainFilename = 'covtype.shuffled.90pct.data'
        train_key = 'covtype.train.hex'
        b = Key(train_key)

        model_key = 'bayesModelKey'
        timeoutSecs = 1800
        csvPathname = importFolderPath + "/" + trainFilename

        # FIX! do I need to force enum for classification? what if I do regression after this?
        columnTypeDict = {54: 'Enum'}
        parseResult = h2i.import_parse(bucket=bucket, path=csvPathname, columnTypeDict=columnTypeDict,
            schema='local', chunk_size=4194304, hex_key=train_key, timeoutSecs=timeoutSecs)

        # don't have to make it enum, if 0/1 (can't operate on enums like this)
        # make 1-7 go to 0-6. 0 isn't there.
        # make 1 thru 6 go to 1
        # change columnTypeDict to None above if I do this
        # Assign(b[:,54], b[:,54]-1)
        # Assign(b[:,54], b[:,54]!=0)
        # now we have just 0 and 1

        pA = h2o_cmd.ParseObj(parseResult)
        iA = h2o_cmd.InspectObj(pA.parse_key)
        parse_key = pA.parse_key
        numRows = iA.numRows
        numCols = iA.numCols
        labelList = iA.labelList

        labelListUsed = list(labelList)
        numColsUsed = numCols

        # run through a couple of parameter sets
        parameters = []
        parameters.append({
            'response_column': 'C55', # still 1-55 on colnames
            }) # just default

        model_key = 'covtype_bayes.hex'

        for p in parameters:
            bmResult = h2o.n0.build_model(
                algo='naivebayes',
                model_id=model_key,
                training_frame=train_key,
                validation_frame=train_key,
                parameters=p,
                timeoutSecs=60)
            bm = OutputObj(bmResult, 'bm')

            modelResult = h2o.n0.models(key=model_key)
            model = OutputObj(modelResult['models'][0]['output'], 'model')

            cmmResult = h2o.n0.compute_model_metrics(model=model_key, frame=parse_key, timeoutSecs=60)
            cmm = OutputObj(cmmResult, 'cmm')

            mmResult = h2o.n0.model_metrics(model=model_key, frame=parse_key, timeoutSecs=60)
            mmResultShort = mmResult['model_metrics'][0]
            del mmResultShort['frame'] # too much!
            mm = OutputObj(mmResultShort, 'mm')

            prResult = h2o.n0.predict(model=model_key, frame=parse_key, timeoutSecs=60)
            pr = OutputObj(prResult['model_metrics'][0]['predictions'], 'pr')

if __name__ == '__main__':
    h2o.unit_main()

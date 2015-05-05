import unittest, time, sys
sys.path.extend(['.','..','../..','py'])
import h2o2 as h2o
import h2o_cmd, h2o_glm
import h2o_browse as h2b
import h2o_import as h2i
import time, random
from h2o_test import OutputObj

class Basic(unittest.TestCase):
    def tearDown(self):
        h2o.check_sandbox_for_errors()

    @classmethod
    def setUpClass(cls):
        h2o.init(1,java_heap_GB=10)

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()

    def test_billion_rows(self):
        # just do the import folder once
        timeoutSecs = 1500

        csvFilenameAll = [
            # quick test first
            # "covtype.data", 
            # then the real thing
            "billion_rows.csv.gz",
            ]
        # csvFilenameList = random.sample(csvFilenameAll,1)
        csvFilenameList = csvFilenameAll

        # pop open a browser on the cloud
        ### h2b.browseTheCloud()

        for csvFilename in csvFilenameList:
            # creates csvFilename.hex from file in importFolder dir 
            start = time.time()
            parseResult = h2i.import_parse(bucket='home-0xdiag-datasets', path='standard/' + csvFilename,
                timeoutSecs=timeoutSecs, pollTimeoutSecs=60)
            elapsed = time.time() - start
            print csvFilename, "completed in", elapsed, "seconds.", "%d pct. of timeout" % ((elapsed*100)/timeoutSecs)

            pA = h2o_cmd.ParseObj(parseResult)
            iA = h2o_cmd.InspectObj(pA.parse_key)
            parse_key = pA.parse_key
            numRows = iA.numRows
            numCols = iA.numCols
            labelList = iA.labelList

            parameters = {
                'response_column': 'C2', 
                'alpha': '[0]', 
                'lambda': '[0]',
            }
            model_key = 'B.hex'
            bmResult = h2o.n0.build_model(
                algo='glm',
                model_id=model_key,
                training_frame=parse_key,
                parameters=parameters,
                timeoutSecs=300)
            bm = OutputObj(bmResult, 'bm')

            modelResult = h2o.n0.models(key=model_key)
            model = OutputObj(modelResult['models'][0]['output'], 'model')
            h2o_glm.simpleCheckGLM(self, model, parameters, labelList, labelListUsed)

            cmmResult = h2o.n0.compute_model_metrics(model=model_key, frame=parse_key, timeoutSecs=60)
            cmm = OutputObj(cmmResult, 'cmm')

            mmResult = h2o.n0.model_metrics(model=model_key, frame=parse_key, timeoutSecs=60)
            mm = OutputObj(mmResult, 'mm')

            prResult = h2o.n0.predict(model=model_key, frame=parse_key, timeoutSecs=60)
            pr = OutputObj(prResult['model_metrics'][0]['predictions'], 'pr')

            h2o_cmd.runStoreView()

            labelListUsed = labelList
            h2o_glm.simpleCheckGLM(self, model, parameters, labelList, labelListUsed)

if __name__ == '__main__':
    h2o.unit_main()

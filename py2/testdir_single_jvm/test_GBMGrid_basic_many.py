import unittest, sys, time
sys.path.extend(['.','..','../..','py'])
import h2o2 as h2o
import h2o_cmd, h2o_import as h2i
from h2o_test import dump_json, verboseprint, OutputObj
import h2o_jobs

DO_CLASSIFICATION = True
DO_FAIL_CASE = False
DO_FROM_TO_STEP = False

class Basic(unittest.TestCase):
    def tearDown(self):
        h2o.check_sandbox_for_errors()

    @classmethod
    def setUpClass(cls):
        h2o.init(1, java_heap_GB=4)

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()

    def test_GBMGrid_basic_many(self):
        trainFilename = 'prostate.csv'
        train_key = 'prostate.hex'
        timeoutSecs = 300
        csvPathname = "logreg/" + trainFilename
        parseResult = h2i.import_parse(bucket='smalldata', path=csvPathname, hex_key=train_key, schema='put')

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
            'ignored_columns': "['ID']", # this has to have []
            'response_column': 'CAPSULE',
            # 'balance_classes':
            # 'max_after_balance_size':
            # ??
            # 'ntrees': '[8, 10]',
            'ntrees': 8,
            # 'max_depth': '[8, 9]',
            'max_depth': 8,
            # ??
            # 'min_rows': '[1, 2]',
            'min_rows': 1,
            'nbins': 40,
            # ??
            # 'learn_rate': "[0.1, 0.2]",
            'learn_rate': 0.1,
            # FIX! doesn't like it?
            # 'loss': 'Bernoulli',
            # FIX..no variable importance for GBM yet?
            # 'variable_importance': False,
            # 'seed': 
        }

        jobs = []
        # kick off 5 of these GBM grid jobs, with different tree choices
        start = time.time()
        totalGBMGridJobs = 0

        for i in range(5):
            modelKey = 'GBMGrid_prostate_%s', i
            bmResult = h2o.n0.build_model(
                algo='gbm',
                model_id=modelKey,
                training_frame=parse_key,
                parameters=parameters,
                timeoutSecs=60)
            bm = OutputObj(bmResult, 'bm')
            print "GBMResult:", h2o.dump_json(bm)

            # FIX! is this right for gridded? 
            job_key = bm.jobs[0].key.name
            # FIX! this isn't a full formed name (%)
            model_key = bm.jobs[0].dest.name
            jobs.append( (job_key, model_key) )
            totalGBMGridJobs += 1

        h2o_jobs.pollWaitJobs(timeoutSecs=300)
        elapsed = time.time() - start
        print "All GBM jobs completed in", elapsed, "seconds."
        print "totalGBMGridJobs:", totalGBMGridJobs

        for job_key, model_key in jobs:
            modelResult = h2o.n0.models(key=model_key)
            model = OutputObj(modelResult['models'][0]['output'], 'model')

            cmmResult = h2o.n0.compute_model_metrics(model=model_key, frame=parse_key, timeoutSecs=60)
            cmm = OutputObj(cmmResult, 'cmm')
            print "\nLook!, can use dot notation: cmm.cm.confusion.matrix", cmm.cm.confusion_matrix, "\n"

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

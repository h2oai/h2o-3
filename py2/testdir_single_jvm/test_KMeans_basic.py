import unittest, time, sys, random
sys.path.extend(['.','..','../..','py'])
import h2o2 as h2o
import h2o_cmd, h2o_kmeans, h2o_import as h2i, h2o_jobs
from h2o_test import verboseprint, dump_json, OutputObj
import h2o_kmeans
# test


class Basic(unittest.TestCase):
    def tearDown(self):
        h2o.check_sandbox_for_errors()

    @classmethod
    def setUpClass(cls):
        global SEED
        SEED = h2o.setup_random_seed()

        h2o.init(1)

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()

    def notest_kmeans_benign(self):
        importFolderPath = "logreg"
        csvFilename = "benign.csv"
        hex_key = "benign.hex"
        csvPathname = importFolderPath + "/" + csvFilename

        parseResult = h2i.import_parse(bucket='smalldata', path=csvPathname, hex_key=hex_key, check_header=1, 
            timeoutSecs=180, doSummary=False)
        pA = h2o_cmd.ParseObj(parseResult)
        iA = h2o_cmd.InspectObj(pA.parse_key)
        parse_key = pA.parse_key
        numRows = iA.numRows
        numCols = iA.numCols
        labelList = iA.labelList

        expected = [
            (None, [8.86, 2.43, 35.53, 0.31, 13.22, 1.47, 1.33, 20.06, 13.08, 0.53, 2.12, 128.61, 35.33, 1.57], 49, None), 
            (None, [33.47, 2.29, 50.92, 0.34, 12.82, 1.33, 1.36, 21.43, 13.30, 0.37, 2.52, 125.40, 43.91, 1.79], 87, None), 
            (None, [27.64, 2.87, 48.11, 0.09, 11.80, 0.98, 1.51, 21.02, 12.53, 0.58, 2.89, 171.27, 42.73, 1.53], 55, None), 
            (None, [26.00, 2.67, 46.67, 0.00, 13.00, 1.33, 1.67, 21.56, 11.44, 0.22, 2.89, 234.56, 39.22, 1.56], 9, None), 
        ]

        # all are multipliers of expected tuple value
        allowedDelta = (0.01, 0.01, 0.01, 0.01)

        # loop, to see if we get same centers

        # no cols ignored
        labelListUsed = list(labelList)
        numColsUsed = numCols
        for trial in range(5):
            kmeansSeed = random.randint(0, sys.maxint)
            # kmeansSeed = 6655548259421773879
            parameters = {
                'validation_frame': parse_key,
                'ignored_columns': None,
                'k': 4,
                'max_iterations': 50,
                'standardize': False,
                'seed': kmeansSeed,
                'init': 'Furthest',
            }

            model_key = 'benign_k.hex'
            kmeansResult = h2o.n0.build_model(
                algo='kmeans', 
                model_id=model_key,
                training_frame=parse_key,
                parameters=parameters, 
                timeoutSecs=10) 

            modelResult = h2o.n0.models(key=model_key)
            km = h2o_kmeans.KMeansObj(modelResult, parameters, numRows, numColsUsed, labelListUsed)
            # zip with * is it's own inverse here. It's sorted by centers for easy comparisons
            # changed..old order: ids, mses, rows, centers = zip(*km.tuplesSorted)
            # new order:
            # ids, centers, rows, errors = zip(*km.tuplesSorted)
            # create a tuple for each cluster, then sort by row

            # old. this was going to do a predict and a summary (histogram) (old h2o1 needed this for more info)
            # (centers, tupleResultList) = h2o_kmeans.bigCheckResults(self, kmeansResult, csvPathname, parseResult, 'd', parameters)
            h2o_kmeans.compareResultsToExpected(km.tuplesSorted, expected, allowedDelta)

            # Not seeing any scoring results yet?
            cmmResult = h2o.n0.compute_model_metrics(model=model_key, frame=parse_key, timeoutSecs=60)
            cmm = OutputObj(cmmResult, 'cmm')

            mmResult = h2o.n0.model_metrics(model=model_key, frame=parse_key, timeoutSecs=60)
            mm = OutputObj(mmResult['model_metrics'][0], 'mm')

            prResult = h2o.n0.predict(model=model_key, frame=parse_key, timeoutSecs=60)
            pr = OutputObj(prResult['model_metrics'][0]['predictions'], 'pr')

            h2o_cmd.runStoreView()


    def test_kmeans_prostate(self):
        importFolderPath = "logreg"
        csvFilename = "prostate.csv"
        hex_key = "prostate.hex"
        csvPathname = importFolderPath + "/" + csvFilename

        parseResult = h2i.import_parse(bucket='smalldata', path=csvPathname, hex_key=hex_key, check_header=1, 
            timeoutSecs=180, doSummary=False)
        pA = h2o_cmd.ParseObj(parseResult)
        iA = h2o_cmd.InspectObj(pA.parse_key)
        parse_key = pA.parse_key
        numRows = iA.numRows
        numCols = iA.numCols
        labelList = iA.labelList

        # loop, to see if we get same centers

        expected = [
            (None, [0.37, 65.77, 1.07, 2.23, 1.11, 10.49, 4.24, 6.31],   215,  36955),  
            (None, [0.36, 66.44, 1.09, 2.21, 1.06, 10.84, 34.16, 6.31],  136,  46045), 
            (None, [0.83, 66.17, 1.21, 2.86, 1.34, 73.30, 15.57, 7.31],   29,  33412), 
        ]

        # all are multipliers of expected tuple value
        allowedDelta = (0.02, 0.02, 0.02)

        labelListUsed = list(labelList)
        labelListUsed.remove('ID')
        numColsUsed = numCols - 1

        for trial in range(5):
            # kmeansSeed = random.randint(0, sys.maxint)
            # actually can get a slightly better error sum with a different seed
            # this seed gets the same result as scikit (at least in h2o1)
            # kmeansSeed = 6655548259421773879
            kmeansSeed = 7037878434240420762
            parameters = {
                'validation_frame': parse_key,
                'ignored_columns': "['ID']",
                'k': 3,
                'max_iterations': 500,
                'standardize': False,
                'seed': kmeansSeed,
                # PlusPlus init seems bad here..should investigate
                'init': 'Furthest',
            }

            model_key = 'prostate_k.hex'
            bmResult = h2o.n0.build_model(
                algo='kmeans', 
                model_id=model_key,
                training_frame=parse_key,
                parameters=parameters, 
                timeoutSecs=10) 

            bm = OutputObj(bmResult, 'bm')

            modelResult = h2o.n0.models(key=model_key)
            km = h2o_kmeans.KMeansObj(modelResult, parameters, numRows, numColsUsed, labelListUsed)
            h2o_kmeans.compareResultsToExpected(km.tuplesSorted, expected, allowedDelta)

            cmmResult = h2o.n0.compute_model_metrics(model=model_key, frame=parse_key, timeoutSecs=60)
            cmm = OutputObj(cmmResult, 'cmm')

            mmResult = h2o.n0.model_metrics(model=model_key, frame=parse_key, timeoutSecs=60)
            mm = OutputObj(mmResult['model_metrics'][0], 'mm')

            prResult = h2o.n0.predict(model=model_key, frame=parse_key, timeoutSecs=60)
            pr = OutputObj(prResult['model_metrics'][0]['predictions'], 'pr')

            h2o_cmd.runStoreView()


if __name__ == '__main__':
    h2o.unit_main()

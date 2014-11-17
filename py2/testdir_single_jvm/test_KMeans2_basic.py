import unittest, time, sys, random
sys.path.extend(['.','..','../..','py'])
import h2o, h2o_cmd, h2o_kmeans, h2o_import as h2i, h2o_jobs
from h2o_test import verboseprint, dump_json


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

    class KmeansOutput(object):
        def __init__(self, output):
            assert isinstance(output, dict)
            for k,v in output.iteritems():
                setattr(self, k, v) # achieves self.k = v

    def test_kmeans_benign(self):
        importFolderPath = "logreg"
        csvFilename = "benign.csv"
        hex_key = "benign.hex"
        csvPathname = importFolderPath + "/" + csvFilename

        parseResult = h2i.import_parse(bucket='smalldata', path=csvPathname, hex_key=hex_key, checkHeader=1, 
            timeoutSecs=180, doSummary=False)
        numRows, numCols, parse_key = h2o_cmd.infoFromParse(parseResult)

        inspectResult = h2o_cmd.runInspect(key=parse_key)
        missingList, labelList, numRows, numCols = h2o_cmd.infoFromInspect(inspectResult)

        expected = [
            ([8.86, 2.43, 35.53, 0.31, 13.22, 1.47, 1.33, 20.06, 13.08, 0.53, 2.12, 128.61, 35.33, 1.57], 49, None), 
            ([33.47, 2.29, 50.92, 0.34, 12.82, 1.33, 1.36, 21.43, 13.30, 0.37, 2.52, 125.40, 43.91, 1.79], 87, None), 
            ([27.64, 2.87, 48.11, 0.09, 11.80, 0.98, 1.51, 21.02, 12.53, 0.58, 2.89, 171.27, 42.73, 1.53], 55, None), 
            ([26.00, 2.67, 46.67, 0.00, 13.00, 1.33, 1.67, 21.56, 11.44, 0.22, 2.89, 234.56, 39.22, 1.56], 9, None), 
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
                'score_each_iteration': False,
                'K': 4, 
                'max_iters': 50,
                'normalize': False,
                'seed': kmeansSeed,
                'init': 'PlusPlus',
            }

            model_key = 'benign_k.hex'
            kmeansResult = h2o.n0.build_model(
                algo='kmeans', 
                destination_key=model_key,
                training_frame=parse_key,
                parameters=parameters, 
                timeoutSecs=10) 

            modelResult = h2o.n0.models(key=model_key)

            # this prints too
            tuplesSorted, iters, mse, names = \
                h2o_kmeans.simpleCheckKMeans(self, modelResult, parameters, numRows, numColsUsed, labelListUsed)

            # zip with * is it's own inverse here. It's sorted by centers for easy comparisons
            ids, mses, rows, clusters = zip(*tuplesSorted)

            # create a tuple for each cluster, then sort by row

            # (centers, tupleResultList) = h2o_kmeans.bigCheckResults(self, 
            #    kmeansResult, csvPathname, parseResult, 'd', parameters)
            # h2o_kmeans.compareResultsToExpected(self, tupleResultList, expected, allowedDelta, trial=0)


    def test_kmeans_prostate(self):

        importFolderPath = "logreg"
        csvFilename = "prostate.csv"
        hex_key = "prostate.hex"
        csvPathname = importFolderPath + "/" + csvFilename

        parseResult = h2i.import_parse(bucket='smalldata', path=csvPathname, hex_key=hex_key, checkHeader=1, 
            timeoutSecs=180, doSummary=False)
        numRows, numCols, parse_key = h2o_cmd.infoFromParse(parseResult)

        inspectResult = h2o_cmd.runInspect(key=parse_key)
        missingList, labelList, numRows, numCols = h2o_cmd.infoFromInspect(inspectResult)

        # loop, to see if we get same centers

        expected = [
            ([0.37,65.77,1.07,2.23,1.11,10.49,4.24,6.31], 215, 36955), 
            ([0.36,66.44,1.09,2.21,1.06,10.84,34.16,6.31], 136, 46045),
            ([0.83,66.17,1.21,2.86,1.34,73.30,15.57,7.31], 29, 33412),
        ]

        # all are multipliers of expected tuple value
        allowedDelta = (0.01, 0.01, 0.01)

        labelListUsed = list(labelList)
        labelListUsed.remove('ID')
        numColsUsed = numCols - 1

        for trial in range(5):
            # kmeansSeed = random.randint(0, sys.maxint)
            # actually can get a slightly better error sum with a different seed
            # this seed gets the same result as scikit
            # kmeansSeed = 6655548259421773879
            kmeansSeed = random.randint(0, sys.maxint)
            parameters = {
                'validation_frame': parse_key,
                'ignored_columns': '[ID]',
                'score_each_iteration': False,
                'K': 3, 
                'max_iters': 500,
                'normalize': False,
                'seed': kmeansSeed,
                'init': 'PlusPlus',
            }

            model_key = 'prostate_k.hex'
            kmeansResult = h2o.n0.build_model(
                algo='kmeans', 
                destination_key=model_key,
                training_frame=parse_key,
                parameters=parameters, 
                timeoutSecs=10) 

            modelResult = h2o.n0.models(key=model_key)

            tuplesSorted, iters, mse, names = \
                h2o_kmeans.simpleCheckKMeans(self, modelResult, parameters, numRows, numColsUsed, labelListUsed)
            ids, mses, rows, clusters = zip(*tuplesSorted)

            # (centers, tupleResultList) = h2o_kmeans.bigCheckResults(self, kmeans, csvPathname, parseResult, 'd', **kwargs)
            # h2o_kmeans.compareResultsToExpected(self, tupleResultList, expected, allowedDelta, trial=trial)





if __name__ == '__main__':
    h2o.unit_main()

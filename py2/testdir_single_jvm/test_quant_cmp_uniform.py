import unittest, time, sys, random, math, getpass
sys.path.extend(['.','..','../..','py'])
import h2o2 as h2o
import h2o_cmd, h2o_import as h2i, h2o_util
import h2o_print as h2p, h2o_exec as h2e, h2o_summ
from h2o_test import dump_json, verboseprint, OutputObj

# have to match the csv file?
# dtype=['string', 'float');

probsList = [0.001, 0.01, 0.1, 0.25, 0.33, 0.5, 0.66, 0.75, 0.9, 0.99, 0.999]
ROWS = 100000

# tweak this to check different results (compare to sort/numpy/scipy)
CHECK_PCTILE_INDEX = 10
assert CHECK_PCTILE_INDEX >=0
assert CHECK_PCTILE_INDEX < len(probsList)
CHECK_PCTILE = probsList[CHECK_PCTILE_INDEX]

def write_syn_dataset(csvPathname, rowCount, colCount, expectedMin, expectedMax, SEED):
    r1 = random.Random(SEED)
    dsf = open(csvPathname, "w+")

    expectedRange = (expectedMax - expectedMin)
    for i in range(rowCount):
        rowData = []
        ri = expectedMin + (random.uniform(0,1) * expectedRange)
        for j in range(colCount):
            rowData.append(ri)

        rowDataCsv = ",".join(map(str,rowData))
        dsf.write(rowDataCsv + "\n")

    dsf.close()

class Basic(unittest.TestCase):
    def tearDown(self):
        h2o.check_sandbox_for_errors()

    @classmethod
    def setUpClass(cls):
        global SEED
        SEED = h2o.setup_random_seed()
        h2o.init(1, java_heap_GB=4)

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()

    def test_quant_cmp_uniform(self):
        SYNDATASETS_DIR = h2o.make_syn_dir()
        tryList = [
            (5*ROWS, 1, 'x.hex', 1, 20000,        ['C1',  1.10, 5000.0, 10000.0, 15000.0, 20000.00]),
            (5*ROWS, 1, 'x.hex', -5000, 0,        ['C1', -5001.00, -3750.0, -2445, -1200.0, 99]),
            (1*ROWS, 1, 'x.hex', -100000, 100000, ['C1',  -100001.0, -50000.0, 1613.0, 50000.0, 100000.0]),
            (1*ROWS, 1, 'x.hex', -1, 1,           ['C1',  -1.05, -0.48, 0.0087, 0.50, 1.00]),

            (1*ROWS, 1, 'A.hex', 1, 100,          ['C1',   1.05, 26.00, 51.00, 76.00, 100.0]),
            (1*ROWS, 1, 'A.hex', -99, 99,         ['C1',  -99, -50.0, 0, 50.00, 99]),

            (1*ROWS, 1, 'B.hex', 1, 10000,        ['C1',   1.05, 2501.00, 5001.00, 7501.00, 10000.00]),
            (1*ROWS, 1, 'B.hex', -100, 100,       ['C1',  -100.10, -50.0, 0.85, 51.7, 100,00]),

            (1*ROWS, 1, 'C.hex', 1, 100000,       ['C1',   1.05, 25002.00, 50002.00, 75002.00, 100000.00]),
            (1*ROWS, 1, 'C.hex', -101, 101,       ['C1',  -100.10, -50.45, -1.18, 49.28, 100.00]),
        ]

        timeoutSecs = 10
        trial = 1
        n = h2o.nodes[0]
        lenNodes = len(h2o.nodes)

        x = 0
        timeoutSecs = 60
        for (rowCount, colCount, hex_key, expectedMin, expectedMax, expected) in tryList:
            # max error = half the bin size?
            colname = expected[0]
            maxDelta = ((expectedMax - expectedMin)/1000.0) / 2.0

            # add 5% for fp errors?
            maxDelta = 1.05 * maxDelta

            SEEDPERFILE = random.randint(0, sys.maxint)
            x += 1
            csvFilename = 'syn_' + "binary" + "_" + str(rowCount) + 'x' + str(colCount) + '.csv'
            csvPathname = SYNDATASETS_DIR + '/' + csvFilename

            print "Creating random", csvPathname
            write_syn_dataset(csvPathname, rowCount, colCount, expectedMin, expectedMax, SEEDPERFILE)
            # need the full pathname when python parses the csv for numpy/sort
            csvPathnameFull = h2i.find_folder_and_filename(None, csvPathname, returnFullPath=True)

            #***************************
            # Parse
            parseResult = h2i.import_parse(path=csvPathname, schema='put', hex_key=hex_key, timeoutSecs=30, doSummary=False)
            pA = h2o_cmd.ParseObj(parseResult, expectedNumRows=rowCount, expectedNumCols=colCount)
            numRows = pA.numRows
            numCols = pA.numCols
            parse_key = pA.parse_key
            # this guy can take json object as first thing, or re-read with key
            iA = h2o_cmd.InspectObj(parse_key,
                expectedNumRows=rowCount, expectedNumCols=colCount, expectedMissinglist=[])

            #***************************
            # Summary
            co = h2o_cmd.runSummary(key=parse_key)
            default_pctiles = co.default_pctiles

            coList = [ co.base, len(co.bins), len(co.data), co.domain,
                co.label, co.maxs, co.mean, co.mins, co.missing, co.ninfs, co.pctiles,
                co.pinfs, co.precision, co.sigma, co.str_data, co.stride, co.type, co.zeros]
            for c in coList:
                print c

            print "len(co.bins):", len(co.bins)
            print "co.label:", co.label, "mean (2 places):", h2o_util.twoDecimals(co.mean)
            print "co.label:", co.label, "std dev. (2 places):", h2o_util.twoDecimals(co.sigma)

            print "FIX! hacking the co.pctiles because it's short by two"
            summ_pctiles = [0] + co.pctiles + [0]

            pt = h2o_util.twoDecimals(summ_pctiles)
            mx = h2o_util.twoDecimals(co.maxs)
            mn = h2o_util.twoDecimals(co.mins)
            exp = h2o_util.twoDecimals(expected[1:])

            print "co.label:", co.label, "co.pctiles (2 places):", pt
            print "default_pctiles:", default_pctiles
            print "co.label:", co.label, "co.maxs: (2 places):", mx
            print "co.label:", co.label, "co.mins: (2 places):", mn

            # FIX! we should do an exec and compare using the exec quantile too
            h2p.green_print("min/25/50/75/max co.label:", co.label, "(2 places):",\
                mn[0], pt[3], pt[5], pt[7], mx[0])
            h2p.green_print("min/25/50/75/max co.label:", co.label, "(2 places):",\
                exp[0], exp[1], exp[2], exp[3], exp[4])

            #***************************
            # Quantile
            # the thresholds h2o used, should match what we expected

            # using + here seems to result in an odd tuple..doesn't look right to h2o param
            # so went with this. Could add '[' and ']' to the list first, before the join.
            probsStr  = "[%s]" % ",".join(map(str,probsList))
            parameters = {
                'model_id': "a.hex",
                'training_frame': parse_key,
                'validation_frame': parse_key,
                'ignored_columns': None,
                'probs': probsStr,
            }

            model_key = 'qhex'
            bmResult = h2o.n0.build_model(
                algo='quantile',
                model_id=model_key,
                training_frame=parse_key,
                parameters=parameters,
                timeoutSecs=10)
            bm = OutputObj(bmResult, 'bm')

            msec = bm.jobs[0]['msec']
            print "bm msec", msec

            # quantile result is just a job result to a key
            modelResult = h2o.n0.models(key=model_key)
            model = OutputObj(modelResult['models'][0], 'model')

            print "model.output:", model.output
            print "model.output:['quantiles']", model.output['quantiles']
            print "model.output:['iterations']", model.output['iterations']
            print "model.output:['names']", model.output['names']
            quantiles = model.output['quantiles'][0] # why is this a double array
            iterations = model.output['iterations']
            assert iterations == 11, iterations
            print "quantiles: ", quantiles
            print "iterations: ", iterations

            # cmmResult = h2o.n0.compute_model_metrics(model=model_key, frame=parse_key, timeoutSecs=60)
            # cmm = OutputObj(cmmResult, 'cmm')

            # mmResult = h2o.n0.model_metrics(model=model_key, frame=parse_key, timeoutSecs=60)
            # mm = OutputObj(mmResult, 'mm')

            # prResult = h2o.n0.predict(model=model_key, frame=parse_key, timeoutSecs=60)
            # pr = OutputObj(prResult['model_metrics'][0]['predictions'], 'pr')
            h2o_cmd.runStoreView()

            trial += 1
            # compare the last threshold
            if colname!='':
                # don't do for enums
                # also get the median with a sort (h2o_summ.percentileOnSortedlist()
                h2o_summ.quantile_comparisons(
                    csvPathnameFull,
                    col=0, # what col to extract from the csv
                    datatype='float',
                    quantile=CHECK_PCTILE,
                    # h2oSummary2=pctile[-1],
                    # h2oQuantilesApprox=result, # from exec
                    h2oExecQuantiles=quantiles[CHECK_PCTILE_INDEX],
                    )
            h2o.nodes[0].remove_all_keys()

if __name__ == '__main__':
    h2o.unit_main()


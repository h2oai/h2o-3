import unittest, time, sys, random, math, getpass
sys.path.extend(['.','..','../..','py'])
import h2o, h2o_cmd, h2o_import as h2i, h2o_util, h2o_print as h2p, h2o_summ
from h2o_test import OutputObj

print "Same as test_summary2_uniform.py but with exponential distribution on the data"
DO_TRY_SCIPY = False
if  getpass.getuser() == 'kevin' or getpass.getuser() == 'jenkins':
    DO_TRY_SCIPY = True

DO_MEDIAN = True
MAX_QBINS = 1000
# 1 over desired mean
LAMBD = 0.005

def write_syn_dataset(csvPathname, rowCount, colCount, lambd=0.2, SEED=None):
    r1 = random.Random(SEED)
    dsf = open(csvPathname, "w+")

    expectedMin = None
    expectedMax = None
    for i in range(rowCount):
        rowData = []

        # Exponential distribution. 
        # lambd is 1.0 divided by the desired mean. It should be nonzero. 
        # Returned values range from 0 to positive infinity if lambd is positive, 
        # and from negative infinity to 0 if lambd is negative.
        for j in range(colCount):
            ri = random.expovariate(lambd=lambd)
            # None doesn't dominate for max, it doesn for min
            if expectedMin is None:
                expectedMin = ri
            else:
                expectedMin = min(expectedMin, ri)

            if expectedMax is None:
                expectedMax = ri
            else:
                expectedMax = max(expectedMax, ri)
            rowData.append(ri)

        rowDataCsv = ",".join(map(str,rowData))
        dsf.write(rowDataCsv + "\n")
        

    dsf.close()
    return (expectedMin, expectedMax)

class Basic(unittest.TestCase):
    def tearDown(self):
        h2o.check_sandbox_for_errors()

    @classmethod
    def setUpClass(cls):
        global SEED
        SEED = h2o.setup_random_seed()
        h2o.init()

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()
    def test_summary2_exp(self):
        SYNDATASETS_DIR = h2o.make_syn_dir()
        LAMBD = random.uniform(0.005, 0.5)
        tryList = [
            # co.label, (min, 25th, 50th, 75th, max)
# parse setup error
#            (1,     1, 'x.hex', 1, 20000,        ['C1', None, None, None, None, None]),
            (5,     1, 'x.hex', 1, 20000,        ['C1', None, None, None, None, None]),
#            (10,     1, 'x.hex', 1, 20000,        ['C1', None, None, None, None, None]),
#            (100,    1, 'x.hex', 1, 20000,        ['C1', None, None, None, None, None]),
#            (1000,   1, 'x.hex', -5000, 0,        ['C1', None, None, None, None, None]),
#            (10000,  1, 'x.hex', -100000, 100000, ['C1', None, None, None, None, None]),
#            (100000, 1, 'x.hex', -1, 1,           ['C1', None, None, None, None, None]),
#            (1000000, 1, 'A.hex', 1, 100,          ['C1', None, None, None, None, None]),
        ]

        timeoutSecs = 10
        trial = 1
        n = h2o.nodes[0]
        lenNodes = len(h2o.nodes)

        x = 0
        timeoutSecs = 60

        for (rowCount, colCount, hex_key, rangeMin, rangeMax, expected) in tryList:
            SEEDPERFILE = random.randint(0, sys.maxint)
            x += 1

            csvFilename = 'syn_' + "binary" + "_" + str(rowCount) + 'x' + str(colCount) + '.csv'
            csvPathname = SYNDATASETS_DIR + '/' + csvFilename

            print "Creating random", csvPathname, "lambd:", LAMBD
            (expectedMin, expectedMax) = write_syn_dataset(csvPathname, 
                rowCount, colCount, lambd=LAMBD, SEED=SEEDPERFILE)
            print "expectedMin:", expectedMin, "expectedMax:", expectedMax
            maxDelta = ((expectedMax - expectedMin)/20.0) / 2.0
            # add 5% for fp errors?
            maxDelta = 1.05 * maxDelta

            expected[1] = expectedMin
            expected[5] = expectedMax

            csvPathnameFull = h2i.find_folder_and_filename(None, csvPathname, returnFullPath=True)
            parseResult = h2i.import_parse(path=csvPathname, schema='put', 
                hex_key=hex_key, timeoutSecs=30, doSummary=False)
            numRows, numCols, parse_key = h2o_cmd.infoFromParse(parseResult)

            inspect = h2o_cmd.runInspect(key=parse_key)
            missingList, labelList, numRows, numCols = h2o_cmd.infoFromInspect(inspect)
            print "\n" + csvFilename
            # column 0?
            summaryResult = h2o_cmd.runSummary(key=hex_key, column='C1')
            h2o.verboseprint("Summary2 summaryResult:", h2o.dump_json(summaryResult))

            # default_pctiles
            # isText
            # rows
            # off
            # key
            # checksum

            # only one column (column 0)
            columns = summaryResult['frames'][0]['columns']
            default_pctiles = summaryResult['frames'][0]['default_pctiles']
            co = OutputObj(columns[0], 'summary')
            # how are enums binned. Stride of 1? (what about domain values)
            coList = [
                co.base,
                len(co.bins),
                len(co.data),
                co.domain,
                co.label,
                co.maxs,
                co.mean,
                co.mins,
                co.missing,
                co.ninfs,
                co.pctiles,
                co.pinfs,
                co.precision,
                co.sigma,
                co.str_data,
                co.stride,
                co.type,
                co.zeros,
                ]

            for k,v in co:
                print k, v

            print "len(co.bins):", len(co.bins)

            print "co.label:", co.label, "mean (2 places):", h2o_util.twoDecimals(co.mean)
            # what is precision. -1?
            print "co.label:", co.label, "std dev. (2 places):", h2o_util.twoDecimals(co.sigma)

            print "FIX! hacking the co.pctiles because it's short by two"
            pctiles = [0] + co.pctiles + [0]
            
            # the thresholds h2o used, should match what we expected
            if expected[0]:
                self.assertEqual(co.label, expected[0])
            if expected[1]:
                h2o_util.assertApproxEqual(co.mins[0], expected[1], tol=maxDelta, msg='min is not approx. expected')
            if expected[2]:
                h2o_util.assertApproxEqual(pctiles[3], expected[2], tol=maxDelta, msg='25th percentile is not approx. expected')
            if expected[3]:
                h2o_util.assertApproxEqual(pctiles[5], expected[3], tol=maxDelta, msg='50th percentile (median) is not approx. expected')
            if expected[4]:
                h2o_util.assertApproxEqual(pctiles[7], expected[4], tol=maxDelta, msg='75th percentile is not approx. expected')
            if expected[5]:
                h2o_util.assertApproxEqual(co.maxs[0], expected[5], tol=maxDelta, msg='max is not approx. expected')

            # figure out the expected max error
            # use this for comparing to sklearn/sort
            if expected[1] and expected[5]:
                expectedRange = expected[5] - expected[1]
                # because of floor and ceil effects due we potentially lose 2 bins (worst case)
                # the extra bin for the max value, is an extra bin..ignore
                expectedBin = expectedRange/(MAX_QBINS-2)
                maxErr = expectedBin # should we have some fuzz for fp?

            else:
                print "Test won't calculate max expected error"
                maxErr = 0

            pt = h2o_util.twoDecimals(pctiles)
            mx = h2o_util.twoDecimals(co.maxs)
            mn = h2o_util.twoDecimals(co.mins)

            print "co.label:", co.label, "co.pctiles (2 places):", pt
            print "default_pctiles:", default_pctiles
            print "co.label:", co.label, "co.maxs: (2 places):", mx
            print "co.label:", co.label, "co.mins: (2 places):", mn

            # FIX! we should do an exec and compare using the exec quantile too
            compareActual = mn[0], pt[3], pt[5], pt[7], mx[0]
            h2p.green_print("min/25/50/75/max co.label:", co.label, "(2 places):", compareActual)
            print "co.label:", co.label, "co.maxs (2 places):", mx
            print "co.label:", co.label, "co.mins (2 places):", mn

            trial += 1
            h2o.nodes[0].remove_all_keys()

            scipyCol = 0
            print "h2oSummary2MaxErr", maxErr
            if co.label!='' and expected[scipyCol]:
                # don't do for enums
                # also get the median with a sort (h2o_summ.percentileOnSortedlist()
                h2o_summ.quantile_comparisons(
                    csvPathnameFull,
                    skipHeader=False,
                    col=scipyCol,
                    datatype='float',
                    quantile=0.5 if DO_MEDIAN else 0.999,
                    h2oSummary2=pctiles[5 if DO_MEDIAN else 10],
                    # h2oQuantilesApprox=qresult_single,
                    # h2oQuantilesExact=qresult,
                    h2oSummary2MaxErr=maxErr,
                    )


if __name__ == '__main__':
    h2o.unit_main()


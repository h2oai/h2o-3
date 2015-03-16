import unittest, time, sys, random, math, getpass
sys.path.extend(['.','..','../..','py'])
import h2o2 as h2o
import h2o_cmd, h2o_import as h2i, h2o_util, h2o_print as h2p, h2o_summ
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
        # use the failing seed for now
        SEED = h2o.setup_random_seed(seed=6119134012054500977)
        h2o.init()

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()
    def test_summary2_exp(self):
        SYNDATASETS_DIR = h2o.make_syn_dir()
        LAMBD = random.uniform(0.005, 0.5)
        tryList = [
            # co.label, (min, 25th, 50th, 75th, max)
            # parse setup error ? supposedly fixed now
            # (1,     1, 'x.hex', 1, 20000,        ['C1', None, None, None, None, None]),
            (5,     1, 'x.hex', 1, 20000,        ['C1', None, None, None, None, None]),
            (10,     1, 'x.hex', 1, 20000,        ['C1', None, None, None, None, None]),
            (100,    1, 'x.hex', 1, 20000,        ['C1', None, None, None, None, None]),
            (1000,   1, 'x.hex', -5000, 0,        ['C1', None, None, None, None, None]),
            (10000,  1, 'x.hex', -100000, 100000, ['C1', None, None, None, None, None]),
            (100000, 1, 'x.hex', -1, 1,           ['C1', None, None, None, None, None]),
            (1000000, 1, 'A.hex', 1, 100,          ['C1', None, None, None, None, None]),
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
            (expectedMin, expectedMax) = write_syn_dataset(csvPathname, rowCount, colCount, lambd=LAMBD, SEED=SEEDPERFILE)
            print "expectedMin:", expectedMin, "expectedMax:", expectedMax
            maxErr = ((expectedMax - expectedMin)/20.0) / 2.0
            # add 5% for fp errors?
            maxErr = 1.05 * maxErr

            expected[1] = expectedMin
            expected[5] = expectedMax

            csvPathnameFull = h2i.find_folder_and_filename(None, csvPathname, returnFullPath=True)
            parseResult = h2i.import_parse(path=csvPathname, schema='put', hex_key=hex_key, timeoutSecs=30, doSummary=False)
            pA = h2o_cmd.ParseObj(parseResult, expectedNumRows=rowCount, expectedNumCols=colCount)
            print pA.numRows, pA.numCols, pA.parse_key

            iA = h2o_cmd.InspectObj(pA.parse_key,
                expectedNumRows=rowCount, expectedNumCols=colCount, expectedMissinglist=[])
            print iA.missingList, iA.labelList, iA.numRows, iA.numCols

            # column 0 not used here
            assert len(expected) == 6
            co = h2o_cmd.runSummary(key=hex_key, column=0, expected=expected[1:], maxDelta=maxErr)
            trial += 1
            h2o.nodes[0].remove_all_keys()

            scipyCol = 0
            print "maxErr", maxErr
            if co.label!='' and expected[scipyCol]:
                # don't do for enums
                # also get the median with a sort (h2o_summ.percentileOnSortedlist()
                h2o_summ.quantile_comparisons(
                    csvPathnameFull,
                    skipHeader=False,
                    col=scipyCol,
                    datatype='float',
                    quantile=0.5 if DO_MEDIAN else 0.99,
                    h2oSummary2=co.percentiles[5 if DO_MEDIAN else 9],

                    # h2oQuantilesApprox=qresult_single,
                    # h2oQuantilesExact=qresult,
                    h2oSummary2MaxErr=maxErr,
                    )


if __name__ == '__main__':
    h2o.unit_main()


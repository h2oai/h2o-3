import unittest, random, sys, time
sys.path.extend(['.','..','../..','py'])
import h2o2 as h2o
import h2o_cmd, h2o_browse as h2b, h2o_import as h2i, h2o_exec as h2e
from h2o_test import verboseprint, dump_json
from h2o_xl import KeyIndexed, Fcn, Seq, Colon, Assign, Item, Col, Xbase
import h2o_xl

print "just looking at an issue with mean()"
def write_syn_dataset(csvPathname, rowCount, colCount, SEED):
    # 8 random generatators, 1 per column
    r1 = random.Random(SEED)
    dsf = open(csvPathname, "w+")

    for i in range(rowCount):
        rowData = []
        for j in range(colCount):
            r = r1.randint(0,1)
            rowData.append(r)

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
        h2o.init(1, java_heap_GB=14, sandbox_ignore_errors=True)

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()

    def test_rapids_mean(self):
        SYNDATASETS_DIR = h2o.make_syn_dir()
        tryList = [
            (1000, 5, 'cA', 200),
            ]

        for (rowCount, colCount, hex_key, timeoutSecs) in tryList:
            SEEDPERFILE = random.randint(0, sys.maxint)

            csvFilename = 'syn_' + str(SEEDPERFILE) + "_" + str(rowCount) + 'x' + str(colCount) + '.csv'
            csvPathname = SYNDATASETS_DIR + '/' + csvFilename

            print "\nCreating random", csvPathname
            write_syn_dataset(csvPathname, rowCount, colCount, SEEDPERFILE)
            parseResult = h2i.import_parse(path=csvPathname, schema='put', hex_key=hex_key, 
                timeoutSecs=timeoutSecs, doSummary=False)

            inspect = h2o_cmd.runInspect(key=hex_key)
            missingList, labelList, numRows, numCols = h2o_cmd.infoFromInspect(inspect)

            print "\n" + csvPathname, \
                "    numRows:", "{:,}".format(numRows), \
                "    numCols:", "{:,}".format(numCols)

            # should match # of cols in header or ??
            self.assertEqual(numCols, colCount,
                "parse created result with the wrong number of cols %s %s" % (numCols, colCount))
            self.assertEqual(numRows, rowCount,
                "parse created result with the wrong number of rows %s %s" % (numRows, rowCount))

            data_key = hex_key
            data_key2 = hex_key + "_2"
            for trial in range(4):
                result_key = data_key + "_" + str(trial)
                # copy the key
                Assign(data_key2, data_key)
                Assign(result_key, Fcn('mean', KeyIndexed(data_key2, col=0), 0, False))
                trial += 1

if __name__ == '__main__':
    h2o.unit_main()

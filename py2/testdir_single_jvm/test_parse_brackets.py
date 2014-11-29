import unittest, random, sys, time
sys.path.extend(['.','..','../..','py'])
import h2o, h2o_cmd, h2o_import as h2i

DO_INTERMEDIATE_RESULTS = False
print "HACK: have to change this schema to local, from put, for now. h2o-dev support?"
def write_syn_dataset(csvPathname, rowCount, colCount, SEED):
    # 8 random generatators, 1 per column
    r1 = random.Random(SEED)
    dsf = open(csvPathname, "w+")

    for i in range(rowCount+1):
        rowData = []

        if i==0: # header
            for j in range(colCount):
                # try single quotes in there too
                r = "c'[" + str(j) + "]"
                rowData.append(r)

        else:
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
        h2o.init(1,java_heap_GB=14)

    @classmethod
    def tearDownClass(cls):
        ## print "sleeping 3600"
        # h2o.sleep(3600)
        h2o.tear_down_cloud()

    def test_parse_brackets(self):
        SYNDATASETS_DIR = h2o.make_syn_dir()
        tryList = [
            (10, 1000, 'cA', 200, 200),
            (10, 2000, 'cA', 200, 200),
            (10, 4000, 'cA', 200, 200),
            # (10, 8000, 'cA', 200, 200),
            # (10, 9000, 'cA', 200, 200),
            #(10, 10000, 'cA', 200, 200),
            # (10, 100000, 'cA', 200, 200),
            # (10, 200000, 'cB', 200, 200),
            # (10, 300000, 'cB', 200, 200),
            # we timeout/fail on 500k? stop at 200k
            # (10, 500000, 'cC', 200, 200),
            # (10, 1000000, 'cD', 200, 360),
            # (10, 1100000, 'cE', 60, 100),
            # (10, 1200000, 'cF', 60, 120),
            ]

        # h2b.browseTheCloud()
        for (rowCount, colCount, hex_key, timeoutSecs, timeoutSecs2) in tryList:
            SEEDPERFILE = random.randint(0, sys.maxint)

            csvFilename = 'syn_' + str(SEEDPERFILE) + "_" + str(rowCount) + 'x' + str(colCount) + '.csv'
            csvPathname = SYNDATASETS_DIR + '/' + csvFilename

            print "\nCreating random", csvPathname
            write_syn_dataset(csvPathname, rowCount, colCount, SEEDPERFILE)

            start = time.time()
            # does it blow up if it sets columnNames?
            parseResult = h2i.import_parse(path=csvPathname, schema='local', hex_key=hex_key,
                timeoutSecs=timeoutSecs, doSummary=False, columnNames=None, intermediateResults=DO_INTERMEDIATE_RESULTS)
            print "Parse:", csvFilename, "took", time.time() - start, "seconds"

            start = time.time()
            inspect = h2o_cmd.runInspect(None, hex_key, timeoutSecs=timeoutSecs2)
            print "Inspect:", hex_key, "took", time.time() - start, "seconds"
            missingList, labelList, numRows, numCols = h2o_cmd.infoFromInspect(inspect)
            print "\n" + csvPathname, \
                "    numRows:", "{:,}".format(numRows), \
                "    numCols:", "{:,}".format(numCols)

            # should match # of cols in header or ??
            self.assertEqual(numCols, colCount,
                "parse created result with the wrong number of cols %s %s" % (numCols, colCount))
            self.assertEqual(numRows, rowCount,
                "parse created result with the wrong number of rows (header shouldn't count) %s %s" % \
                (numRows, rowCount))

            print "Skipping the delete keys for now"
            if 1==0:
                # if not h2o.browse_disable:
                #    h2b.browseJsonHistoryAsUrlLastMatch("Inspect")
                #    time.sleep(5)
                h2i.delete_keys_at_all_nodes()

if __name__ == '__main__':
    h2o.unit_main()

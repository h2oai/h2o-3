import unittest, random, sys, time
sys.path.extend(['.','..','../..','py'])
import h2o2 as h2o
import h2o_cmd, h2o_browse as h2b, h2o_import as h2i, h2o_exec as h2e, h2o_util, h2o_gbm

print "Plot the time to remove a key vs parsed size"
print "Stress the # of cols with fp reals here." 
print "Can pick fp format but will start with just the first (e0)"
def write_syn_dataset(csvPathname, rowCount, colCount, SEEDPERFILE, sel):
    # we can do all sorts of methods off the r object
    r = random.Random(SEEDPERFILE)
    NUM_CASES = h2o_util.fp_format()
    if sel and (sel<0 or sel>=NUM_CASES):
        raise Exception("sel used to select from possible fp formats is out of range: %s %s", (sel, NUM_CASES))
    ## MIN = -1e20
    ## MAX = 1e20

    dsf = open(csvPathname, "w+")
    for i in range(rowCount):
        val = r.triangular(-1e9,1e9,0)
        s = h2o_util.fp_format(val, sel=sel) # use same format for all numbers
        rowData = [s for j in range(colCount)]
        rowDataCsv = ",".join(rowData) + "\n"
        dsf.write(rowDataCsv)
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
        h2o.tear_down_cloud()

    def test_plot_remove_keys(self):
        SYNDATASETS_DIR = h2o.make_syn_dir()

        tryList = [
            (100000, 100, 'cG', 400),
            (200000, 100, 'cH', 400),
            (400000, 100, 'cI', 400),
            (800000, 100, 'cJ', 400),
            (1000000, 100, 'cK', 400),
        ]
        
        xList = []
        eList = []
        fList = []
        for (rowCount, colCount, hex_key, timeoutSecs) in tryList:
            SEEDPERFILE = random.randint(0, sys.maxint)
            NUM_CASES = h2o_util.fp_format()
            sel = random.randint(0, NUM_CASES-1)
            csvFilename = "syn_%s_%s_%s_%s.csv" % (SEEDPERFILE, sel, rowCount, colCount)
            csvPathname = SYNDATASETS_DIR + '/' + csvFilename

            print "Creating random", csvPathname
            write_syn_dataset(csvPathname, rowCount, colCount, SEEDPERFILE, sel)

            parseResult = h2i.import_parse(path=csvPathname, schema='put', hex_key=hex_key, timeoutSecs=timeoutSecs, doSummary=False)
            pA = h2o_cmd.ParseObj(parseResult, expectedNumRows=rowCount, expectedNumCols=colCount)
            iA = h2o_cmd.InspectObj(pA.parse_key)
            parseElapsed = pA.python_elapsed
            parse_key = pA.parse_key
            numRows = iA.numRows
            numCols = iA.numCols
            print parse_key, parseElapsed, numRows, numCols

            labelList = iA.labelList
            node = h2o.nodes[0]

            print "Deleting", hex_key, "at", node.http_addr, "Shouldn't matter what node the delete happens at..global?"
            start = time.time()
            node.remove_key(hex_key, timeoutSecs=30)
            removeElapsed = time.time() - start
            print "Deleting", hex_key, "took", removeElapsed, "seconds"

            # xList.append(ntrees)
            xList.append(numRows)
            eList.append(parseElapsed)
            fList.append(removeElapsed)

        # just plot the last one
        if 1==1:
            xLabel = 'byteSize'
            eLabel = 'parseElapsed'
            fLabel = 'removeElapsed'
            eListTitle = ""
            fListTitle = ""
            h2o_gbm.plotLists(xList, xLabel, eListTitle, eList, eLabel, fListTitle, fList, fLabel)

if __name__ == '__main__':
    h2o.unit_main()

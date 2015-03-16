import unittest, time, sys, random
sys.path.extend(['.','..','../..','py'])
import h2o2 as h2o
import h2o_import as h2i, h2o_cmd

class Basic(unittest.TestCase):
    def tearDown(self):
        h2o.check_sandbox_for_errors()

    @classmethod
    def setUpClass(cls):
        h2o.init(1,java_heap_GB=10)

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()

    def test_delete_all_keys(self):
        # FIX! should have some model keys in here too, from RF etc.
        importFolderPath = 'standard'
        timeoutSecs = 500

        csvFilenameAll = [
            "covtype.data",
            "covtype20x.data",
            ]
        # csvFilenameList = random.sample(csvFilenameAll,1)
        csvFilenameList = csvFilenameAll
        for trial in range(2):
            for csvFilename in csvFilenameList:
                csvPathname = importFolderPath + "/" + csvFilename

                parseResult = h2i.import_parse(bucket='home-0xdiag-datasets', path=csvPathname, timeoutSecs=500)
                pA = h2o_cmd.ParseObj(parseResult)
                iA = h2o_cmd.InspectObj(pA.parse_key)
                parse_key = pA.parse_key
                numRows = iA.numRows
                numCols = iA.numCols
                labelList = iA.labelList

                h2i.delete_keys_at_all_nodes()
                print "Delete all keys. Shouldn't be any more?"
                h2o.nodes[0].remove_all_keys()

            print "\nTrial", trial, "completed\n"

if __name__ == '__main__':
    h2o.unit_main()

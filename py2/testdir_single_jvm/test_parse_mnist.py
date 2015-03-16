import unittest, sys, time
sys.path.extend(['.','..','../..','py'])
import h2o2 as h2o
import h2o_cmd, h2o_import as h2i

# avoid NPE due to KeyIndexed
DO_INTERMEDIATE_RESULTS = False

class Basic(unittest.TestCase):
    def tearDown(self):
        h2o.check_sandbox_for_errors()

    @classmethod
    def setUpClass(cls):
        # assume we're at 0xdata with it's hdfs namenode
        h2o.init(java_heap_GB=14)

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()

    def test_parse_mnist_A_training(self):
        importFolderPath = "mnist"
        csvFilelist = [
            ("mnist_training.csv.gz", 600),
            ("mnist_training.csv.gz", 600),
        ]

        trial = 0
        allDelta = []
        for (csvFilename,  timeoutSecs) in csvFilelist:
            testKey2 = csvFilename + "_" + str(trial) + ".hex"
            parseResult = h2i.import_parse(bucket='home-0xdiag-datasets', path=importFolderPath+"/"+csvFilename,
                hex_key=testKey2, timeoutSecs=timeoutSecs, intermediateResults=DO_INTERMEDIATE_RESULTS)

    def test_parse_mnist_B_testing(self):
        importFolderPath = "mnist"
        csvFilelist = [
            ("mnist_testing.csv.gz", 600),
            ("mnist_testing.csv.gz", 600),
        ]

        trial = 0
        allDelta = []
        for (csvFilename, timeoutSecs) in csvFilelist:
            testKey2 = csvFilename + "_" + str(trial) + ".hex"
            parseResult = h2i.import_parse(bucket='home-0xdiag-datasets', path=importFolderPath+"/"+csvFilename,
                hex_key=testKey2, timeoutSecs=timeoutSecs, intermediateResults=DO_INTERMEDIATE_RESULTS)


if __name__ == '__main__':
    h2o.unit_main()

import unittest, sys, time
sys.path.extend(['.','..','../..','py'])
import h2o2 as h2o
import h2o_cmd, h2o_import as h2i, h2o_jobs as h2j

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

    def test_parse_unlock(self):
        importFolderPath = "mnist"
        csvFilelist = [
            ("mnist_training.csv.gz", 600),
            ("mnist_testing.csv.gz", 600),
        ]

        trial = 0
        allDelta = []
        for (csvFilename,  timeoutSecs) in csvFilelist:
            hex_key = csvFilename + "_" + str(trial) + ".hex"
            # can't import the dir again while the src file is being parsed
            parseResult = h2i.import_parse(bucket='home-0xdiag-datasets', 
                path=importFolderPath+"/"+csvFilename,
                schema='put',
                hex_key=hex_key, timeoutSecs=timeoutSecs, 
                intermediateResults=DO_INTERMEDIATE_RESULTS,
                noPoll=True)
            trial += 1


        # can't unlock while jobs are running
        # Session WARN: java.lang.UnsupportedOperationException: Cannot unlock all keys since locking jobs are still running.
        h2j.pollWaitJobs()

        h2o.n0.unlock()
        # don't bother cancelling the jobs

if __name__ == '__main__':
    h2o.unit_main()

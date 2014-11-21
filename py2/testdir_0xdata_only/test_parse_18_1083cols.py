import unittest, sys, time
sys.path.extend(['.','..','../..','py'])
import h2o, h2o_cmd, h2o_import as h2i

print "18 files, each 1083 cols, almost 1GB each, uncompressed"
class Basic(unittest.TestCase):
    def tearDown(self):
        h2o.check_sandbox_for_errors()

    @classmethod
    def setUpClass(cls):
        global SEED
        SEED = h2o.setup_random_seed()
        # h2o.init(3, java_heap_GB=10)
        h2o.init(3, java_heap_GB=9)

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()

    def test_50_nongz_fvec(self):
        avgMichalSize = 237270000 * 2
        bucket = 'home-0xdiag-datasets'
        importFolderPath = "many_many"
        print "Using non-gz'ed files in", importFolderPath
        csvFilenameList= [
            ("*.dat", "file_18_A.dat", 18 * avgMichalSize, 1800),
        ]

        pollTimeoutSecs = 120
        retryDelaySecs = 10

        for trial, (csvFilepattern, csvFilename, totalBytes, timeoutSecs) in enumerate(csvFilenameList):
            csvPathname = importFolderPath + "/" + csvFilepattern

            (importResult, importPattern) = h2i.import_only(bucket=bucket, path=csvPathname, schema='local')
            importFullList = importResult['files']
            importFailList = importResult['fails']
            print "\n Problem if this is not empty: importFailList:", h2o.dump_json(importFailList)

            start = time.time()
            parseResult = h2i.import_parse(bucket=bucket, path=csvPathname, schema='local',
                hex_key=csvFilename + ".hex", timeoutSecs=timeoutSecs, 
                retryDelaySecs=retryDelaySecs,
                pollTimeoutSecs=pollTimeoutSecs)
            elapsed = time.time() - start
            print "Parse #", trial, "completed in", "%6.2f" % elapsed, "seconds.", \
                "%d pct. of timeout" % ((elapsed*100)/timeoutSecs)

            
            numRows, numCols, parse_key = h2o_cmd.infoFromParse(parseResult)
            h2o_cmd.columnInfoFromInspect(parse_key, exceptionOnMissingValues=False)

            if totalBytes is not None:
                fileMBS = (totalBytes/1e6)/elapsed
                msg = '{!s} jvms, {!s}GB heap, {:s} {:s} {:6.2f} MB/sec for {:.2f} secs'.format(
                    len(h2o.nodes), h2o.nodes[0].java_heap_GB, csvFilepattern, csvFilename, fileMBS, elapsed)
                print msg


if __name__ == '__main__':
    h2o.unit_main()

import unittest, sys
sys.path.extend(['.','..','../..','py'])

import h2o2 as h2o
import h2o_cmd, h2o_import as h2i
from h2o_test import find_file, dump_json, verboseprint

print "Do a hack to import files individually, then parse, to avoid Frames.json on unused files"

class Basic(unittest.TestCase):
    def tearDown(self):
        h2o.check_sandbox_for_errors()

    @classmethod
    def setUpClass(cls):
        h2o.init()

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()

    def test_parse_manyfile_hack(self):

        for trial in range(2):
            importFolderPath = "/home/0xdiag/datasets/manyfiles-nflx-gz"

            importList = []
            maxi = 50
            # 4-9 don't exist?
            for i in range(10, 10+maxi+1):
                csvFilename = "file_%s.dat.gz" % i
                csvPathname = importFolderPath + "/" + csvFilename
                importResult = h2o.n0.import_files(path=csvPathname)
                # just 1!
                import_key = importResult['keys'][0]
                assert len(importResult['keys'])==1
                assert len(importResult['files'])==1
                assert len(importResult['fails'])==0
                assert len(importResult['dels'])==0
                importList.append(import_key)
                

            timeoutSecs = 800
            parseResult = h2o.n0.parse(key=importList, timeoutSecs=timeoutSecs)
            numRows, numCols, parse_key = h2o_cmd.infoFromParse(parseResult)
            inspectResult = h2o_cmd.runInspect(key=parse_key)
            missingList, labelList, numRows, numCols = h2o_cmd.infoFromInspect(inspectResult)

            assert numRows == (maxi * 100000)
            assert numCols == 542

            # FIX! add summary. Do I need to do each col separately?

if __name__ == '__main__':
    h2o.unit_main()

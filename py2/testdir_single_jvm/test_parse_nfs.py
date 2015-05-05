import unittest, sys
sys.path.extend(['.','..','../..','py'])
import os

import h2o2 as h2o
import h2o_cmd, h2o_import as h2i, h2o_browse as h2b
from h2o_test import find_file, dump_json, verboseprint

expectedZeros = [0 for i in range(11)]

CAUSE_FAIL = False

def assertEqualMsg(a, b): assert a == b, "%s %s" % (a, b)

def parseKeyIndexedCheck(frames_result, multiplyExpected):
    # get the name of the frame?
    print ""
    frame = frames_result['frames'][0]
    rows = frame['rows']
    columns = frame['columns']
    for i,c in enumerate(columns):
        label = c['label']
        stype = c['type']
        missing = c['missing_count']
        zeros = c['zero_count']
        domain = c['domain']
        print "column: %s label: %s type: %s missing: %s zeros: %s domain: %s" %\
            (i,label,stype,missing,zeros,domain)

        # files are concats of covtype. so multiply expected
        # assertEqualMsg(zeros, expectedZeros[i] * multiplyExpected)
        # assertEqualMsg(label,"C%s" % (i+1))
        # assertEqualMsg(stype,"int")
        assertEqualMsg(missing, 0)
        # assertEqualMsg(domain, None)

class Basic(unittest.TestCase):
    def tearDown(self):
        h2o.check_sandbox_for_errors()

    @classmethod
    def setUpClass(cls):
        h2o.init()

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()

    def test_parse_nfs(self):
        print "run as user 0xcustomer on machine with nfs /mnt/0xcustomer-datasets/c1"
        tryList = [
            ('iris2.csv', 'iris2.hex', 1, 30),
        ]

        for (csvFilename, hex_key, multiplyExpected, timeoutSecs) in tryList:
            importFolderPath = "/mnt/0xcustomer-datasets/c1"
            csvPathname = importFolderPath + "/" + csvFilename
            parseResult  = h2i.import_parse(path=csvPathname, schema='local',
                timeoutSecs=timeoutSecs, hex_key=hex_key, chunk_size=4194304/2, doSummary=False)

            pA = h2o_cmd.ParseObj(parseResult)
            iA = h2o_cmd.InspectObj(pA.parse_key, expectedNumRows=150*multiplyExpected, 
                expectedNumCols=5, expectedMissinglist=[])
            print iA.missingList, iA.labelList, iA.numRows, iA.numCols

            for i in range(0):
                print "Summary on column", i
                co = h2o_cmd.runSummary(key=hex_key, column=i)

            k = parseResult['frames'][0]['frame_id']['name']
            frames_result = h2o.nodes[0].frames(key=k, row_count=5)
            # print "frames_result from the first parseResult key", dump_json(frames_result)
            parseKeyIndexedCheck(frames_result, multiplyExpected)

if __name__ == '__main__':
    h2o.unit_main()

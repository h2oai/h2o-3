import unittest, sys
sys.path.extend(['.','..','../..','py'])

import h2o2 as h2o
import h2o_cmd, h2o_import as h2i, h2o_browse as h2b
from h2o_test import find_file, dump_json, verboseprint

expectedZeros = [0, 4914, 656, 24603, 38665, 124, 13, 5, 1338, 51, 320216, 551128, 327648, 544044, 577981, 
573487, 576189, 568616, 579415, 574437, 580907, 580833, 579865, 548378, 568602, 551041, 
563581, 580413, 581009, 578167, 577590, 579113, 576991, 571753, 580174, 547639, 523260, 
559734, 580538, 578423, 579926, 580066, 465765, 550842, 555346, 528493, 535858, 579401, 
579121, 580893, 580714, 565439, 567206, 572262, 0]

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
        missing = c['missing']
        zeros = c['zeros']
        domain = c['domain']
        print "column: %s label: %s type: %s missing: %s zeros: %s domain: %s" %\
            (i,label,stype,missing,zeros,domain)

        # files are concats of covtype. so multiply expected
        assertEqualMsg(zeros, expectedZeros[i] * multiplyExpected)
        assertEqualMsg(label,"C%s" % (i+1))
        assertEqualMsg(stype,"int")
        assertEqualMsg(missing, 0)
        assertEqualMsg(domain, None)

class Basic(unittest.TestCase):
    def tearDown(self):
        h2o.check_sandbox_for_errors()

    @classmethod
    def setUpClass(cls):
        h2o.init(java_heap_GB=24)

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()

    def test_parse_covtype200x(self):

        tryList = [
            ('covtype200x.data', 200, 360),
        ]

        for (csvFilename, multiplyExpected, timeoutSecs) in tryList:
            # h2o-dev doesn't take ../.. type paths? make find_file return absolute pathj
            a_node = h2o.nodes[0]

            # import_result = a_node.import_files(path=find_file("smalldata/logreg/prostate.csv"))
            importFolderPath = "/home/0xdiag/datasets/standard"
            csvPathname = importFolderPath + "/" + csvFilename
            import_result = a_node.import_files(path=csvPathname)

            # print "import_result:", dump_json(import_result)
            k = import_result['keys'][0]
            frames_result = a_node.frames(key=k, len=5, timeoutSecs=timeoutSecs)
            # print "frames_result from the first import_result key", dump_json(frames_result)

            # how do you parse multiple files
            parse_result = a_node.parse(key=k, timeoutSecs=timeoutSecs)
            k = parse_result['frames'][0]['key']['name']
            # print "parse_result:", dump_json(parse_result)
            frames_result = a_node.frames(key=k, len=5)
            # print "frames_result from the first parse_result key", dump_json(frames_result)
            
            parseKeyIndexedCheck(frames_result, multiplyExpected)

if __name__ == '__main__':
    h2o.unit_main()

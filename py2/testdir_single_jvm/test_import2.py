import unittest, sys
sys.path.extend(['.','..','../..','py'])
import string

import h2o2 as h2o
import h2o_cmd, h2o_import as h2i, h2o_browse as h2b
from h2o_test import find_file, dump_json, verboseprint

expectedZeros = [0, 4914, 656, 24603, 38665, 124, 13, 5, 1338, 51, 320216, 551128, 327648, 544044, 577981, 
573487, 576189, 568616, 579415, 574437, 580907, 580833, 579865, 548378, 568602, 551041, 
563581, 580413, 581009, 578167, 577590, 579113, 576991, 571753, 580174, 547639, 523260, 
559734, 580538, 578423, 579926, 580066, 465765, 550842, 555346, 528493, 535858, 579401, 
579121, 580893, 580714, 565439, 567206, 572262, 0]

DO_2X_SRC = False
DO_TEST_BAD_COLNAME = False
DO_TEST_BAD_COL_LENGTH = False

DO_IMPORT_PARSE = True
SINGLE_CSVFILENAME = 'covtype.data.sorted'
SINGLE_CSVFILENAME = 'covtype.data'

def assertEqualMsg(a, b): assert a == b, "%s %s" % (a, b)

def parseKeyIndexedCheck(frames_result, multiplyExpected, expectedColumnNames):
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
        assertEqualMsg(label, expectedColumnNames[i])
        assertEqualMsg(stype,"int")
        # assertEqualMsg(missing, 0)
        assertEqualMsg(domain, None)

class Basic(unittest.TestCase):
    def tearDown(self):
        h2o.check_sandbox_for_errors()

    @classmethod
    def setUpClass(cls):
        h2o.init()

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()

    def test_parse_covtype(self):

        tryList = [
            (['covtype.data', 'covtype.shuffled.data', 'covtype.sorted.data'], 3, 30),
        ]

        for (csvFilenameList, multiplyExpected, timeoutSecs) in tryList:
            # h2o-dev doesn't take ../.. type paths? make find_file return absolute pathj
            a_node = h2o.nodes[0]

            # import_result = a_node.import_files(path=find_file("smalldata/logreg/prostate.csv"))
            importFolderPath = "/home/0xdiag/datasets/standard"

            # keep a list of the keys you import, to feed to parse
            kList = []
            for csvFilename in csvFilenameList:
                csvPathname = importFolderPath + "/" + csvFilename
                if not DO_IMPORT_PARSE:
                    import_result = a_node.import_files(path=csvPathname)
                    k = import_result['keys'][0]
                    frames_result = a_node.frames(key=k, row_count=5, timeoutSecs=timeoutSecs)
                    kList.append(k)
            # print "frames_result from the first import_result key", dump_json(frames_result)

            print "I think I imported these keys:", kList

            # what happens if I put the kList in twice? can it touch the same source file without lock issues?

            if DO_2X_SRC:
                kList2 = kList + kList
                multiplyExpected = 2 * multiplyExpected
            else:
                kList2 = kList

            # try passing column names also. 
            # questions to try
            # what if you pass missing (,,)
            # what if you pass too many, too few, or some with same name?

            # let's try all the characters
            basename = string.printable
            # remove the ',' in the string (remember strings are immutable..can't use .replace to remove
            # other characters are illegal? [] '
            if DO_TEST_BAD_COLNAME:
                basename = basename.translate(None, ",[]!#$%&'()*+-./:;<=>?@\^_`{|}~" + '"')
            else:
                basename = "abcd012345"

            colLength = 1 if DO_TEST_BAD_COL_LENGTH else 55
            expectedColumnNames = map(lambda x: basename + "_" + str(x+1), range(colLength))
            # need to quote each column name in the string passed 
            column_names = '[' + ','.join(map((lambda x: '"' + x + '"'), expectedColumnNames)) + ']'

            kwargs = {
                'column_names': column_names,
                'intermediateResults': False,
            }
            print kwargs
            if DO_IMPORT_PARSE:
                multiplyExpected = 1
                csvPathname = importFolderPath + "/" + SINGLE_CSVFILENAME
                parse_result = h2i.import_parse(path=csvPathname, timeoutSecs=timeoutSecs, **kwargs)
            else:
                parse_result = a_node.parse(key=kList2, timeoutSecs=timeoutSecs, **kwargs)

            k = parse_result['frames'][0]['frame_id']['name']
            # print "parse_result:", dump_json(parse_result)
            frames_result = a_node.frames(key=k, row_count=5)
            # print "frames_result from the first parse_result key", dump_json(frames_result)
            
            # we doubled the keyList, from what was in tryList

            parseKeyIndexedCheck(frames_result, multiplyExpected, expectedColumnNames)

        h2o.nodes[0].log_download()

if __name__ == '__main__':
    h2o.unit_main()

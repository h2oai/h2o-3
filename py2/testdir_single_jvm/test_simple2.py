import unittest, sys, time
sys.path.extend(['.','..','../..','py'])

import h2o2 as h2o
import h2o_cmd, h2o_import as h2i, h2o_browse as h2b, h2o_util
from h2o_test import find_file, dump_json, verboseprint

DO_INTERMEDIATE_RESULTS = False

class Basic(unittest.TestCase):
    def tearDown(self):
        h2o.check_sandbox_for_errors()

    @classmethod
    def setUpClass(cls):
        h2o.init()

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()

    def test_simple2(self):
        # h2o-dev doesn't take ../.. type paths? make find_file return absolute path
        # csvPathname = find_file("bigdata/laptop/poker-hand-testing.data")
        csvPathname = find_file("smalldata/logreg/prostate.csv")
        import_result = h2o.n0.import_files(path=csvPathname)
        # print dump_json(import_result)

        k = import_result['destination_frames'][0]
        frames_result = h2o.n0.frames(key=k)

        frame = frames_result['frames'][0]
        rows = frame['rows']
        columns = frame['columns']
        for c in columns:
            label = c['label']
            missing = c['missing_count']
            stype = c['type']
            domain = c['domain']

        # print dump_json(frame)

        # let's see what ray's util does
        frames = h2o.n0.frames()['frames']
        frames_dict = h2o_util.list_to_dict(frames, 'frame_id/name')
        # print "frames:", dump_json(frames)
        # print "frames_dict:", dump_json(frames_dict)
        for k,v in frames_dict.items():
            print "frames_dict key:", k

        # interesting. we can do dictionary comprehensions
        # { k:v for k,v in my_dict.items() if 'Peter' in k }

        # how do you parse multiple files
        parse_result = h2o.n0.parse(key=k, intermediateResults=DO_INTERMEDIATE_RESULTS)

        frame = parse_result['frames'][0]
        hex_key = frame['frame_id']['name']

        colCount = 9
        rowCount = 380
        # colCount = 11
        # rowCount = 1000000
        start = time.time()
        inspect = h2o_cmd.runInspect(None, hex_key)
        print "Inspect:", hex_key, "took", time.time() - start, "seconds"
        numCols = len(inspect['frames'][0]['columns'])
        numRows = inspect['frames'][0]['rows']
        print "\n" + csvPathname, \
            "    rows:", "{:,}".format(numRows), \
            "    len(columns):", "{:,}".format(numCols)

        # should match # of cols in header or ??
        self.assertEqual(numCols, colCount,
            "parse created result with the wrong number of cols %s %s" % (numCols, colCount))
        self.assertEqual(numRows, rowCount,
            "parse created result with the wrong number of rows (header shouldn't count) %s %s" % \
            (numRows, rowCount))

        verboseprint(hex_key, ":", dump_json(parse_result))

if __name__ == '__main__':
    h2o.unit_main()

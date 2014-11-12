import unittest, sys
sys.path.extend(['.','..','../..','py'])

import h2o, h2o_cmd, h2o_import as h2i, h2o_browse as h2b
from h2o_test import find_file, dump_json, verboseprint

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
        a_node = h2o.nodes[0]

        # import_result = a_node.import_files(path=find_file("smalldata/logreg/prostate.csv"))
        import_result = a_node.import_files(path=find_file("smalldata/poker/poker-hand-testing.data"))
        # print dump_json(import_result)

        k = import_result['keys'][0]
        # frames_result = a_node.frames(key=k[0], len=5)

        frames_result = a_node.frames(key=k)

        frame = frames_result['frames'][0]
        byteSize = frame['byteSize']
        rows = frame['rows']
        columns = frame['columns']
        for c in columns:
            label = c['label']
            missing = c['missing']
            stype = c['type']
            zeros = c['zeros']
            domain = c['domain']

        # print dump_json(frame)

        # how do you parse multiple files
        parse_result = a_node.parse(key=k)

        frame = parse_result['frames'][0]
        hex_key = frame['key']['name']

        verboseprint(hex_key, ":", dump_json(parse_result))

if __name__ == '__main__':
    h2o.unit_main()

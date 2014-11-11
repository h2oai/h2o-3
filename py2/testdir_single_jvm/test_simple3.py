import unittest, sys, time
sys.path.extend(['.','..','../..','py'])

import h2o, h2o_cmd, h2o_import as h2i, h2o_browse as h2b

class Basic(unittest.TestCase):
    def tearDown(self):
        h2o.check_sandbox_for_errors()

    @classmethod
    def setUpClass(cls):
        h2o.init()

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()

    def test_simple3(self):
        a = h2o.nodes[0].endpoints()
        print h2o.dump_json(a)
        print "There are %s endpoints" % len(a['routes'])
        for l in a['routes']:
            print l['url_pattern']

if __name__ == '__main__':
    h2o.unit_main()

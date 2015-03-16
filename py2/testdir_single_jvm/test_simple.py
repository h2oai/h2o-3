import unittest, sys, time
sys.path.extend(['.','..','../..','py'])

import h2o2 as h2o
import h2o_cmd, h2o_import as h2i, h2o_browse as h2b

class Basic(unittest.TestCase):
    def tearDown(self):
        h2o.check_sandbox_for_errors()

    @classmethod
    def setUpClass(cls):
        h2o.init()

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()

    def test_simple(self):
        print "\ncloud is supposed to be good. cloud_size and locked should be 1, 0 below"

        for i in range(10):
            a = h2o.nodes[0].get_cloud()
            consensus = a['consensus']
            locked = a['locked']
            cloud_size = a['cloud_size']
            cloud_name = a['cloud_name']
            version = a['version']

            print 'iteration %s: %s%s %s%s %s%s %s%s %s%s' % (i,
                "  consensus: ", consensus,
                "  locked: ", locked,
                "  cloud_size: ", cloud_size,
                "  cloud_name: ", cloud_name,
                "  version: ", version)

if __name__ == '__main__':
    h2o.unit_main()

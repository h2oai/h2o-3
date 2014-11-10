import unittest, sys, time
sys.path.extend(['.','..','../..','py'])

import h2o, h2o_cmd, h2o_import as h2i, h2o_browse as h2b

class Basic(unittest.TestCase):
    def tearDown(self):
        h2o.check_sandbox_for_errors()

    @classmethod
    def setUpClass(cls):
        h2o.init(8, java_heap_GB=2)

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()

    def test_simple(self):
        pass # put the browser in test_simple_browser.py because it fails

if __name__ == '__main__':
    h2o.unit_main()

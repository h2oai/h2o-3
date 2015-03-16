import unittest, time, sys, os
sys.path.extend(['.','..','../..','py'])
import h2o2 as h2o
import h2o_cmd, h2o_browse as h2b, h2o_args


class Basic(unittest.TestCase):
    def tearDown(self):
        h2o.check_sandbox_for_errors()

    @classmethod
    def setUpClass(cls):
        # Uses your username specific json: pytest_config-<username>.json
        h2o.init (1, 
            # use_hdfs=True, 
            # aws_credentials=os.path.expanduser('~/.ec2/AwsCredentials.properties'),
            # hdfs_config=os.path.expanduser("~/.ec2/core-site.xml"),
            java_heap_GB=12, 
            java_extra_args='-XX:+PrintGCDetails')

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()

    def test_with_a_browser(self):
        h2b.browseTheCloud()

        if not h2o_args.browse_disable:
            time.sleep(500000)

if __name__ == '__main__':

    h2o.unit_main()

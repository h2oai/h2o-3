import unittest, time, sys, random 
sys.path.extend(['.','..','../..','py'])
import h2o, h2o_cmd, h2o_glm, h2o_import as h2i, h2o_jobs, h2o_exec as h2e

DO_POLL = False
class Basic(unittest.TestCase):
    def tearDown(self):
        h2o.check_sandbox_for_errors()

    @classmethod
    def setUpClass(cls):
        h2o.init(java_heap_GB=4)

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()

    def test_frame_split(self):

        csvFilename = 'iris.csv'
        csvPathname = 'iris/' + csvFilename
        hex_key = "iris.hex"

        parseResult = h2i.import_parse(bucket='smalldata', path=csvPathname, hex_key=hex_key, schema='local', timeoutSecs=10)

        print "Just split away and see if anything blows up"
        splitMe = hex_key

        # don't split
        for s in range(10):
            fs = h2o.nodes[0].frame_split(training_frame=splitMe, ratios=0.5)
            split0_key = fs['split_keys'][0]
            split1_key = fs['split_keys'][1]
            split0_rows = fs['split_rows'][0]
            split1_rows = fs['split_rows'][1]
            split0_ratio = fs['split_ratios'][0]
            split1_ratio = fs['split_ratios'][1]
            print "Iteration", s, "split0_rows:", split0_rows, "split1_rows:", split1_rows
            splitMe = split0_key
            if split0_rows<=2:
                break


if __name__ == '__main__':
    h2o.unit_main()

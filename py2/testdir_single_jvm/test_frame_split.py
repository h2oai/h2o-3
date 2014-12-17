import unittest, time, sys, random 
sys.path.extend(['.','..','../..','py'])
import h2o, h2o_cmd, h2o_glm, h2o_import as h2i, h2o_jobs, h2o_exec as h2e

from h2o_test import dump_json, OutputObj, verboseprint

DO_POLL = False
class Basic(unittest.TestCase):
    def tearDown(self):
        h2o.check_sandbox_for_errors()

    @classmethod
    def setUpClass(cls):
        h2o.init(3, java_heap_GB=4)

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()

    def test_frame_split(self):

        csvFilename = 'covtype.data'
        csvPathname = 'standard/' + csvFilename
        hex_key = "covtype.hex"

        parseResultA = h2i.import_parse(bucket='home-0xdiag-datasets', path=csvPathname, hex_key=hex_key, schema='local', timeoutSecs=20)
        pA = h2o_cmd.ParseObj(parseResultA)
        print pA.numRows
        print pA.numCols
        print pA.parse_key

        print "Just split away and see if anything blows up"
        splitMe = hex_key
        iA = h2o_cmd.InspectObj(splitMe)
        origNumRows = iA.numRows
        origNumCols = iA.numCols
        for s in range(20):
            iA = h2o_cmd.InspectObj(splitMe)
            numRows = iA.numRows

            fsResult = h2o.nodes[0].frame_split(training_frame=splitMe, ratios='[0.5]')
            fs = OutputObj(fsResult, 'frame_split')
            model_key = fs.jobs[0].dest.name

            modelResult = h2o.n0.models(key=model_key)
            model = OutputObj(modelResult['models'][0]['output'], 'frame_split')
            # print "model:", dump_json(model)
            split_keys = [split._key.name for split in model.splits]

            # split0_key = fs['split_keys'][0]
            # split1_key = fs['split_keys'][1]
            # split0_rows = fs['split_rows'][0]
            # split1_rows = fs['split_rows'][1]
            # split0_ratio = fs['split_ratios'][0]
            # split1_ratio = fs['split_ratios'][1]

            iB = h2o_cmd.InspectObj(split_keys[0])
            iC = h2o_cmd.InspectObj(split_keys[1])

            numCols = iB.numCols
            split0_rows = iB.numRows
            split1_rows = iC.numRows

            # print "Iteration", s, "split0_rows:", split0_rows, "split1_rows:", split1_rows
            splitMe = split_keys[1]
            # split should be within 1 row accuracy. let's say within 20 for now
            self.assertLess(abs(split1_rows - split0_rows), 2)
            self.assertEqual(numRows, (split1_rows + split0_rows))
            self.assertEqual(numCols, origNumCols)
            if split1_rows <= 1:
                break


if __name__ == '__main__':
    h2o.unit_main()

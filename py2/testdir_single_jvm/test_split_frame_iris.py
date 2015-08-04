import unittest, time, sys, random 
sys.path.extend(['.','..','../..','py'])
import h2o2 as h2o
import h2o_cmd, h2o_glm, h2o_import as h2i, h2o_jobs, h2o_exec as h2e

from h2o_test import OutputObj, dump_json

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

    def test_split_frame(self):

        csvFilename = 'iris.csv'
        csvPathname = 'iris/' + csvFilename
        hex_key = "iris.hex"

        parseResultA = h2i.import_parse(bucket='smalldata', path=csvPathname, hex_key=hex_key, timeoutSecs=10)

        print "Just split away and see if anything blows up"
        splitMe = hex_key

        pA = h2o_cmd.ParseObj(parseResultA)
        print pA.numRows
        print pA.numCols
        print pA.parse_key

        print "Just split away and see if anything blows up"
        splitMe = hex_key
        iA = h2o_cmd.InspectObj(splitMe)
        origNumRows = iA.numRows
        origNumCols = iA.numCols
        for s in range(10):
            iA = h2o_cmd.InspectObj(splitMe)
            numRows = iA.numRows

            fsResult = h2o.n0.split_frame(dataset=splitMe, ratios='[0.5]')
            fs = OutputObj(fsResult, 'split_frame')
            d = fs.jobs[0].destination_frames

            # modelResult = h2o.n0.models(key=model_key)
            # model = OutputObj(modelResult['models'][0]['output'], 'split_frame')
            # print "model:", dump_json(model)
            split_keys = [split.name for split in d]


            # modelResult = h2o.n0.models(key=model_key)
            # model = OutputObj(modelResult['models'][0]['output'], 'split_frame')
            # print "model:", dump_json(model)
            # split_keys = [split._key.name for split in model.splits]

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


if __name__ == '__main__':
    h2o.unit_main()

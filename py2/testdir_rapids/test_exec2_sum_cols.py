import unittest, random, sys, time
sys.path.extend(['.','..','../..','py'])
import h2o2 as h2o
import h2o_cmd, h2o_browse as h2b, h2o_import as h2i, h2o_util, h2o_exec
from h2o_xl import Assign, Fcn, Key, Expr

class Basic(unittest.TestCase):
    def tearDown(self):
        h2o.check_sandbox_for_errors()

    @classmethod
    def setUpClass(cls):
        global SEED
        SEED = h2o.setup_random_seed()
        h2o.init(2)

        global SYNDATASETS_DIR
        SYNDATASETS_DIR = h2o.make_syn_dir()

    @classmethod
    def tearDownClass(cls):
        # wait while I inspect things
        # time.sleep(1500)
        h2o.tear_down_cloud()

    def test_exec2_sum(self):
        print "Replicating covtype.data by 2x for results comparison to 1x"
        filename1x = 'covtype.data'
        pathname1x = h2i.find_folder_and_filename('home-0xdiag-datasets', 'standard/covtype.data', returnFullPath=True)
        filename2x = "covtype_2x.data"
        pathname2x = SYNDATASETS_DIR + '/' + filename2x
        h2o_util.file_cat(pathname1x, pathname1x, pathname2x)

        csvAll = [
            (pathname1x, "cA", 5,  1),
            (pathname2x, "cB", 5,  2),
            (pathname2x, "cC", 5,  2),
        ]

        # h2b.browseTheCloud()
        lenNodes = len(h2o.nodes)

        firstDone = False
        for (csvPathname, hex_key, timeoutSecs, resultMult) in csvAll:
            parseResultA = h2i.import_parse(path=csvPathname, schema='put', hex_key=hex_key)
            pA = h2o_cmd.ParseObj(parseResultA)
            print pA.numRows
            print pA.numCols
            print pA.parse_key
            iA = h2o_cmd.InspectObj(pA.parse_key)

            k = Key(hex_key)
            colResultList = []
            for i in range(pA.numCols):
                result = Expr(Fcn('sum', k[:,i], True)).result
                colResultList.append(result)
            print "\ncolResultList", colResultList

            if not firstDone:
                colResultList0 = list(colResultList)
                good = [float(x) for x in colResultList0] 
                firstDone = True
            else:
                print "\n", colResultList0, "\n", colResultList
                # create the expected answer...i.e. N * first
                compare = [float(x)/resultMult for x in colResultList] 
                print "\n", good, "\n", compare
                self.assertEqual(good, compare, 'compare is not equal to good (first try * resultMult)')

if __name__ == '__main__':
    h2o.unit_main()

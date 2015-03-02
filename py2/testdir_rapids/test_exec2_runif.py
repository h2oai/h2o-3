import unittest, random, sys, time
sys.path.extend(['.','..','../..','py'])
import h2o2 as h2o
import h2o_browse as h2b, h2o_exec as h2e, h2o_import as h2i, h2o_cmd, h2o_util

from h2o_xl import KeyIndexed, Fcn, Seq, Colon, Assign, Item, Col, Xbase


DO_UNIMPLEMENTED = False
class Basic(unittest.TestCase):
    def tearDown(self):
        h2o.check_sandbox_for_errors()

    @classmethod
    def setUpClass(cls):
        global SEED
        SEED = h2o.setup_random_seed()
        h2o.init(1)

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()

    def test_exec2_runif(self):
        print "in h2o-dev, params are column, min, max, seed"
        bucket = 'home-0xdiag-datasets'
        csvPathname = 'standard/covtype.data'
        hexKey = 'r.hex'
        parseResult = h2i.import_parse(bucket=bucket, path=csvPathname, schema='put', hex_key=hexKey)
        # work up to the failing case incrementally
        execExprList = [
            # hack to make them keys? (not really needed but interesting)
            # params for h2o-dev runif are: column, min, max, seed 
            AssignObj('r0.hex', KeyIndexed('r.hex',col=0) ),
            AssignObj('s0.hex', Fcn("h2o.runif", KeyIndexed('r.hex', col=0), 1) ),
            AssignObj('s1.hex', Fcn("h2o.runif", KeyIndexed('r.hex', col=1), -1)  ),
            AssignObj('s2.hex', Fcn("h2o.runif", KeyIndexed('r.hex', col=54), -1) ),
        ]

        results = []
        for execExpr in execExprList:
            start = time.time()
            result = execExpr.do(timeoutSecs=30)
            results.append(result)
            execResult = execExpr.execResult
            print "exec took", time.time() - start, "seconds"
            print "exec result:", result
            print "exec result (full):", h2o.dump_json(execResult)
            h2o.check_sandbox_for_errors()

        rSummary = h2o_cmd.runSummary(key='r0.hex', cols='0')
        # h2o_cmd.infoFromSummary(rSummary)

        rSummary = h2o_cmd.runSummary(key='s0.hex', cols='0')
        # h2o_cmd.infoFromSummary(rSummary)

        sSummary = h2o_cmd.runSummary(key='s1.hex', cols='0')
        # h2o_cmd.infoFromSummary(sSummary)

        sSummary = h2o_cmd.runSummary(key='s2.hex', cols='0')
        # h2o_cmd.infoFromSummary(sSummary)

        # since there are no NAs in covtype, r.hex and s.hex should be identical?
        if 1==0:
            print "Comparing summary of r.hex to summary of s.hex"
            df = h2o_util.JsonDiff(rSummary, sSummary, with_values=True)
            # time can be different
            print "df.difference:", h2o.dump_json(df.difference)
            self.assertLess(len(df.difference), 2)
        

            print "results from the individual exec expresssions (ignore last which was an apply)"
            print "results:", results
            self.assertEqual(results, [0.0, 0.0, 0.0, 1859.0, 581012.0, 581012.0, 2959.365300544567, 1859.0, 1859.0])



if __name__ == '__main__':
    h2o.unit_main()

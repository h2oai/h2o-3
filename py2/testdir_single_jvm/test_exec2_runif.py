import unittest, random, sys, time
sys.path.extend(['.','..','../..','py'])
import h2o, h2o_browse as h2b, h2o_exec as h2e, h2o_import as h2i, h2o_cmd, h2o_util

from h2o_xexec import Frame, Fcn, Seq, Colon, Assign, Item, Col, Xbase


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
        print "h2o syntax is not full R. Doesn't take min/max interval params. assumed 0/1 interval"
        print " just one param, it must be a column or row vector. Result is same length"
        print " R allows a scalar to be param"
        bucket = 'home-0xdiag-datasets'
        csvPathname = 'standard/covtype.data'
        hexKey = 'r.hex'
        parseResult = h2i.import_parse(bucket=bucket, path=csvPathname, schema='put', hex_key=hexKey)
        # work up to the failing case incrementally
        execExprList = [
            # hack to make them keys? (not really needed but interesting)
            # 'r0.hex = r.hex[,1]',
            str(Assign('r0.hex', Frame('r.hex',col=0)) ),
            # 's0.hex = runif(r.hex[,1],-1)',
            str(Assign('s0.hex', Fcn("runif", Frame('r.hex',col=0), -1)) ),
            's1.hex = runif(r.hex[,1],-1)',
            str(Assign('s1.hex', Fcn("runif", Frame('r.hex',col=0), -1)) ),
            's2.hex = runif(r.hex[,1],-1)',
            str(Assign('s2.hex', Fcn("runif", Frame('r.hex',col=0), -1)) ),
            # error. this causes exception
            # 's3.hex = runif(nrow(r.hex), -1)',
        ]

        results = []
        for execExpr in execExprList:
            start = time.time()
            (execResult, result) = h2e.exec_expr(execExpr=execExpr, timeoutSecs=30) # unneeded but interesting 
            results.append(result)
            print "exec end on ", "operators" , 'took', time.time() - start, 'seconds'
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

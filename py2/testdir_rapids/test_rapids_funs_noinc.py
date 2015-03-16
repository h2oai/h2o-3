import unittest, random, sys, time, re
sys.path.extend(['.','..','../..','py'])
import h2o2 as h2o
import h2o_browse as h2b, h2o_exec as h2e, h2o_import as h2i, h2o_cmd
from h2o_test import dump_json, verboseprint

class Basic(unittest.TestCase):
    def tearDown(self):
        h2o.check_sandbox_for_errors()

    @classmethod
    def setUpClass(cls):
        global SEED
        SEED = h2o.setup_random_seed()
        h2o.init(1, base_port=54333)

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()

    def test_rapids_basic_with_funs_noinc(self):
        bucket = 'smalldata'
        csvPathname = 'iris/iris_wheader.csv'
        hexKey = 'r1'
        parseResult = h2i.import_parse(bucket=bucket, path=csvPathname, schema='put', hex_key=hexKey)

        keys = []
        for i in range(100):
            if i==0:
                # should never see v as a key from the function?
                execExpr1 = '(= !v1 (c {#0}))'
                execResult, result = h2e.exec_expr(h2o.nodes[0], execExpr1, resultKey='v1', timeoutSecs=5)
                execExpr2 = '(= !v2 (cbind %v1 ))'
                execResult, result = h2e.exec_expr(h2o.nodes[0], execExpr2, resultKey='v2', timeoutSecs=5)
            else:
                # adding to v shouldn't hurt, but not required cause function output will update it
                # execExpr1 = '(= !v (+ %v #1))'
                # execExpr1 = '(+ %v #1)'
                # add to itself?
                execExpr1 = '(+ %v %v)'
                funs = '[(def anon {v} %s;;;)]' % execExpr1
                execResult, result = h2e.exec_expr(h2o.nodes[0], funs, resultKey=None, timeoutSecs=5, doFuns=True)
                # execExpr2 = '(= !v2 (anon ([ %v2 "null" #0)))'
                # execExpr2 = '(= !v2 (anon %v2))'
                execExpr2 = '(= !v2 (+ %v2 #1))'
                execResult, result = h2e.exec_expr(h2o.nodes[0], execExpr2, resultKey='v2', timeoutSecs=15)


            # see if the execExpr had a lhs assign. If so, it better be in the storeview
            r = re.search('![a-zA-Z0-9]+', execExpr2)
            if r:
                lhs = r.group(0)[1:]
                print "Found key lhs assign", lhs

                # FIX! check if v is ever there.

                # KeyIndexeds gets too many rollup stats problems. Don't use for now
                if 1==0: 
                    inspect = h2o_cmd.runInspect(key=lhs)
                    missingList, labelList, numRows, numCols = infoFromInspect(inspect)

                    storeview = h2o_cmd.runStoreView()
                    print "\nstoreview:", dump_json(storeview)
                    if not k in storeView['keys']:
                        raise Exception("Expected to find %s in %s", (k, storeView['keys']))
            else: 
                print "No key lhs assign"

            # rows might be zero!
            if execResult['num_rows'] or execResult['num_cols']:
                keys.append(execExpr2)

        print "\nExpressions that created keys"
        for k in keys:
            print k

        # for execExpr in exprList:
        #     h2e.exec_expr(execExpr=execExpr, resultKey=None, timeoutSecs=10)

        h2o.check_sandbox_for_errors()


if __name__ == '__main__':
    h2o.unit_main()

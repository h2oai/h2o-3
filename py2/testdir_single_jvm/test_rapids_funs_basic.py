import unittest, random, sys, time
sys.path.extend(['.','..','../..','py'])
import h2o, h2o_browse as h2b, h2o_exec as h2e, h2o_import as h2i

initList = [0]

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

    def test_rapids_basic(self):
        bucket = 'smalldata'
        csvPathname = 'iris/iris_wheader.csv'
        hexKey = 'r1'
        parseResult = h2i.import_parse(bucket=bucket, path=csvPathname, schema='put', hex_key=hexKey)

        keys = []
        for execExpr in initList:
            # funs is an array ??
            # execExpr = "[(def fcn {a b c}{%s;;;})]" % execExpr
            execExpr = "[(def cn{aa}{(c {#1,#2})};;(c {#1,#2});;;})]" 
            execResult, result = h2e.exec_expr(h2o.nodes[0], execExpr, doFuns=True, resultKey=None, timeoutSecs=4)
            if execResult['num_rows']:
                keys.append(execExpr)
            

        print "\nExpressions that created keys"
        for k in keys:
            print k

        # for execExpr in exprList:
        #     h2e.exec_expr(execExpr=execExpr, resultKey=None, timeoutSecs=10)

        h2o.check_sandbox_for_errors()


if __name__ == '__main__':
    h2o.unit_main()

import unittest, random, sys, time, re
sys.path.extend(['.','..','../..','py'])
import h2o2 as h2o
import h2o_browse as h2b, h2o_exec as h2e, h2o_import as h2i, h2o_gbm, h2o_cmd


class Basic(unittest.TestCase):
    def tearDown(self):
        h2o.check_sandbox_for_errors()

    @classmethod
    def setUpClass(cls):
        global SEED
        SEED = h2o.setup_random_seed()
        h2o.init(1, java_heap_GB=12, base_port=54333)

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()

    def test_rapids_vec_fail(self):
        start = time.time()
        xList = []
        eList = []
        fList = []

        bucket = 'smalldata'
        csvPathname = 'iris/iris_wheader.csv'
        hexKey = 'r1'
        parseResult = h2i.import_parse(bucket=bucket, path=csvPathname, schema='put', hex_key=hexKey)

        keys = []
        # stop if > 1G (fails memory cleaner assetion
        maxx = 29
        # for trial in range(maxx):
        for trial in range(int(1e6),int(8e6),int(1e6)):
            
            # length = (2 ** trial)
            # execExpr = '(= !v (c {(: #0 #%s)})' % (length - 1)
            length = trial

            execExpr = '(= !vreal (c {(: #0 #%s)})' % (length - 1)
            start = time.time()
            execResult, result = h2e.exec_expr(h2o.nodes[0], execExpr, resultKey=None, timeoutSecs=10)
            elapsed1 = time.time() - start
            if execResult['num_rows']:
                keys.append(execExpr)

            # change it to all 1s? v = v==0
            execExpr = '(= !vint (N %vreal #0))'
            execResult, result = h2e.exec_expr(h2o.nodes[0], execExpr, resultKey=None, timeoutSecs=10)

            # comparing the sum times for int vs real..maybe the other guy isn't real. at least: different compression
            # execExpr = '(= !v (+ (+ %v %v) (+ %v %v))'


            # recursively expand
            execExpr = '(= !v2 (+ %vint <patt>))'
            for j in range(3):
                execExpr = re.sub('<patt>', '(+ %vint <patt>)', execExpr)
            # last one
            execExpr = re.sub('<patt>', '(+ %vint %vint)', execExpr)

            start = time.time()
            execResult, result = h2e.exec_expr(h2o.nodes[0], execExpr, resultKey=None, timeoutSecs=10)
            elapsed2 = time.time() - start

            execExpr = '(= !v1 (+ %vreal %vreal))'
            start = time.time()
            execResult, result = h2e.exec_expr(h2o.nodes[0], execExpr, resultKey=None, timeoutSecs=10)
            elapsed1 = time.time() - start

            inspectResult = h2o_cmd.runInspect(key='vreal')
            h2o_cmd.infoFromInspect(inspectResult)

            inspectResult = h2o_cmd.runInspect(key='vint')
            h2o_cmd.infoFromInspect(inspectResult)

            summaryResult = h2o_cmd.runSummary(key='vreal')

            if execResult['num_rows']:
                keys.append(execExpr)
            
            xList.append(length)
            eList.append(elapsed1)
            fList.append(elapsed2)


        if 1==1:
            xLabel = 'vector length'
            eLabel = 'elapsed (v1 = vint + vint)'
            fLabel = 'elapsed (v2 = vreal + vreal)'
            eListTitle = ""
            fListTitle = ""
            h2o_gbm.plotLists(xList, xLabel, eListTitle, eList, eLabel, fListTitle, fList, fLabel)



        print "\nExpressions that created keys"
        for k in keys:
            print k

        # for execExpr in exprList:
        #     h2e.exec_expr(execExpr=execExpr, resultKey=None, timeoutSecs=10)

        h2o.check_sandbox_for_errors()


if __name__ == '__main__':
    h2o.unit_main()

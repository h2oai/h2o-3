import unittest, random, sys, time, getpass
sys.path.extend(['.','..','../..','py'])
import h2o, h2o_browse as h2b, h2o_exec as h2e, h2o_import as h2i, h2o_cmd

# new ...ability to reference cols
# src[ src$age<17 && src$zip=95120 && ... , ]
# can specify values for enums ..values are 0 thru n-1 for n enums
print "FIX!: need to test the && and || reduction operators"
initList = [
        ]

from h2o_xl import KeyIndexed, Fcn, Seq, Colon, Assign, Item, Col

DO_SUM = False

if DO_SUM:
    exprList = [
            # 'a=c(1); a = sum(r1[1,])',
            Assign('a', Col(Seq(1))).ast(),
            Assign('a', Fcn('sum', KeyIndexed('r1', row=1))).ast(),
            # 'b=c(1); b = sum(r1[1,])',
            Assign('b', Col(Seq(1))).ast(),
            Assign('b', Fcn('sum', KeyIndexed('r1', row=1))).ast(),
            # 'd=c(1); d = sum(r1[1,])'(),
            Assign('d', Col(Seq(1))).ast(),
            Assign('d', Fcn('sum', KeyIndexed('r1', row=1))).ast(),
            # 'e=c(1); e = sum(r1[1,])',
            Assign('e', Col(Seq(1))).ast(),
            Assign('e', Fcn('sum', KeyIndexed('r1', row=1))).ast(),
            # 'f=c(1); f = sum(r1[1,])',
            Assign('f', Col(Seq(1))).ast(),
            Assign('f', Fcn('sum', KeyIndexed('r1', row=1))).ast(),
            # 'f=c(1); g = sum(r1[1,])',
            Assign('g', Col(Seq(1))).ast(),
            Assign('g', Fcn('sum', KeyIndexed('r1', row=1))).ast(),
            # 'h=c(1); h = sum(r1[1,])',
            Assign('h', Col(Seq(1))).ast(),
            Assign('h', Fcn('sum', KeyIndexed('r1', row=1))).ast(),
            ]
else:
    exprList = [
            # 'a=c(1); a = log(r1[1,])',
            Assign('a', Col(Seq(1))).ast(),
            # can't force a scalar to have a key be created?
            Assign('a', Fcn('log', KeyIndexed('r1', row=1))).ast(),
            # 'b=c(1); b = log(r1[1,])',
            Assign('b', Col(Seq(1))).ast(),
            Assign('b', Fcn('log', KeyIndexed('r1', row=1))).ast(),
            # 'd=c(1); d = log(r1[1,])'(),
            Assign('d', Col(Seq(1))).ast(),
            Assign('d', Fcn('log', KeyIndexed('r1', row=1))).ast(),
            # 'e=c(1); e = log(r1[1,])',
            Assign('e', Col(Seq(1))).ast(),
            Assign('e', Fcn('log', KeyIndexed('r1', row=1))).ast(),
            # 'f=c(1); f = log(r1[1,])',
            Assign('f', Col(Seq(1))).ast(),
            Assign('f', Fcn('log', KeyIndexed('r1', row=1))).ast(),
            # 'f=c(1); g = log(r1[1,])',
            Assign('g', Col(Seq(1))).ast(),
            Assign('g', Fcn('log', KeyIndexed('r1', row=1))).ast(),
            # 'h=c(1); h = log(r1[1,])',
            Assign('h', Col(Seq(1))).ast(),
            Assign('h', Fcn('log', KeyIndexed('r1', row=1))).ast(),
            ]

class Basic(unittest.TestCase):
    def tearDown(self):
        h2o.check_sandbox_for_errors()

    @classmethod
    def setUpClass(cls):
        global SEED
        SEED = h2o.setup_random_seed()
        h2o.init(1, java_heap_GB=28)

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()

    def test_exec2_sum(self):
        bucket = 'home-0xdiag-datasets'
        # csvPathname = 'airlines/year2013.csv'
        if getpass.getuser()=='jenkins':
            csvPathname = 'standard/billion_rows.csv.gz'
        else:
            csvPathname = '1B/reals_100000x1000_15f.data'
            csvPathname = '1B/reals_1B_15f.data'
            csvPathname = '1B/reals_1000000x1000_15f.data'

        hex_key = 'r1'
        parseResult = h2i.import_parse(bucket=bucket, path=csvPathname, schema='local', 
            hex_key=hex_key, timeoutSecs=3000, retryDelaySecs=2)
        inspect = h2o_cmd.runInspect(key=hex_key)
        missingList, labelList, numRows, numCols = h2o_cmd.infoFromInspect(inspect)

        for execExpr in exprList:
            start = time.time()
            execResult, result = h2e.exec_expr(h2o.nodes[0], execExpr, resultKey=None, timeoutSecs=300)
            print 'exec took', time.time() - start, 'seconds'
            print "result:", result

        h2o.check_sandbox_for_errors()


if __name__ == '__main__':
    h2o.unit_main()

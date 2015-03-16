import unittest, random, sys, time, getpass
sys.path.extend(['.','..','../..','py'])
import h2o2 as h2o
import h2o_browse as h2b, h2o_exec as h2e, h2o_import as h2i, h2o_cmd

# new ...ability to reference cols
# src[ src$age<17 && src$zip=95120 && ... , ]
# can specify values for enums ..values are 0 thru n-1 for n enums
print "FIX!: need to test the && and || reduction operators"
initList = [
        ]

from h2o_xl import Key, AssignObj, Fcn

DO_SUM = False

r1 = Key('r1')

if DO_SUM:
    funstr = 'sum'
    exprList = [
            AssignObj('a', Fcn(funstr, r1[1], r1[2]) ),
            AssignObj('b', 1),
            AssignObj('b', Fcn(funstr, r1[1], r1[2]) ),
            AssignObj('d', 1),
            AssignObj('d', Fcn(funstr, r1[1], r1[2]) ),
            AssignObj('e', 1),
            AssignObj('e', Fcn(funstr, r1[1], r1[2]) ),
            AssignObj('f', 1),
            AssignObj('f', Fcn(funstr, r1[1], r1[2]) ),
            AssignObj('g', 1),
            AssignObj('g', Fcn(funstr, r1[1], r1[2]) ),
            AssignObj('h', 1),
            AssignObj('h', Fcn(funstr, r1[1], r1[2]) ),
            ]
else:
    funstr = 'log'
    exprList = [
            AssignObj('a', Fcn(funstr, r1[1]) ),
            AssignObj('b', 1),
            AssignObj('b', Fcn(funstr, r1[1]) ),
            AssignObj('d', 1),
            AssignObj('d', Fcn(funstr, r1[1]) ),
            AssignObj('e', 1),
            AssignObj('e', Fcn(funstr, r1[1]) ),
            AssignObj('f', 1),
            AssignObj('f', Fcn(funstr, r1[1]) ),
            AssignObj('g', 1),
            AssignObj('g', Fcn(funstr, r1[1]) ),
            AssignObj('h', 1),
            AssignObj('h', Fcn(funstr, r1[1]) ),
            ]



class Basic(unittest.TestCase):
    def tearDown(self):
        h2o.check_sandbox_for_errors()

    @classmethod
    def setUpClass(cls):
        global SEED
        SEED = h2o.setup_random_seed()
        h2o.init(1, java_heap_GB=12)

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()

    def test_exec2_sum(self):
        bucket = 'home-0xdiag-datasets'
        # csvPathname = 'airlines/year2013.csv'
        if getpass.getuser()=='jenkins':
            csvPathname = 'standard/billion_rows.csv.gz'
        else:
            # csvPathname = '1B/reals_1B_15f.data'
            csvPathname = '1B/reals_1000000x1000_15f.data'

        hex_key = 'r1'
        parseResult = h2i.import_parse(bucket=bucket, path=csvPathname, schema='local', 
            hex_key=hex_key, timeoutSecs=3000, retryDelaySecs=2)
        inspect = h2o_cmd.runInspect(key=hex_key)
        missingList, labelList, numRows, numCols = h2o_cmd.infoFromInspect(inspect)

        for execExpr in exprList:
            start = time.time()
            execExpr.do()
            print 'exec took', time.time() - start, 'seconds'

        h2o.check_sandbox_for_errors()


if __name__ == '__main__':
    h2o.unit_main()

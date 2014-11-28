import unittest, random, sys, time, getpass
sys.path.extend(['.','..','../..','py'])
import h2o, h2o_browse as h2b, h2o_exec as h2e, h2o_import as h2i, h2o_cmd
from h2o_xexec import Frame, Fcn, Seq, Colon, Assign, Item, Col

# http://stackoverflow.com/questions/6558921/r-boolean-operators-and
# The shorter ones are vectorized, meaning they can return a vector, like this:
# 
# > ((-2:2) >= 0) & ((-2:2) <= 0)
# [1] FALSE FALSE  TRUE FALSE FALSE
# 
# The longer form evaluates left to right examining only the first element of each vector, so the above gives
# 
# > ((-2:2) >= 0) && ((-2:2) <= 0)
# [1] FALSE
# 
# As the help page says, this makes the longer form "appropriate for programming control-flow and [is] typically preferred in if clauses."
# 
# So you want to use the long forms only when you are certain the vectors are length one.
# 
# You should be absolutely certain your vectors are only length 1, such as in cases where they are functions that return only length 1 booleans. You want to use the short forms if the vectors are length possibly >1. So if you're not absolutely sure, you should either check first, or use the short form and then use all and any to reduce it to length one for use in control flow statements, like if.
# 
# The functions all and any are often used on the result of a vectorized comparison to see if all or any of the comparisons are true, respectively. The results from these functions are sure to be length 1 so they are appropriate for use in if clauses, while the results from the vectorized comparison are not. (Though those results would be appropriate for use in ifelse.
# 
# One final difference: the && and || only evaluate as many terms as they need to (which seems to be what is meant by short-circuiting). For example, here's a comparison using an undefined value a; if it didn't short-circuit, as & and | don't, it would give an error.
# also see http://www.burns-stat.com/pages/Tutor/R_inferno.pdf
initList = [
        # 'a=c(1); a = sum(r1[1,])',
        Assign('a', Col(Seq(1,0,0))).ast(),
    ]

exprList = [
        Assign('a', Fcn('&&', Frame('r1', row=1), Frame('r1', row=2),)).ast(),
        # 'b=c(1); b = sum(r1[1,])',
        Assign('b', Col(Seq(1))).ast(),
        Assign('b', Fcn('&&', Frame('r1', row=1), Frame('r1', row=2),)).ast(),
        # 'd=c(1); d = sum(r1[1,])'(),
        Assign('d', Col(Seq(1))).ast(),
        Assign('d', Fcn('&&', Frame('r1', row=1), Frame('r1', row=2),)).ast(),
        # 'e=c(1); e = sum(r1[1,])',
        Assign('e', Col(Seq(1))).ast(),
        Assign('e', Fcn('||', Frame('r1', row=1), Frame('r1', row=2),)).ast(),
        # 'f=c(1); f = sum(r1[1,])',
        Assign('f', Col(Seq(1))).ast(),
        Assign('f', Fcn('||', Frame('r1', row=1), Frame('r1', row=2),)).ast(),
        # 'f=c(1); g = sum(r1[1,])',
        Assign('g', Col(Seq(1))).ast(),
        Assign('g', Fcn('||', Frame('r1', row=1), Frame('r1', row=2),)).ast(),
        # 'h=c(1); h = sum(r1[1,])',
        Assign('h', Col(Seq(1))).ast(),
        Assign('h', Fcn('||', Frame('r1', row=1), Frame('r1', row=2),)).ast(),
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

    def test_exec2_reduction(self):
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

        for execExpr in initList:
            execResult, result = h2e.exec_expr(h2o.nodes[0], execExpr, resultKey=None, timeoutSecs=300)
            print "result:", result

        for execExpr in exprList:
            start = time.time()
            execResult, result = h2e.exec_expr(h2o.nodes[0], execExpr, resultKey=None, timeoutSecs=300)
            print 'exec took', time.time() - start, 'seconds'
            print "result:", result
            assert result==1

        h2o.check_sandbox_for_errors()


if __name__ == '__main__':
    h2o.unit_main()

import unittest, random, sys, time
sys.path.extend(['.','..','../..','py'])
import h2o2 as h2o
import h2o_browse as h2b, h2o_exec as h2e, h2o_import as h2i
# '(def anon {x} ( (var %x "null" %FALSE "null");;(var %x "null" %FALSE "null") );;;)',

from copy import copy
from h2o_xl import Assign, If, IfElse, Return, Xbase, Key, Expr
from h2o_test import dump_json

print "Trying a different way, listing Rapids objects, rather than .ast() strings"

# 'c' allowed
# should be able to take a list of statements
exprList = [
    "Expr(If(1,2))",
    "Expr(Return(3))",
    "Expr(Return(r))",
    "Expr(IfElse(1, 2, 3))",
    "Assign('a', If(1, 2) )",
    "Assign('d', IfElse(1, 2, 3) )",
    "Assign('e', If(1, 2) )",
    "Assign('f', IfElse(1, r, 3) )",
    "Assign('h', IfElse(1, 2, Return(r)) )",
    "Assign('i', IfElse(1, Return(r), 3) )",
    "Assign('g', IfElse(r, 2, 3) )",
    "Assign('g', IfElse(r, r, 3) )",
    "Assign('g', IfElse(r, 2, r) )",
]

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

    def test_rapids_ifelse(self):
        bucket = 'smalldata'
        csvPathname = 'iris/iris_wheader.csv'

        hexKey = 'r1'
        parseResult = h2i.import_parse(bucket=bucket, path=csvPathname, schema='put', hex_key=hexKey)

        r = Key('r1')
        keys = []
        for trial in range(2):
            for execExpr in exprList:
                exec(execExpr)
                result = Xbase.lastResult
                execResult = Xbase.lastExecResult
                print dump_json(execResult)
                # rows might be zero!
                if execResult['num_rows'] or execResult['num_cols']:
                    keys.append(execExpr)

        print "\nExpressions that created keys"
        for k in keys:
            print k

        # for execExpr in exprList:
        #     h2e.exec_expr(execExpr=execExpr, resultKey=None, timeoutSecs=10)

        h2o.check_sandbox_for_errors()


if __name__ == '__main__':
    h2o.unit_main()

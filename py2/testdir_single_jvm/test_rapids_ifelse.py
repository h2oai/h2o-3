import unittest, random, sys, time
sys.path.extend(['.','..','../..','py'])
import h2o, h2o_browse as h2b, h2o_exec as h2e, h2o_import as h2i
# '(def anon {x} ( (var $x "null" $FALSE "null");;(var $x "null" $FALSE "null") );;;)',

from h2o_xexec import Def, Fcn, Assign, Frame, If, IfElse, Return

print "Trying a different way, listing Rapids objects, rather than .ast() strings"

# 'c' allowed
# should be able to take a list of statements
objList = [
    If(1,2),
    Return(3),
    Return('r1'),
    IfElse(1, 2, 3),
    Assign('a', If(1, 2)),
    Assign('d', IfElse(1, 2, 3)),
    Assign('e', If(1, 2)), 
    Assign('f', IfElse(1, 'r1', 3)),
    Assign('h', IfElse('1', 2, Return('r1'))),
    Assign('i', IfElse('1', Return('r1'), 3)),
    Assign('g', IfElse('r1', 2, 3)),
    Assign('g', IfElse('r1', 'r1', 3)),
    Assign('g', IfElse('r1', 2, 'r1')),
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

        keys = []
        for trial in range(2):
            for execObj in objList:
                result = execObj.do()
                # rows might be zero!
                if execObj.execResult['num_rows'] or execObj.execResult['num_cols']:
                    keys.append(execObj.execExpr)

        print "\nExpressions that created keys"
        for k in keys:
            print k

        # for execExpr in exprList:
        #     h2e.exec_expr(execExpr=execExpr, resultKey=None, timeoutSecs=10)

        h2o.check_sandbox_for_errors()


if __name__ == '__main__':
    h2o.unit_main()

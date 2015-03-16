import unittest, random, sys, time
sys.path.extend(['.','..','../..','py'])
import h2o2 as h2o
import h2o_browse as h2b, h2o_exec as h2e, h2o_import as h2i
# '(def anon {x} ( (var %x "null" %FALSE "null");;(var %x "null" %FALSE "null") );;;)',

from h2o_xl import Def, Fcn, Assign, KeyIndexed, If, IfElse, Return
from h2o_test import dump_json, verboseprint
from copy import copy

print "Trying a different way, listing Rapids objects, rather than .ast() strings"

# 'c' allowed
# should be able to take a list of statements
objList = [
    Assign('e', IfElse(1, 2, IfElse(4, 5, IfElse(7, 8, 9))), do=False),
    Assign('f', IfElse(1, 2, IfElse(4, 5, IfElse(7, 8, 9))), do=False),
    Assign('g', IfElse(0, 2, IfElse(0, 5, IfElse(0, 8, 9))), do=False),

    Def('ms', 'x', [
        IfElse(0, 2, IfElse(0, 5, IfElse(0, 8, 9))),
        Assign('k', IfElse(0, 12, IfElse(0, 15, IfElse(0, 18, 19))), do=False),
        ] ),
    Assign('e', Fcn('ms', 2), do=False),

    Def('ms', 'x', [
        If(0, Return(3)),
        IfElse(0, 5, IfElse(0, 8, 9)),
        Assign('k', IfElse(0, 12, IfElse(0, 15, IfElse(0, 18, 19))), do=False),
        If(1, Return(2)), 
        ] ),
    Assign('e', Fcn('ms', 2), do=False),
]

resultList = [
    None,
    None,
    None,

    None,
    19,

    None,
    2,
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

    def test_rapids_ifelse_nested(self):
        bucket = 'smalldata'
        csvPathname = 'iris/iris_wheader.csv'

        hexKey = 'r1'
        parseResult = h2i.import_parse(bucket=bucket, path=csvPathname, schema='put', hex_key=hexKey)

        keys = []
        for trial in range(2):
            for execObj, expected in zip(objList, resultList):
                freshObj = copy(execObj)
                result = freshObj.do()
                # do some scalar result checking
                if expected is not None:
                    # result is a string now??
                    print "result:", result
                    print "expected:", expected
                    # assert result==expected, "%s %s" (result,expected)

                # rows might be zero!
                print "freshObj:", dump_json(freshObj.execResult)
                if 'key' in freshObj.execResult and freshObj.execResult['key']:
                    keys.append(freshObj.execExpr)

        print "\nExpressions that created keys"
        for k in keys:
            print k

        # for execExpr in exprList:
        #     h2e.exec_expr(execExpr=execExpr, resultKey=None, timeoutSecs=10)

        h2o.check_sandbox_for_errors()


if __name__ == '__main__':
    h2o.unit_main()

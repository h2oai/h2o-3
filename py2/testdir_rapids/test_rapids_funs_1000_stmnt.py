import unittest, random, sys, time
sys.path.extend(['.','..','../..','py'])
import h2o2 as h2o
import h2o_browse as h2b, h2o_exec as h2e, h2o_import as h2i
# '(def anon {x} ( (var %x "null" %FALSE "null");;(var %x "null" %FALSE "null") );;;)',

from h2o_xl import Def, Fcn, Assign, KeyIndexed
from copy import copy

print "Trying a different way, listing Rapids objects, rather than .ast() strings"

# 'c' allowed
# should be able to take a list of statements
keyString = 'abdefghijklmnopqrstuvzabdefghijklmnopqrstuvz'
keyString += 'abdefghijklmnopqrstuvzabdefghijklmnopqrstuvz'
keyString += 'abdefghijklmnopqrstuvzabdefghijklmnopqrstuvz'
funsList = [
    Def('anon', 'x', 
        [Assign(key, Fcn('var', 'x', None, False, None), do=False) for key in keyString],
        
        [Assign(key, Fcn('sum', KeyIndexed('x',col=0), False), do=False) for key in keyString],
        [Assign(key, Fcn('max', KeyIndexed('x',col=0), False), do=False) for key in keyString],
        [Assign(key, Fcn('min', KeyIndexed('x',col=0), False), do=False) for key in keyString],
        [Assign(key, Fcn('xorsum', KeyIndexed('x',col=0), False), do=False) for key in keyString],

        [Assign(key, Fcn('sd', KeyIndexed('x',col=0), False), do=False) for key in keyString],
        [Assign(key, Fcn('ncol', KeyIndexed('x',col=0)), do=False) for key in keyString],
        [Assign(key, Fcn('is.factor', KeyIndexed('x',col=0)), do=False) for key in keyString],
        [Assign(key, Fcn('any.factor', KeyIndexed('x',col=0)), do=False) for key in keyString],
        [Assign(key, Fcn('length', KeyIndexed('x',col=0)), do=False) for key in keyString],

        [Assign(key, Fcn('sin', KeyIndexed('x',col=0)), do=False) for key in keyString],
        [Assign(key, Fcn('asin', KeyIndexed('x',col=0)), do=False) for key in keyString],
        [Assign(key, Fcn('sinh', KeyIndexed('x',col=0)), do=False) for key in keyString],
        [Assign(key, Fcn('cos', KeyIndexed('x',col=0)), do=False) for key in keyString],
        [Assign(key, Fcn('acos', KeyIndexed('x',col=0)), do=False) for key in keyString],
        [Assign(key, Fcn('tan', KeyIndexed('x',col=0)), do=False) for key in keyString],
        [Assign(key, Fcn('atan', KeyIndexed('x',col=0)), do=False) for key in keyString],
        [Assign(key, Fcn('tanh', KeyIndexed('x',col=0)), do=False) for key in keyString],

        Fcn('var', 'x', None, False, None)
    ),
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

    def test_rapids_funs_1000_stmnt(self):
        DO_FAIL = False
        if DO_FAIL:
            bucket = 'home-0xdiag-datasets'
            csvPathname = 'standard/covtype.data'
        else:
            bucket = 'smalldata'
            csvPathname = 'iris/iris_wheader.csv'

        hexKey = 'r1'
        parseResult = h2i.import_parse(bucket=bucket, path=csvPathname, schema='put', hex_key=hexKey)

        keys = []

        for trial in range(3):
            for execObj in funsList:
                freshObj = copy(execObj)
                print "ast length:", len(str(freshObj))
                result = freshObj.do()

                # rapids doesn't like complicated params right now?
                if DO_FAIL:
                    a = Assign('junk', Fcn('anon', KeyIndexed('r1',col=0)))
                else:
                    a = Assign('junk', Fcn('anon', 'r1'))
                result = a.do(timeoutSecs=60)

                # rows might be zero!
                if a.execResult['num_rows'] or a.execResult['num_cols']:
                    keys.append(a.execExpr)

        print "\nExpressions that created keys"
        for k in keys:
            print k

        # for execExpr in exprList:
        #     h2e.exec_expr(execExpr=execExpr, resultKey=None, timeoutSecs=10)

        h2o.check_sandbox_for_errors()


if __name__ == '__main__':
    h2o.unit_main()

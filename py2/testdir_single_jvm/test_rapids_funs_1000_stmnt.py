import unittest, random, sys, time
sys.path.extend(['.','..','../..','py'])
import h2o, h2o_browse as h2b, h2o_exec as h2e, h2o_import as h2i
# '(def anon {x} ( (var $x "null" $FALSE "null");;(var $x "null" $FALSE "null") );;;)',

from h2o_xexec import Def, Fcn, Assign, Frame

print "Trying a different way, listing Rapids objects, rather than .ast() strings"

# 'c' allowed
# should be able to take a list of statements
keyString = 'abdefghijklmnopqrstuvzabdefghijklmnopqrstuvz'
keyString += 'abdefghijklmnopqrstuvzabdefghijklmnopqrstuvz'
keyString += 'abdefghijklmnopqrstuvzabdefghijklmnopqrstuvz'
funsList = [
    Def('anon', 'x', 
        [Assign(key, Fcn('var', 'x', None, False, None)) for key in keyString],
        
        [Assign(key, Fcn('sum', Frame('x',col=0), False)) for key in keyString],
        [Assign(key, Fcn('max', Frame('x',col=0), False)) for key in keyString],
        [Assign(key, Fcn('min', Frame('x',col=0), False)) for key in keyString],
        [Assign(key, Fcn('xorsum', Frame('x',col=0), False)) for key in keyString],

        [Assign(key, Fcn('sd', Frame('x',col=0), False)) for key in keyString],
        [Assign(key, Fcn('ncol', Frame('x',col=0))) for key in keyString],
        [Assign(key, Fcn('is.factor', Frame('x',col=0))) for key in keyString],
        [Assign(key, Fcn('any.factor', Frame('x',col=0))) for key in keyString],
        [Assign(key, Fcn('length', Frame('x',col=0))) for key in keyString],

        [Assign(key, Fcn('sin', Frame('x',col=0))) for key in keyString],
        [Assign(key, Fcn('asin', Frame('x',col=0))) for key in keyString],
        [Assign(key, Fcn('sinh', Frame('x',col=0))) for key in keyString],
        [Assign(key, Fcn('cos', Frame('x',col=0))) for key in keyString],
        [Assign(key, Fcn('acos', Frame('x',col=0))) for key in keyString],
        [Assign(key, Fcn('tan', Frame('x',col=0))) for key in keyString],
        [Assign(key, Fcn('atan', Frame('x',col=0))) for key in keyString],
        [Assign(key, Fcn('tanh', Frame('x',col=0))) for key in keyString],

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
                print "ast length:", len(execObj.ast())
                result = execObj.do()

                # rapids doesn't like complicated params right now?
                if DO_FAIL:
                    a = Assign('junk', Fcn('anon', Frame('r1',col=0)))
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

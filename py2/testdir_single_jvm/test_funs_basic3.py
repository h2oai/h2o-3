import unittest, random, sys, time
sys.path.extend(['.','..','../..','py'])
import h2o, h2o_browse as h2b, h2o_exec as h2e, h2o_import as h2i

# '(def anon {x} ( (var $x "null" $FALSE "null");;(var $x "null" $FALSE "null") );;;)',

from h2o_xexec import Def, Fcn, Assign

print "Trying a different way, listing Rapids objects, rather than .ast() strings"

# 'c' allowed
funsList = [
    Def('anon', 'x', 
        Assign('a', Fcn('var', 'x', None, False, None)),
        Assign('b', Fcn('var', 'x', None, False, None)),
        Assign('d', Fcn('var', 'x', None, False, None)),
        Assign('e', Fcn('var', 'x', None, False, None)),
        Assign('f', Fcn('var', 'x', None, False, None)),
        Assign('g', Fcn('var', 'x', None, False, None)),
        Assign('d', Fcn('var', 'x', None, False, None)),
        Assign('i', Fcn('var', 'x', None, False, None)),
        Assign('j', Fcn('var', 'x', None, False, None)),
        Assign('k', Fcn('var', 'x', None, False, None)),
        Assign('l', Fcn('var', 'x', None, False, None)),
        Assign('m', Fcn('var', 'x', None, False, None)),
        Assign('n', Fcn('var', 'x', None, False, None)),
        Assign('o', Fcn('var', 'x', None, False, None)),
        Assign('p', Fcn('var', 'x', None, False, None)),
        Assign('q', Fcn('var', 'x', None, False, None)),
        Assign('r', Fcn('var', 'x', None, False, None)),
        Assign('s', Fcn('var', 'x', None, False, None)),
        Assign('t', Fcn('var', 'x', None, False, None)),
        Assign('u', Fcn('var', 'x', None, False, None)),
        Assign('v', Fcn('var', 'x', None, False, None)),
        Assign('w', Fcn('var', 'x', None, False, None)),
        Assign('x', Fcn('var', 'x', None, False, None)),
        Assign('y', Fcn('var', 'x', None, False, None)),
        Assign('z', Fcn('var', 'x', None, False, None)),
        Fcn('var', 'x', None, False, None),
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

    def test_rapids_funs_basic3(self):
        if 1==1:
            bucket = 'smalldata'
            csvPathname = 'iris/iris_wheader.csv'
        else:
            bucket = 'home-0xdiag-datasets'
            csvPathname = 'standard/covtype.data'

        hexKey = 'r1'
        parseResult = h2i.import_parse(bucket=bucket, path=csvPathname, schema='put', hex_key=hexKey)

        keys = []
        for trial in range(5):
            for execObj in funsList:
                result = execObj.do()

                a = Assign('junk', Fcn('anon', 'r1'))
                result = a.do(timeoutSecs=15)

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

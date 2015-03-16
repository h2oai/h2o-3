import unittest, random, sys, time
sys.path.extend(['.','..','../..','py'])
import h2o2 as h2o
import h2o_browse as h2b, h2o_exec as h2e, h2o_import as h2i
# '(def anon {x} ( (var %x "null" %FALSE "null");;(var %x "null" %FALSE "null") );;;)',

from h2o_xl import Def, Fcn, Assign, KeyIndexed
from copy import copy, deepcopy

print "Trying a different way, listing Rapids objects, rather than .ast() strings"

# 'c' allowed
# should be able to take a list of statements
funsList = [
    Def('anon', 'x', 
        Assign('a', Fcn('var', 'x', None, False, None), do=False),
        Assign('b', Fcn('var', 'x', None, False, None), do=False),
        Assign('d', Fcn('var', 'x', None, False, None), do=False),
        Assign('e', Fcn('var', 'x', None, False, None), do=False),
        Assign('f', Fcn('var', 'x', None, False, None), do=False),
        Assign('g', Fcn('var', 'x', None, False, None), do=False),
        Assign('d', Fcn('var', 'x', None, False, None), do=False),
        Assign('i', Fcn('var', 'x', None, False, None), do=False),
        Assign('j', Fcn('var', 'x', None, False, None), do=False),
        Assign('k', Fcn('var', 'x', None, False, None), do=False),
        Assign('l', Fcn('var', 'x', None, False, None), do=False),
        Assign('m', Fcn('var', 'x', None, False, None), do=False),
        Assign('n', Fcn('var', 'x', None, False, None), do=False),
        Assign('o', Fcn('var', 'x', None, False, None), do=False),
        Assign('p', Fcn('var', 'x', None, False, None), do=False),
        Assign('q', Fcn('var', 'x', None, False, None), do=False),
        Assign('r', Fcn('var', 'x', None, False, None), do=False),
        Assign('s', Fcn('var', 'x', None, False, None), do=False),
        Assign('t', Fcn('var', 'x', None, False, None), do=False),
        Assign('u', Fcn('var', 'x', None, False, None), do=False),
        Assign('v', Fcn('var', 'x', None, False, None), do=False),
        Assign('w', Fcn('var', 'x', None, False, None), do=False),
        Assign('x', Fcn('var', 'x', None, False, None), do=False),
        Assign('y', Fcn('var', 'x', None, False, None), do=False),
        Assign('z', Fcn('var', 'x', None, False, None), do=False),
        Fcn('var', 'x', None, False, None),
    ),

    Def('anon', 'x', 
        [Assign(key, Fcn('var', 'x', None, False, None), do=False) for key in 'abdefghijklmnopqrstuvz'],
        [Assign(key, Fcn('sum', KeyIndexed('x',col=0), False), do=False) for key in 'abdefghijklmnopqrstuvz'],
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

        # works for 1 pass..why is execExpr set for 2nd pass? should be new instance?
        # if we reuse the same object in the list, it has state?
        # do we need to copy the object...hmm
        for trial in range(1):
            for execObj in funsList:
                freshObj = copy(execObj)
                result = freshObj.do()
                # rapids doesn't like complicated params right now?
                if DO_FAIL:
                    a = Assign('junk', Fcn('anon', KeyIndexed('r1',col=0)), do=False)
                else:
                    a = Assign('junk', Fcn('anon', 'r1'), do=False)
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

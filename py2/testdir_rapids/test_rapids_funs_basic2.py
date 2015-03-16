import unittest, random, sys, time
sys.path.extend(['.','..','../..','py'])
import h2o2 as h2o
import h2o_browse as h2b, h2o_exec as h2e, h2o_import as h2i

funsList = [
    # bad (KeyIndexed.java: 78 assert)
    # '(def anon {x} (var %x "null" %FALSE "null");;(var %x "null" %FALSE "null");;;)',
    # bad also
    '(def anon {x} ( (var %x "null" %FALSE "null");;(var %x "null" %FALSE "null") );;;)',
    # good
    # '(def anon {x} (var %x "null" %FALSE "null");;;)',
]

funsList2 = [
    # '(def anon {x}  (abs (- (* (* %x %x) %x) (* (/ #999 (var ([ %x (: #0 #19) "null") "null" %FALSE "null")) %x)));;;)',
    # badly formed?
    # '(def anon {x}  ( (var ([ %x (: #0 #19) "null") "null" %FALSE "null") %x));;;)',
    # bad. causes assertion
    # '(def anon {x}  ( (var ([ %x (: #0 #19) "null") "null" %FALSE "null") %x);;;)',

    # bad?
    '(def anon {x}  (var ([ %x (: #0 #19) "null") "null" %FALSE "null");;;)',
    # does it need the extra paren? for the function def?
    '(def anon {x}  ( (var ([ %x (: #0 #19) "null") "null" %FALSE "null") );;;)',
    '(def anon {x}  ((var ([ %x (: #0 #19) "null") "null" %FALSE "null"));;;)',

    # '(def anon {x} (var %x "null" %FALSE "null");;;)',
    # '(def anon {x} (var %x "null" %FALSE "null");;(var %x "null" %FALSE "null");;;)',
    # '(def anon {x} (var %x "null" %FALSE "null"))',

    # '(def anon {x}  (var ([ %x (: #0 #19) "null") "null" %FALSE "null");;;)',
    # '(def anon {x}  (var ([ %x (: #0 #19) "null") "null" %FALSE "null") ;;;)',
    # what if you don't have ;;;
    # '(def anon {x} ( (var %x "null" %FALSE "null");;;) )',
    # '(def anon {x} ( ( (var %x "null" %FALSE "null");;;) ))',
    # '(def anon {x} (var %x "null" %FALSE "null") ;;;)',
    # '(def anon {x} ( (var %x "null" %FALSE "null") ;;; ) )',
    # '(def anon {x} ( ( (var %x "null" %FALSE "null") ;;; ) ))',
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

    def test_rapids_funs_basic2(self):
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
            for execExpr in funsList:
                funs = '[%s]' % execExpr
                execResult, result = h2e.exec_expr(h2o.nodes[0], funs, doFuns=True, resultKey=None, 
                    timeoutSecs=4)
                execExpr2 = '(= !junk (apply %r1 #2 %anon))' 
                execResult, result = h2e.exec_expr(h2o.nodes[0], execExpr2, doFuns=False, resultKey=None, 
                    timeoutSecs=15)
                # rows might be zero!
                if execResult['num_rows'] or execResult['num_cols']:
                    keys.append(execExpr2)

        print "\nExpressions that created keys"
        for k in keys:
            print k

        # for execExpr in exprList:
        #     h2e.exec_expr(execExpr=execExpr, resultKey=None, timeoutSecs=10)

        h2o.check_sandbox_for_errors()


if __name__ == '__main__':
    h2o.unit_main()

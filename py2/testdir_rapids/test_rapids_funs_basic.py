import unittest, random, sys, time
sys.path.extend(['.','..','../..','py'])
import h2o2 as h2o
import h2o_browse as h2b, h2o_exec as h2e, h2o_import as h2i

funsList = [
    '(def anon {x}  (, ((- x x ) )))',

    '(def anon {x}  (, ((- (abs (- (* %x %x) (* (* %x #5) %x))) )) ))',



    '(def anon {x}  (, ((- (abs (- (* %x %x) (* (* %x #5) %x))) (/ #55 %x))) ) ((abs (- (* (* %x %x) %x) (* (/ #999 (var ([ %x (: #0 #19) "null") "null" %FALSE "null")) %x))) ))',

    '(def anon {x} (- (abs (- (* %x %x) (* (* %x #5) %x))) (/ #55 %x));;  (abs (- (* (* %x %x) %x) (* (/ #999 (var ([ %x (: #0 #19) "null") "null" %FALSE "null")) %x)));;;)',

    '(def anon {x}(- (abs (- (* %x %x) (* (* %x #5) %x))) (/ #55 %x));;  (abs (- (* (* %x %x) %x) (* (/ #999 (var ([ %x (: #0 #19) "null") "null" %FALSE "null")) %x)));;;)',

# need space after function name

    '(def anon {x}(- (abs (- (* %x %x) (* (* %x #5) %x))) (/ #55 %x));;  (abs (- (* (* %x %x) %x) (* (/ #999 (var ([ %x (: #0 #19) "null") "null" %FALSE "null")) %x)));;;)',

    '(def anon {x}(- (abs (- (* %x %x) (* (* %x #5) %x))) (/ #55 %x));; (abs (- (* (* %x %x) %x) (* (/ #999 (var ([ %x (: #0 #19) "null") "null" %FALSE "null")) %x)));;;)',

    '(def anon {x}(- (abs (- (* %x %x) (* (* %x #5) %x))) (/ #55 %x));;(abs (- (* (* %x %x) %x) (* (/ #999 (var ([ %x (: #0 #19) "null") "null" %FALSE "null")) %x))) ;;;)',

    '(def anon {x}(- (abs (- (* %x %x) (* (* %x #5) %x))) (/ #55 %x));;(abs (- (* (* %x %x) %x) (* (/ #999 (var ([ %x (: #0 #19) "null") "null" %FALSE "null")) %x))) ;;; )',
]
# https://github.com/0xdata/h2o-dev/blob/master/h2o-r/tests/testdir_munging/exec/runit_apply.R
# Specifically this line is interesting since it has >1 stmnt:
# apply(hex, 2, function(x) { abs( x*x - x*5*x ) - 55/x; abs(x*x*x - 999/var(x[1:20,])*x ) })

# I do still do a post function, but no need for user to deal with that. Here's the AST for the function:
# funs=[(def anon {x}  (- (abs (- (* %x %x) (* (* %x #5) %x))) (/ #55 %x));;  (abs (- (* (* %x %x) %x) (* (/ #999 (var ([ %x (: #0 #19) "null") "null" %FALSE "null")) %x)));;;)]

# And then the apply comes next:
# (apply %prostate.hex #2 %anon) 

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

    def test_rapids_funs_basic(self):
        bucket = 'smalldata'
        csvPathname = 'iris/iris_wheader.csv'
        hexKey = 'r1'
        parseResult = h2i.import_parse(bucket=bucket, path=csvPathname, schema='put', hex_key=hexKey)

        keys = []
        for trial in range(100):
            for execExpr in funsList:
                funs = '[%s]' % execExpr
                execResult, result = h2e.exec_expr(h2o.nodes[0], funs, doFuns=True, resultKey=None, timeoutSecs=4)
                execExpr2 = '(apply %r1 #2 %anon)' 
                execResult, result = h2e.exec_expr(h2o.nodes[0], execExpr2, doFuns=False, resultKey=None, timeoutSecs=4)
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

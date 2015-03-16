import unittest, random, sys, time
sys.path.extend(['.','..','../..','py'])
import h2o2 as h2o
import h2o_browse as h2b, h2o_exec as h2e, h2o_import as h2i

initList = [
        '(#(1))', # why isn't this illegal?

        '(#1)',
        '((#1))',
        '(((#1)))',

        '(= !x #1)',
        '((= !x #1))',
        '(((= !x #1)))',

        # complains
        # '(= !x (#1 #2))',
        # '((= !x (#1 #2)))',
        # '(((= !x (#1 #2))))',

        # okay. not okay if comma separated. seems wrong
        '(= !x (+ #1 #2))',
        '((= !x (+ #1 #2)))',
        '(((= !x (+ #1 #2))))',

        # complains
        # '(= !x (+ #1 #2 #4))',
        # '((= !x (+ #1 #2 #4)))',
        # '(((= !x (+ #1 #2 #4))))',

        # okay. 
        '(= !x + #1 #2)',
        '((= !x + #1 #2))',
        '(((= !x + #1 #2)))',

        # '(= x + #1 #2)', # fails

        # parens on binary operators
        '(= !x + #1 + #1 (+ #1 #1))',
        '= !x + #1 + #1 (+ #1 #1)',
        '= !x N #1 N #1 (N #1 #1)',
        '= !x n #1 n #1 (n #1 #1)',
        '= !x L #1 L #1 (L #1 #1)',
        '= !x l #1 l #1 (l #1 #1)',
        '= !x G #1 G #1 (G #1 #1)',
        '= !x g #1 g #1 (g #1 #1)',

        '= !x (* (* #1 #1) (* #1 #1))',

        '= !x * #1 * #1 (* #1 #1)',
        '= !x - #1 - #1 (- #1 #1)',
        '= !x ^ #1 ^ #1 (^ #1 #1)',
        '= !x / #1 / #1 (/ #1 #1)',
        '= !x ** #1 ** #1 (** #1 #1)',
        '= !x % #1 % #1 (% #1 #1)',
        # '= !x %/% #1 %/% #1 %/% #1 #1', # unimplemented
        # '= !x %% #1 %% #1 %% #1 #1', # unimplemented

        # '(= !x + _#1 + _#1 + _#1 _#1)', # unimplemented
        '= !x _ + #1 + #1 (+ #1 _ #1)',
        '= !x _ N #1 N #1 (N #1 _ #1)',
        '= !x _ n #1 n #1 (n #1 _ #1)',
        '= !x _ L #1 L #1 (L #1 _ #1)',
        '= !x _ l #1 l #1 (l #1 _ #1)',
        '= !x _ G #1 G #1 (G #1 _ #1)',
        '= !x _ g #1 g #1 (g #1 _ #1)',

        '= !x _ * #1 * #1 (* #1 _ #1)',
        '= !x _ - #1 - #1 (- #1 _ #1)',
        '= !x _ ^ #1 ^ #1 (^ #1 _ #1)',
        '= !x _ / #1 / #1 (/ #1 _ #1)',
        '= !x _ ** #1 ** #1 (** #1 _ #1)',
        '= !x _ % #1 % #1 (% #1 _ #1)',


        # can have space between ( and function
        '= !x1 ( sum ([ %r1 "null" #0) %TRUE)',
        '= !x2 ( sum ([ %r1 "null" #0) %TRUE)',
        '= !x2a ( sum ([ %r1 "null" #0) %TRUE )',

        # can have space after (
        '= !x3 ( sum ([ %r1 "null" #0) %TRUE )',
        '= !x3a ( sum ([ %r1 "null" #0) %TRUE )',
        '= !x3b ( sum ([ %r1 "null" #0 ) %TRUE )',
        '= !x4 ( sum ([ %r1 " null " #0 ) %TRUE )',

        # can have space after (
        '(= !x3 ( sum ([ %r1 "null" #0) %TRUE ))',
        '(= !x3a ( sum ([ %r1 "null" #0) %TRUE ) )',
        '(= !x3b ( sum ([ %r1 "null" #0 ) %TRUE )  )',
        '((= !x4 ( sum ([ %r1 " null " #0 ) %TRUE )))',

        '(= !x3 ( max ([ %r1 "null" #0) %TRUE ))',
        '(= !x3a ( max ([ %r1 "null" #0) %TRUE ) )',
        '(= !x3b ( max ([ %r1 "null" #0 ) %TRUE )  )',
        '((= !x4 ( max ([ %r1 " null " #0 ) %TRUE )))',

        '(= !x3 ( min ([ %r1 "null" #0) %TRUE ))',
        '(= !x3a ( min ([ %r1 "null" #0) %TRUE ) )',
        '(= !x3b ( min ([ %r1 "null" #0 ) %TRUE )  )',
        '((= !x4 ( min ([ %r1 " null " #0 ) %TRUE )))',

        '(= !v (c {#1;#4567;(: #91234 #9000209);(: #9000210 #45001045);45001085})',

        '(= !x3 ( min ([ %r1 "null" #0) %TRUE ))',

        '(= !x3 (+ (sum ([ %r1 "null" #0) %TRUE) (sum ([ %r1 "null" #0) %TRUE) )',
        '(= !x3 (+ (xorsum ([ %r1 "null" #0) %TRUE) (xorsum ([ %r1 "null" #0) %TRUE) )',
        '(= !x3 (+ (max ([ %r1 "null" #0) %TRUE) (max ([ %r1 "null" #0) %TRUE) )',
        '(= !x3 (+ (min ([ %r1 "null" #0) %TRUE) (min ([ %r1 "null" #0) %TRUE) )',

        # '{ #1 #1 }',
        # '(= !x4 { #1 #1 })',

        #  r1[c(1,5,8,10,33),]  
        # commas are illegal (var name?)

        # vectors can be strings or numbers only, not vars or keys
        # h2o objects can't be in a vector

        # should work soon
        # '(= !x (c {#1;#5;#8;#10;#33}))',
        # '(= !x (c {(: #0 #5) }))',
        # '(= !x (c {(: #5 #5) }))',
        # '(= !x (c {(: #5 #0) }))',
        # space after : should be optional

        # this doesn't work
        # '(= !v (c { #1;#4567;(: #91234 #9000209);(: #9000210 #45001045);45001085 })',

        # c(1,2,3,4)

        # '= !x (sum %r1 )'
        # '(= !x (xorsum ([ %r1 "null" #0) %TRUE))', # works

        
        # 'cave=c(1.3,0,1,2,3,4,5)',
        # 'ma=c(2.3,0,1,2,3,4,5)',
        # 'r2.hex=c(3.3,0,1,2,3,4,5)',
        # 'r3.hex=c(4.3,0,1,2,3,4,5)',
        # 'r4.hex=c(5.3,0,1,2,3,4,5)',
        # 'r.hex=i.hex',
        ]

# single operand stuff
exprList = [
        '(= !x (cos ([ %r1 "null" #0) ))',
        '(= !x (sin ([ %r1 "null" #0) ))',
        '(= !x (tan ([ %r1 "null" #0) ))',
        '(= !x (acos ([ %r1 "null" #0) ))',
        '(= !x (asin ([ %r1 "null" #0) ))',
        '(= !x (atan ([ %r1 "null" #0) ))',
        '(= !x (cosh ([ %r1 "null" #0) ))',
        '(= !x (sinh ([ %r1 "null" #0) ))',
        '(= !x (tanh ([ %r1 "null" #0) ))',
        '(= !x (abs ([ %r1 "null" #0) ))',
        '(= !x (sign ([ %r1 "null" #0) ))',
        '(= !x (sqrt ([ %r1 "null" #0) ))',
        '(= !x (log ([ %r1 "null" #0) ))',
        '(= !x (exp ([ %r1 "null" #0) ))',
        '(= !x (is.na ([ %r1 "null" #0) ))',

        # FIX! these don't work in h2o-dev?
        # '(= !x (ceil ([ %r1 "null" #0) ))',
        # '(= !x (floor ([ %r1 "null" #0) ))',

        '(= !x (length ([ %r1 "null" #0) ))',
        # '(= !x (scale ([ %r1 "null" #0) ))',
        # '(= !x (table ([ %r1 "null" #0) ))',
        # '(= !x (unique ([ %r1 "null" #0) ))',
        # '(= !x (factor ([ %r1 "null" #0) ))',
        # '(= !x (nrow ([ %r1 "null" #0) ))',
        # '(= !x (sd ([ %r1 "null" #0) ))',
        # '(= !x (ncol ([ %r1 "null" #0) ))',
        '(= !x (is.factor ([ %r1 "null" #0) ))',
        '(= !x (any.factor ([ %r1 "null" #0) ))',
        '(= !x (any.na ([ %r1 "null" #0) ))',
        # '(= !x (isTrue ([ %r1 "null" #0) ))',
        # '(= !x (head ([ %r1 "null" #0) ))',
        # '(= !x (tail ([ %r1 "null" #0) ))',

        # 1 operand
        # '(= !x (seq_len #0.1))',
        # 2 operands
        '(= !x (round ([ %r1 "null" #0) #1))',
        # '(= !x (trunc ([ %r1 "null" #0) #1))',
        # '(= !x (signif ([ %r1 "null" #0) #1))',

        # FIX! gets AIOOBE
        # '(= !x (cut ([ %r1 "null" #0) #2))',
        # '(= !x (rep_len #0.1 #10))',
]


class Basic(unittest.TestCase):
    def tearDown(self):
        h2o.check_sandbox_for_errors()

    @classmethod
    def setUpClass(cls):
        global SEED
        SEED = h2o.setup_random_seed()
        h2o.init(1)

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()

    def test_rapids_builtin(self):
        bucket = 'smalldata'
        csvPathname = 'iris/iris_wheader.csv'
        hexKey = 'r1'
        parseResult = h2i.import_parse(bucket=bucket, path=csvPathname, schema='put', hex_key=hexKey)
        
        bad = []
        for execExpr in exprList:
            try:
                h2e.exec_expr(h2o.nodes[0], execExpr, resultKey=None, timeoutSecs=4)
            except:
                # assert 1==0
                bad.append(execExpr)

        print "\nbad:"
        for b in bad:
            print b

        # for execExpr in exprList:
        #     h2e.exec_expr(execExpr=execExpr, resultKey=None, timeoutSecs=10)

        # h2o.check_sandbox_for_errors()


if __name__ == '__main__':
    h2o.unit_main()

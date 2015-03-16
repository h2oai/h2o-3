import unittest, random, sys, time
sys.path.extend(['.','..','../..','py'])
import h2o2 as h2o
import h2o_browse as h2b, h2o_exec as h2e, h2o_import as h2i


# ! is only needed if there is no indexing
initList = [
        # weird cases with lhs 
        # can only index v if it already exists. is the lhs addition before or after the indexed assign
        # if before, what does it add to. If after, does it add to the full key?
        # must be before.
        '(= ([ (+ #5 ([ %v "null" "null"))  {#0;#1;#2;#3;#4} #0) ([ %v (: #4 #8) #0))',
        '(= ([ (+ #5 ([ %v "null" #0))  {#0;#1;#2;#3;#4} #0) ([ %v (: #4 #8) #0))',
        # wrong row count?
        # '(= ([ (+ #5 ([ %v #0 "null"))  {#0;#1;#2;#3;#4} #0) ([ %v (: #4 #8) #0))',
        # fails with exception
        # '(= ([ (+ #5 ([ %v #0 #0))  {#0;#1;#2;#3;#4} #0) ([ %v (: #4 #8) #0))',

        # weird cases with lhs
        # dont need a rhs but should still modify?  
        # does this add 5 to v and then nothing?

        # why does this fail?
        # '([ (+ #5 ([ %v "null" "null")) #0)',
        # why does this fail?
        # '([ (+ #6 ([ %v "null" #0)) #0)',
        # '([ (+ #7 ([ %v #0)) #0))',

        '(not (* #2 #2))',
        '(= !a (not (* #2 #2)))',
        '(+ (* #2 #2) (* #5 #5))',
        '(* #1 (+ (* #2 #2) (* #5 #5)))',

        '(= !x (c {#1;#5;#8;#10;#33}))',
        '(= !x (c {(: #0 #5) }))',
        '(= !x (c {(: #5 #5) }))',

        # why is num_rows = -4 here? Will blow up if we  use it?
        # '(= !x (c {(: #5 #0) }))',

        '(= !v (c {#1;#4567;(: #9 #90);(: #9 #45);#450})',
        '(= !v2 (+ %v %v))',

        # FIX! test with space after { and before }
        '(= !v (c {#1;#4567;(: #91234 #9000209);(: #9000210 #45001045);#45001085})',
        '(= !v (c {#1;#4567;(: #91234 #9000209);(: #9000210 #45001045);45001085})',

        # remember need %v to reference
        '(= !v (c {#1;#4567;(: #9 #90);(: #9 #45);#450})',
        '(= !v2 %v )',

        '(= !v2 (n %v %v))',
        '(= !v2 (N %v %v))',

        '(= !v2 (- %v %v))',
        '(= !v2 (+ %v %v))',

        '(= !v2 (sum (+ %v %v) %TRUE)',
        '(= !v2 (+ #1.0 (sum (+ %v %v) %TRUE))',

        # different dimensions?
        '(= !v3 (+ %v (sum (+ %v %v) %TRUE))',
        '(= !v3 (cbind %v %v %v %v))',
        # '(= !v3 (rbind %v %v %v %v))',

        # '(= !keys (ls))', # works
        # '(= !x #1)', # works
        # '(= !x (sum ([ %v "null" #0) %TRUE))', # works
        # '(= !x (sum ([ v "null" (: #0 #0)) %TRUE))', # bad v

        # '(= !x (xorsum ([ %v "null" #0) %TRUE))', # works

        # 'a',  # AAIOBE
        # 'x', # AAIOBE
        # 'c', # AAIOBE
        # 'c(1)', # says 'c(1' is unimplemented

        # '(= #1)', # AAIOBE
        # '(= !x #1)', # works
        # 'x=c(1.3,0,1,2,3,4,5)', # says 'x=c(1.3,0,1,2,3,4,5' is unimplemented
        # 'x=c(1.3',  # AAIOBE
        # '()',  # Unimplemented on token ''
        # '(x)', # unimplemented on x
        # '(= !x)', # AAIOBE
        # '(= !x ())', # unimplemented
        # '(= !x #1)', # works
        # '(= !x #1 #2)',  # works, answer is 1?
        # '(= !x (cbind (#1 #2) %TRUE))',  # ClassCast exception
        # '(= !x (cbind (#1 #2)))',  # ClassCast exception
        # '(= !x (cbind (#1)))',  # ClassCast exception
        # '(= !x (cbind #1))',  # ClassCast exception
        # '(= !x (seq (#1, #2)) )',  # number format exception
        # '(= !x (seq (#1, #2)) )',  # bad
        # '(= !x (seq #1, #2) )',  # bad

        # '(= !x (seq (#1) )',  # bad
        # '(= !x #1; = !x #2)', # no error but why answer is 1?
        # '(= !x #1) (=!x #2)', # no error but why answer is 1?
        # '{(= !x #1); (=!y %x)', # AAIOBE
        # '{(= !x #1)', # AAIOBE
        # '({(= !x #1); (= !y #1))', # AAIOBE
        # '(1)',
        # '((1))',
        # '(((1)))',

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
        # FIX! what is modulo now
        # '= !x % #1 % #1 (% #1 #1)',

        # '= !x %/% #1 %/% #1 %/% #1 #1', # unimplemented
        # '= !x %% #1 %% #1 %% #1 #1', # unimplemented

        # '(= !x + _#1 + _#1 + _#1 _#1)', # unimplemented
        # '= !x _ + #1 + #1 (+ #1 _ #1)',
        # '= !x _ N #1 N #1 (N #1 _ #1)',
        # '= !x _ n #1 n #1 (n #1 _ #1)',
        # '= !x _ L #1 L #1 (L #1 _ #1)',
        # '= !x _ l #1 l #1 (l #1 _ #1)',
        # '= !x _ G #1 G #1 (G #1 _ #1)',
        # '= !x _ g #1 g #1 (g #1 _ #1)',

        # '= !x _ * #1 * #1 (* #1 _ #1)',
        # '= !x _ - #1 - #1 (- #1 _ #1)',
        # '= !x _ ^ #1 ^ #1 (^ #1 _ #1)',
        # '= !x _ / #1 / #1 (/ #1 _ #1)',
        # '= !x _ ** #1 ** #1 (** #1 _ #1)',
        # '= !x _ % #1 % #1 (% #1 _ #1)',


        # can have space between ( and function
        '= !x1 ( sum ([ %v "null" #0) %TRUE)',
        '= !x2 ( sum ([ %v "null" #0) %TRUE)',
        '= !x2a ( sum ([ %v "null" #0) %TRUE )',

        # can have space after (
        '= !x3 ( sum ([ %v "null" #0) %TRUE )',
        '= !x3a ( sum ([ %v "null" #0) %TRUE )',
        '= !x3b ( sum ([ %v "null" #0 ) %TRUE )',
        '= !x4 ( sum ([ %v " null " #0 ) %TRUE )',

        # can have space after (
        '(= !x3 ( sum ([ %v "null" #0) %TRUE ))',
        '(= !x3a ( sum ([ %v "null" #0) %TRUE ) )',
        '(= !x3b ( sum ([ %v "null" #0 ) %TRUE )  )',
        '((= !x4 ( sum ([ %v " null " #0 ) %TRUE )))',

        '(= !x3 ( max ([ %v "null" #0) %TRUE ))',
        '(= !x3a ( max ([ %v "null" #0) %TRUE ) )',
        '(= !x3b ( max ([ %v "null" #0 ) %TRUE )  )',
        '((= !x4 ( max ([ %v " null " #0 ) %TRUE )))',

        '(= !x3 ( min ([ %v "null" #0) %TRUE ))',
        '(= !x3a ( min ([ %v "null" #0) %TRUE ) )',
        '(= !x3b ( min ([ %v "null" #0 ) %TRUE )  )',
        '((= !x4 ( min ([ %v " null " #0 ) %TRUE )))',


        '(= !x3 ( min ([ %v "null" #0) %TRUE ))',

        '(= !x3 (+ (sum ([ %v "null" #0) %TRUE) (sum ([ %v "null" #0) %TRUE) )',
        '(= !x3 (+ (xorsum ([ %v "null" #0) %TRUE) (xorsum ([ %v "null" #0) %TRUE) )',

        # FIX! these should be like sum
        # '(= !x3 (+ (max ([ %v "null" #0) %TRUE) (max ([ %v "null" #0) %TRUE) )',
        # '(= !x3 (+ (min ([ %v "null" #0) %TRUE) (min ([ %v "null" #0) %TRUE) )',

        # '{ #1 #1 }',
        # '(= !x4 { #1 #1 })',

        #  v[c(1,5,8,10,33),]  
        # commas are illegal (var name?)

        # vectors can be strings or numbers only, not vars or keys
        # h2o objects can't be in a vector


        # c(1,2,3,4)

        # '= !x (sum %v )'
        # '(= !x (xorsum ([ %v "null" #0) %TRUE))', # works

        
        # 'cave=c(1.3,0,1,2,3,4,5)',
        # 'ma=c(2.3,0,1,2,3,4,5)',
        # 'r2.hex=c(3.3,0,1,2,3,4,5)',
        # 'r3.hex=c(4.3,0,1,2,3,4,5)',
        # 'r4.hex=c(5.3,0,1,2,3,4,5)',
        # 'r.hex=i.hex',
        ]

exprList = [
    "round(r.hex[,1],0)",
    "round(r.hex[,1],1)",
    "round(r.hex[,1],2)",
    # "signif(r.hex[,1],-1)",
    # "signif(r.hex[,1],0)",
    "signif(r.hex[,1],1)",
    "signif(r.hex[,1],2)",
    "signif(r.hex[,1],22)",
    "trunc(r.hex[,1])",
    "trunc(r.hex[,1])",
    "trunc(r.hex[,1])",
    "trunc(r.hex[,1])",

    ## Compute row and column sums for a matrix:
    # 'x <- cbind(x1 = 3, x2 = c(4:1, 2:5))',

    # 'dimnames(x)[[1]] <- letters[1:8]',
    # 'apply(x, 2, mean, trim = .2)',
    'apply(x, 2, mean)',
    'col.sums <- apply(x, 2, sum)',
    'row.sums <- apply(x, 1, sum)',
    # 'rbind(cbind(x, Rtot = row.sums), Ctot = c(col.sums, sum(col.sums)))',
    # 'stopifnot( apply(x, 2, is.vector))',
    ## Sort the columns of a matrix
    # 'apply(x, 2, sort)',
    ##- function with extra args:
    # 'cave <- function(x, c1, c2) c(mean(x[c1]), mean(x[c2]))',
    # 'apply(x, 1, cave,  c1 = "x1", c2 = c("x1","x2"))',
    # 'ma <- matrix(c(1:4, 1, 6:8), nrow = 2)',
    'ma',
    # fails unimplemented
    # 'apply(ma, 1, table)',  #--> a list of length 2
    # 'apply(ma, 1, stats::quantile)', # 5 x n matrix with rownames
    #'stopifnot(dim(ma) == dim(apply(ma, 1:2, sum)))',
    ## Example with different lengths for each call
    # 'z <- array(1:24, dim = 2:4)',
    # 'zseq <- apply(z, 1:2, function(x) seq_len(max(x)))',
    # 'zseq',        ## a 2 x 3 matrix
    # 'typeof(zseq)', ## list
    # 'dim(zseq)', ## 2 3
    # zseq[1,]',
    # 'apply(z, 3, function(x) seq_len(max(x)))',
    # a list without a dim attribute
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

    def test_rapids_basic(self):
        bucket = 'smalldata'
        csvPathname = 'iris/iris_wheader.csv'
        hexKey = 'v'
        parseResult = h2i.import_parse(bucket=bucket, path=csvPathname, schema='put', hex_key=hexKey)

        keys = []
        for execExpr in initList:
            execResult, result = h2e.exec_expr(h2o.nodes[0], execExpr, resultKey=None, timeoutSecs=4)
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

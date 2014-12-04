import unittest, random, sys, time, re
sys.path.extend(['.','..','../..','py'])
import h2o, h2o_browse as h2b, h2o_exec as h2e, h2o_import as h2i

exprList = [
        '(+ (* #2 #2) (* #5 #5))',
        '(* #1 (+ (* #2 #2) (* #5 #5)))',
        '(c {#1;#5;#8;#10;#33})',
        '(c {(: #0 #5) })',
        '(c {(: #5 #5) })',
        '(c {#1;#4567;(: #9 #90);(: #9 #45);#450}',
        '(+ $v $v)',

        # FIX! test with space after { and before }
        '(c {#1;#4567;(: #1234 #9000);(: #900 #4500);#4501}',
        '(c {#1;#4567;(: #1234 #9000);(: #900 #4500);4501}',

        # remember need $v to reference
        '(c {#1;#4567;(: #9 #90);(: #9 #45);#450}',
        '$v ',

        '(n $v $v)',
        '(N $v $v)',

        '(- $v $v)',
        '(+ $v $v)',

        '(sum (+ $v $v) $TRUE)',
        '(+ #1.0 (sum (+ $v $v) $TRUE))',

        # different dimensions?
        '(+ $v (sum (+ $v $v) $TRUE))',
        '(cbind $v $v $v $v)',
        '(#(1))', # why isn't this illegal?

        '#1',
        '(#1)',
        '((#1))',
        '(((#1)))',

        # okay. not okay if comma separated. seems wrong
        '(+ #1 #2)',

        # parens on binary operators
        '(+ #1 (+ #1 #1)))',
        '(N #1 (N #1 #1)))',
        '(n #1 (n #1 #1)))',
        '(L #1 (L #1 #1)))',
        '(l #1 (l #1 #1)))',
        '(G #1 (G #1 #1)))',
        '(g #1 (g #1 #1)))',
        '(+ #1 (+ #1 #1)))',
        '(* #1 (* #1 #1)))',
        '(- #1 (- #1 #1)))',
        '(^ #1 (^ #1 #1)))',
        '(/ #1 (/ #1 #1)))',
        '(% #1 (% #1 #1)))',
        '(** #1 (** #1 #1)))',

        # can have space between ( and function
        '( sum ([ $v "null" #0) $TRUE)',
        '( sum ([ $v "null" #0) $TRUE)',
        '( sum ([ $v "null" #0) $TRUE )',

        # can have space after (
        '( sum ([ $v "null" #0) $TRUE )',
        '( sum ([ $v "null" #0) $TRUE )',
        '( sum ([ $v "null" #0 ) $TRUE )',
        '( sum ([ $v " null " #0 ) $TRUE )',

        # can have space after (
        '( sum ([ $v "null" #0) $TRUE )',
        '( sum ([ $v "null" #0) $TRUE ) ',
        '( sum ([ $v "null" #0 ) $TRUE )  ',
        '( sum ([ $v " null " #0 ) $TRUE ))',

        '( max ([ $v "null" #0) $TRUE )',
        '( max ([ $v "null" #0) $TRUE )',
        '( max ([ $v "null" #0 ) $TRUE )',
        '( max ([ $v " null " #0 ) $TRUE )',

        '( min ([ $v "null" #0) $TRUE )',
        '( min ([ $v "null" #0) $TRUE )',
        '( min ([ $v "null" #0 ) $TRUE )',
        '( min ([ $v " null " #0 ) $TRUE )',

        '( min ([ $v "null" #0) $TRUE )',

        # two expressions in one ast is not legal
        '((xorsum ([ $v "null" #0) $TRUE))',
        '((max ([ $v "null" #0) $TRUE))',
        '(+ (c {#1}) (sum ([ $v "null" #0) $TRUE))',
        '(+ (c {#1}) (min ([ $v "null" #0) $TRUE))',
        # java type 6 exception on the seq without (c ..)
        # '(+ {#1}) (sum ([ $v "null" #0) $TRUE)',
        # '(+ {#1}) (min ([ $v "null" #0) $TRUE)',

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

    def test_rapids_colons_basic(self):
        bucket = 'smalldata'
        csvPathname = 'iris/iris_wheader.csv'
        hexKey = 'v'
        parseResult = h2i.import_parse(bucket=bucket, path=csvPathname, schema='put', hex_key=hexKey)

        def doAll(case):
            keys = []
            trial = 0
            for execExpr in exprList:
                # 4x4 cases per expression
                colons = [
                    '#0 #0',
                    '"null" #0',
                    '#0 "null"',
                    '"null" "null"',
                ]
                for colon in colons:
                    # what if the destination doesn't exist?. Use unique name for each, to see
                    t = "t%s" % trial
                    cases = [
                        # no colon 
                        '(= !%s %s' % (t, execExpr),
                        # colon rhs
                        '(= ([ !%s %s) %s)' % (t, colon, execExpr),
                        # colon lhs
                        '(= !%s  ([ t%s %s))' % (t, execExpr, colon),
                        # colon lhs and rhs
                        '(= ([ !%s %s) ([ %s %s))' % (t, colon, execExpr, colon),
                    ]

                    for case in cases:
                        # colonize it, to see if it blows up!
                        # since they all are assigns, they all are wrapped by '(= !<lhs> ...)
                        # unwrap the inner and wrap it with a colon then wrap it with the assign
                        # change the lhs to be coloned (row and/or col) and change the rhs to be a colon
                        # so four cases
                        execResult, result = h2e.exec_expr(h2o.nodes[0], case, resultKey=None, timeoutSecs=4)
                        # rows/cols could be zero
                        # if execResult['num_rows'] or execResult['num_cols']:
                        # I think if key is not null, then that means a key got created
                        # oh, but exec deletes ones with leading "_" immediately? those are temp keys
                        # we'll put them in the list and see if we see them
                        if execResult['key']:
                            keys.append(execExpr)
                        trial += 1


                print "\nExpressions that created keys"
                for k in keys:
                    print k
                    if re.match('_', k):
                        raise Exception("%s I didn't expect any keys with leading underscores." +
                            "\nDoesn't spencer delete those so I can't read them?" % k)

                h2o.check_sandbox_for_errors()

        for case in range(4):
            doAll(case)


if __name__ == '__main__':
    h2o.unit_main()

import unittest, random, sys, time
sys.path.extend(['.','..','../..','py'])
import h2o2 as h2o
import h2o_cmd, h2o_import as h2i, h2o_xl, h2o_print as h2p
from h2o_xl import DF, Xbase, Key, KeyIndexed, Assign, Fcn, Expr
# just for test stuff
from h2o_xl import checkAst, astForInit
from h2o_test import dump_json, verboseprint
import re

def checkAst(expected):
    ast = h2o_xl.Xbase.lastExecResult['ast']
    ast = re.sub('knon_0x[0-9a-fA-F]+', 'knon_0x...', ast)
    expected  = re.sub('knon_0x[0-9a-fA-F]+', 'knon_0x...', expected)
    # remove the id suffix for knon_ created keys, before comparing
    # knon_0x1a34250
    assert ast==expected, 'Actual: "%s"    Expected: "%s"' % (ast, expected)
    print "----------------------------------------------------------------\n"

# we init to 1 row/col. (-1) can't figure how how to init to no rows in a single expression
def astForInit(frame):
     return '(= !%s (c {#-1}))' % frame

print "Going to see if different xl coding styles yield same ast strings"
class Basic(unittest.TestCase):
    def tearDown(self):
        h2o.check_sandbox_for_errors()

    @classmethod
    def setUpClass(cls):
        global SEED
        SEED = h2o.setup_random_seed()
        h2o.init()

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()

    def test_xl_ast_assert_ZZ(self):
        #*****************************************
        a = DF('a1') # inits to -1
        checkAst(astForInit(a))
        # I suppose use of the h2o inspect request is deprecated
        # h2o_cmd.runInspect uses Frames?
        if 1==0:
            inspect = h2o.n0.inspect(key=a) # str(a) becomes 'a1'. so this param should take type Key for key=
            print "a/a1:", dump_json(inspect)

        # let's use runSummary for fun..returns OutputObj for the col
        # will get from column 0, since column not specified
        summaryResult = h2o_cmd.runSummary(key=a)
        co = h2o_cmd.infoFromSummary(summaryResult)
        print "co.label:", co.label
        print "co.data:", co.data

        # how can we get a bunch of data?
        b = DF('b1') # inits to -1
        checkAst(astForInit(b))
        c = DF('c1') # inits to -1
        checkAst(astForInit(c))
        print "lastExecResult:", dump_json(h2o_xl.Xbase.lastExecResult)

        h2p.yellow_print("Assign compare1")
        Assign(c[0], c[0] + 0)
        checkAst("(= ([ %c1 #0 #0) (+ ([ %c1 #0 #0) #0))")

        h2p.yellow_print("Assign compare2")
        Assign(c[0], c[0] - 0)
        checkAst("(= ([ %c1 #0 #0) (- ([ %c1 #0 #0) #0))")

        h2p.yellow_print("Assign compare3")
        Assign(c[0], c[0] == 0)
        checkAst("(= ([ %c1 #0 #0) (n ([ %c1 #0 #0) #0))")

        h2p.yellow_print("Assign compare4")
        Assign(c[0], c[0] != 0)
        checkAst("(= ([ %c1 #0 #0) (N ([ %c1 #0 #0) #0))")

        # h2o_xl.debugPrintEnable = True

        #*****************************************
        c = DF('c1')

        h2p.yellow_print("<<= compare1")
        c[0] <<= (c[0] + 0)
        checkAst("(= ([ %c1 #0 #0) (+ ([ %c1 #0 #0) #0))")

        h2p.yellow_print("<<= compare2")
        c[0] <<= (c[0] - 0)
        checkAst("(= ([ %c1 #0 #0) (- ([ %c1 #0 #0) #0))")

        h2p.yellow_print("<<= compare3")
        c[0] <<= (c[0] == 0)
        checkAst("(= ([ %c1 #0 #0) (n ([ %c1 #0 #0) #0))")

        #*****************************************
        c = DF('c1') # inits to -1
        h2p.yellow_print("compare1")
        # doesn't assign result to a key?, gets result if scalar, otherwise gets a list or ??? 
        # .result can give us scalar, list, Key, None

        # .result could be a property that triggers a csv download, if we didn't cache the scalar/list result because it was small?
        # i.e. check if .result_cached was None, when .result property is used (property to avoid the need for ()
        result = Expr(c[0] == -1).result
        checkAst("(n ([ %c1 #0 #0) #-1)")
        h2p.yellow_print("Expr result..Desire: python datatype/value if scalar or list,.else Key: %s %s" % (type(result), result))
        assert result == 1.0, "%s %s" % (type(result), result) # real result?

        if result:
            print "true for if of result", type(result), result
        else:
            print "else for if of result", type(result), result

        #*****************************************
        # difference is this goes to a temp key, so if not scalar, you can still get the results by looking at the key
        result = Assign(None, c[0]==-1).result
        checkAst("(= !knon_0x1a34250 (n ([ %c1 #0 #0) #-1))")
        h2p.yellow_print("Assign result..Desire: python datatype/value if scalar or list,.else Key: %s %s" % (type(result), result))
        assert result == 1.0, "%s %s" % (type(result), result) # real result?

        if result:
            print "true if of result", result
        else:
            print "false if of result", result
    
    def test_xl_ast_assert_X(self):
        # uses h2o_xl to do magic with Rapids
        # does this DFInit to rows=0 now?
        a = DF('a1')
        checkAst(astForInit(a))
        b = DF('b1')
        checkAst(astForInit(b))
        c = DF('c1')
        checkAst(astForInit(c))
        # look at our secret stash in the base class. Should see the DFInit?
        print "lastExecResult:", dump_json(h2o_xl.Xbase.lastExecResult)

        # DF does a kv store init. Key doesn't
        # DF inherits from Key. KeyIndexed inherits from Key
        assert isinstance(a, DF)
        assert isinstance(a, Key)
        assert isinstance(a, Xbase)

        assert not isinstance(a, KeyIndexed)
        assert not isinstance(a, Fcn)
        assert not isinstance(a, Assign)

        assert isinstance(a, Key)
        assert isinstance(b, Key)
        assert isinstance(c, Key)

        Assign(a, 2)
        checkAst("(= !a1 #2)")
        Assign(b, 2)
        checkAst("(= !b1 #2)")
        Assign(c, 2)
        checkAst("(= !c1 #2)")

        # - doesn't exist? multiply by -1?
        Assign(c, ~c)
        checkAst("(= !c1 (^ %c1 #1))") # not right if more than 1 col?
        Assign(c, -c)
        checkAst("(= !c1 (_ %c1))")
        Assign(c, abs(c))
        checkAst("(= !c1 (abs %c1))")

        # this needs to be an h2o int? because it expects int return
        # Assign(c, int(c))
        # checkAst("(= !c1 (trunc c1 ))")

        Assign(a, [0])
        checkAst("(= !a1 (c {#0}))")
        Assign(b, [0,1])
        checkAst("(= !b1 (c {#0;#1}))")
        Assign(c, [0,1,2])
        checkAst("(= !c1 (c {#0;#1;#2}))")

        Assign(a, (0,)) # make sure it's a tuple with comma
        checkAst("(= !a1 (c {#0}))")
        Assign(b, (0,1))
        checkAst("(= !b1 (c {#0;#1}))")
        Assign(c, (0,1,2))
        checkAst("(= !c1 (c {#0;#1;#2}))")

        Assign(c, a[0] + b[1])
        checkAst("(= !c1 (+ ([ %a1 #0 #0) ([ %b1 #1 #0)))")

        Assign(c[0], (a[0] + b[1]))
        checkAst("(= ([ %c1 #0 #0) (+ ([ %a1 #0 #0) ([ %b1 #1 #0)))")

        # print "\nDoes the keyWriteHistoryList work?"
        for k in Xbase.keyWriteHistoryList:
            print k

        h2o.check_sandbox_for_errors()

    def test_xl_ast_assert_Y(self):
        a = DF('a1')
        checkAst(astForInit(a))
        b = DF('b1')
        checkAst(astForInit(b))
        c = DF('c1')
        checkAst(astForInit(c))

        assert isinstance(a, Key)
        assert isinstance(b, Key)
        assert isinstance(c, Key)

        a <<= 0
        checkAst("(= !a1 #0)")
        b <<= 0
        checkAst("(= !b1 #0)")
        c <<= 0
        checkAst("(= !c1 #0)")

        a <<= [0]
        checkAst("(= !a1 (c {#0}))")
        b <<= [0,1]
        checkAst("(= !b1 (c {#0;#1}))")
        c <<= [0,1,2]
        checkAst("(= !c1 (c {#0;#1;#2}))")

        a <<= (0,) # make sure it's a tuple with comma
        checkAst("(= !a1 (c {#0}))")
        b <<= (0,1)
        checkAst("(= !b1 (c {#0;#1}))")
        c <<= (0,1,2)
        checkAst("(= !c1 (c {#0;#1;#2}))")

        c <<= a[0] + b[1]
        checkAst("(= !c1 (+ ([ %a1 #0 #0) ([ %b1 #1 #0)))")

        c[0] <<= a[0] + b[1]
        checkAst("(= ([ %c1 #0 #0) (+ ([ %a1 #0 #0) ([ %b1 #1 #0)))")

        # print "\nDoes the keyWriteHistoryList work?"
        for k in Xbase.keyWriteHistoryList:
            print k

        h2o.check_sandbox_for_errors()

    def test_xl_ast_assert_Z(self):
        a = DF('a1')
        checkAst(astForInit(a))
        b = DF('b1')
        checkAst(astForInit(b))
        c = DF('c1')
        checkAst(astForInit(c))

        assert isinstance(a, Key)
        assert isinstance(b, Key)
        assert isinstance(c, Key)

        # this just overwrite the a/b/c with python datatypes
        if 1==0:
            a = 0
            checkAst("(= !a1 #0)")
            b = 0
            checkAst("(= !b1 #0)")
            c = 0
            checkAst("(= !c1 #0)")

            a = [0]
            checkAst("(= !a1 (c {#0}))")
            b = [0,1]
            checkAst("(= !b1 (c {#0;#1}))")
            c = [0,1,2]
            checkAst("(= !c1 (c {#0;#1;#2}))")

            a = (0,) # make sure it's a tuple with comma
            checkAst("(= !a1 (c {#0}))")
            b = (0,1)
            checkAst("(= !b1 (c {#0;#1}))")
            c = (0,1,2)
            checkAst("(= !c1 (c {#0;#1;#2}))")


        # added to init the keys, to avoid AAIOBE at h2o
        a <<= [0] # comma isn't needed
        checkAst("(= !a1 (c {#0}))")
        b <<= [0,1]
        checkAst("(= !b1 (c {#0;#1}))")
        c <<= [0,1,2]
        checkAst("(= !c1 (c {#0;#1;#2}))")

        # these don't work
        if 1==0:
            c = a[0] + b[1]
            # no .do() needed because of types on rhs? or ?
            c.do()
            checkAst("(= !c1 (+ ([ %a1 #0 #0) ([ %b1 #1 #0)))")

            c[0] = a[0] + b[1]
            c.do()
            checkAst("(= ([ %c1 #0 #0) (+ ([ %a1 #0 #0) ([ %b1 #1 #0)))")

        # print "\nDoes the keyWriteHistoryList work?"
        for k in Xbase.keyWriteHistoryList:
            print k

        h2o.check_sandbox_for_errors()


if __name__ == '__main__':
    h2o.unit_main()

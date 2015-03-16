import unittest, random, sys, time
sys.path.extend(['.','..','../..','py'])
import h2o2 as h2o
import h2o_cmd, h2o_import as h2i, h2o_xl

from h2o_xl import DF, Xbase, Key, KeyIndexed, Assign, Fcn
from h2o_test import dump_json, verboseprint

def checkAst(expected):
    ast = h2o_xl.Xbase.lastExecResult['ast']
    assert ast==expected, "Actual: %s    Expected: %s" % (ast, expected)
    print "----------------------------------------------------------------\n"

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

    
    def test_xl_seq_A(self):
        # uses h2o_xl to do magic with Rapids
        # does this DFInit to rows=0 now?
        a = DF('a1') # knon_* key
        b = DF('b1')
        c = DF('c1')
        print "lastExecResult:", dump_json(h2o_xl.Xbase.lastExecResult)
        # look at our secret stash in the base class. Should see the DFInit?

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

        Assign(a, 0)
        checkAst("(= !a1 #0)")
        Assign(b, 0)
        checkAst("(= !b1 #0)")
        Assign(c, 0)
        checkAst("(= !c1 #0)")

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

    def test_xl_seq_B(self):
        a = DF('a1')
        b = DF('b1')
        c = DF('c1')

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

    def test_xl_seq_C(self):
        a = DF('a1')
        b = DF('b1')
        c = DF('c1')

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

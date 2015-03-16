import unittest, random, sys, time
sys.path.extend(['.','..','../..','py'])
import h2o2 as h2o
import h2o_cmd, h2o_import as h2i, h2o_xl

from h2o_xl import DF, Xbase, Key, KeyIndexed, Assign, Fcn
from h2o_test import dump_json, verboseprint


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

    def test_xl_real(self):
        bucket = 'smalldata'
        csvPathname = 'iris/iris_wheader.csv'
        hexDF = 'v'
        parseResult = h2i.import_parse(bucket=bucket, path=csvPathname, schema='put', hex_key=hexDF)


        # uses h2o_xl to do magic with Rapids
        # does this DFInit to rows=0 now?
        a = DF('a1') # knon_* key
        assert isinstance(a, DF)
        assert isinstance(a, Key)
        assert isinstance(a, Xbase)
        assert not isinstance(a, KeyIndexed)
        assert not isinstance(a, Fcn)
        assert not isinstance(a, Assign)

        # look at our secret stash in the base class. Should see the DFInit?
        print "Does the lastExecResult stash work?", dump_json(h2o_xl.Xbase.lastExecResult)
        # this should work if str(DF) returns DF.frame
        inspect = h2o_cmd.runInspect(key=a)
        # print "inspect a", dump_json(inspect)

        b = DF('b1')
        assert isinstance(b, DF)
        inspect = h2o_cmd.runInspect(key=b)
        # print "inspect b", dump_json(inspect)

        Assign(a, [0.0, 1.0, 2.0])
        assert isinstance(a, Key)
        b <<= [3.1, 4.1, 5.1]
        assert isinstance(b, Key)
        # FIX! how come I have to create c here first for python
        # see here
        # http://eli.thegreenplace.net/2011/05/15/understanding-unboundlocalerror-in-python
        # is it too much to require c to exist first?
        # c = DF()
        # c <<= a + b

        # this will trigger ok?
        c = DF('c1')
        c <<= [6.2, 7.2, 8.2]
        assert isinstance(c, Key)
        # c[0] <<= a + b
        # Assign(lhs=c[0], rhs=(a + b))
        rhs = a + b
        Assign(c, rhs)
        ast = h2o_xl.Xbase.lastExecResult['ast']
        astExpected = "(= !c1 (+ %a1 %b1))"
        assert ast==astExpected, "Actual: %s    Expected: %s" % (ast, astExpected)

        rhs = a[0] + b[0]
        Assign(c[0], rhs)
        ast = h2o_xl.Xbase.lastExecResult['ast']
        astExpected = "(= ([ %c1 #0 #0) (+ ([ %a1 #0 #0) ([ %b1 #0 #0)))"
        assert ast==astExpected, "Actual: %s    Expected: %s" % (ast, astExpected)

        Assign(c[1], (a[2] + b[2]))
        ast = h2o_xl.Xbase.lastExecResult['ast']
        astExpected = "(= ([ %c1 #1 #0) (+ ([ %a1 #2 #0) ([ %b1 #2 #0)))"
        assert ast==astExpected, "Actual: %s    Expected: %s" % (ast, astExpected)

        # assert ast = "(= !b1 (is.na (c {#0})))"

        assert isinstance(c, Key), type(c)

        inspect = h2o_cmd.runInspect(key=c)
        # # print "inspect c", dump_json(inspect)

        # DF inits the frame
        # if you just want an existing Key, say existing=True
        a = DF('a2') # named data frame
        assert isinstance(a, DF)
        b = DF('b2')
        c = DF('c2')
        inspect = h2o_cmd.runInspect(key=c)
        # # print "inspect c", dump_json(inspect)

        a <<= 3
        b <<= 3
        c <<= 3
        c[0] <<= a[0] + b[0]
        assert isinstance(c, Key)
        inspect = h2o_cmd.runInspect(key=c)
        # print "inspect c", dump_json(inspect)

        a = DF('a3') # named data frame
        b = DF('b3')
        c = DF('c3')
        a <<= 4
        b <<= 4
        c <<= 4

        c[0] <<= a[0] - b[0]
        assert isinstance(c, Key)
        c[0] <<= a[0] * b[0]
        assert isinstance(c, Key)

        a = DF('a4') # named data frame
        b = DF('b4')
        c = DF('c4')
        a <<= 5
        b <<= 5
        c <<= 5
        c[0] <<= (a[0] - b[0])
        assert isinstance(c, Key)
        inspect = h2o_cmd.runInspect(key=c)
        # print "inspect c", dump_json(inspect)

        c[0] <<= (a[0] & b[0]) | a[0]
        assert isinstance(c, Key)
        inspect = h2o_cmd.runInspect(key=c)
        # print "inspect c", dump_json(inspect)

        # print "\nDoes the keyWriteHistoryList work?"
        for k in Xbase.keyWriteHistoryList:
            print k

        h2o.check_sandbox_for_errors()


if __name__ == '__main__':
    h2o.unit_main()

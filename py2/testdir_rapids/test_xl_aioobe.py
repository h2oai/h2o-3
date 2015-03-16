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

    def test_xl_oobe(self):
        # uses h2o_xl to do magic with Rapids
        # does this DFInit to rows=0 now?
        a = DF('a1') # knon_* key
        b = DF('b1')
        c = DF('c1')
        # look at our secret stash in the base class. Should see the DFInit?

        assert isinstance(a, DF)
        assert isinstance(a, Key)
        assert isinstance(a, Xbase)
        assert not isinstance(a, KeyIndexed)
        assert not isinstance(a, Fcn)
        assert not isinstance(a, Assign)

        Assign(a, range(5))
        Assign(b, range(5))
        Assign(c, range(5))
        print "lastExecResult:", dump_json(h2o_xl.Xbase.lastExecResult)

        assert isinstance(a, Key)
        assert isinstance(b, Key)
        assert isinstance(c, Key)

        # print "Referring to non-existent rows causes a problem (AAIOBE)"
        # not any more..change it to legal case
        Assign(c[1], (a[2] + b[2]))
        ast = h2o_xl.Xbase.lastExecResult['ast']
        astExpected = "(= ([ %c1 #1 #0) (+ ([ %a1 #2 #0) ([ %b1 #2 #0)))"
        assert ast==astExpected, "Actual: %s    Expected: %s" % (ast, astExpected)

        # print "\nDoes the keyWriteHistoryList work?"
        for k in Xbase.keyWriteHistoryList:
            print k

        h2o.check_sandbox_for_errors()


if __name__ == '__main__':
    h2o.unit_main()

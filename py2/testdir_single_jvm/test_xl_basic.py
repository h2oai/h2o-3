import unittest, random, sys, time
sys.path.extend(['.','..','../..','py'])
import h2o, h2o_cmd, h2o_import as h2i, h2o_xl

from h2o_xl import DF, Xbase
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

    def test_xl_basic(self):
        bucket = 'smalldata'
        csvPathname = 'iris/iris_wheader.csv'
        hexDF = 'v'
        parseResult = h2i.import_parse(bucket=bucket, path=csvPathname, schema='put', hex_key=hexDF)


        # uses h2o_xl to do magic with Rapids
        # does this DFInit to rows=0 now?
        a = DF() # knon_* key

        # look at our secret stash in the base class. Should see the DFInit?
        print "Does the lastExecResult stash work?", dump_json(h2o_xl.Xbase.lastExecResult)

        # this should work if str(DF) returns DF.frame
        inspect = h2o_cmd.runInspect(key=a)
        print "inspect a", dump_json(inspect)

        b = DF()
        inspect = h2o_cmd.runInspect(key=b)
        print "inspect b", dump_json(inspect)

        a <<= 0
        b <<= 0
        # FIX! how come I have to create c here first for python
        c = DF()
        c <<= a + b
        inspect = h2o_cmd.runInspect(key=c)
        print "inspect c", dump_json(inspect)

        # DF inits the frame
        # if you just want an existing Key, say existing=True
        a = DF('a') # named data frame
        b = DF('b')

        # c has to exist first
        c = DF('c')
        inspect = h2o_cmd.runInspect(key=c)
        print "inspect c", dump_json(inspect)

        c[0] <<= a[0] + b[0]
        inspect = h2o_cmd.runInspect(key=c)
        print "inspect c", dump_json(inspect)

        c[0] <<= a[0] - b[0]
        c[0] <<= a[0] * b[0]

        c[0] <<= (a[0] - b[0])
        inspect = h2o_cmd.runInspect(key=c)
        print "inspect c", dump_json(inspect)

        c[0] <<= (a[0] & b[0]) | a[0]
        inspect = h2o_cmd.runInspect(key=c)
        print "inspect c", dump_json(inspect)

        print "\nDoes the keyWriteHistoryList work?"
        for k in Xbase.keyWriteHistoryList:
            print k

        h2o.check_sandbox_for_errors()


if __name__ == '__main__':
    h2o.unit_main()

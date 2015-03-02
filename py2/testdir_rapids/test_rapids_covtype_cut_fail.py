import unittest, random, sys, time
sys.path.extend(['.','..','../..','py'])
import h2o2 as h2o
import h2o_exec as h2e, h2o_import as h2i, h2o_cmd
import h2o_print as h2p
from h2o_test import dump_json, verboseprint
import re

DO_ROLLUP = True
#    '(= !e8 (c {#1;#2;#3}))',
exprList = [
    '(= !e8 ([ %p (& (& (& (& (& (& (& (n #0 ([ %p "null" #0)) (n #3 ([ %p "null" #1))) (n #0 ([ %p "null" #2))) (n #0 ([ %p "null" #3))) (n #2 ([ %p "null" #4))) (n #3 ([ %p "null" #5))) (n #1 ([ %p "null" #6))) (n #1 ([ %p "null" #9))) "null"))',
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
        bucket = 'home-0xdiag-datasets'
        csvPathname = 'standard/covtype.data'
        hexKey = 'p'
        parseResult = h2i.import_parse(bucket=bucket, path=csvPathname, schema='put', hex_key=hexKey)

        keys = []
        for execExpr in exprList:
            r = re.match ('\(= \!([a-zA-Z0-9_]+) ', execExpr)
            resultKey = r.group(1)
            execResult, result = h2e.exec_expr(h2o.nodes[0], execExpr, resultKey=resultKey, timeoutSecs=4)
            if DO_ROLLUP:
                h2o_cmd.runInspect(key=resultKey)
            # rows might be zero!
            if execResult['num_rows'] or execResult['num_cols']:
                keys.append(execExpr)
            else:
                h2p.yellow_print("\nNo key created?\n", dump_json(execResult))

        print "\nExpressions that created keys. Shouldn't all of these expressions create keys"

        for k in keys:
            print k

        h2o.check_sandbox_for_errors()

if __name__ == '__main__':
    h2o.unit_main()

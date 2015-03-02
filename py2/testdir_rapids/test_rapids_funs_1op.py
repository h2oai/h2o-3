import unittest, random, sys, time
sys.path.extend(['.','..','../..','py'])
import h2o2 as h2o
import h2o_cmd, h2o_browse as h2b, h2o_import as h2i, h2o_exec as h2e
from h2o_test import verboseprint, dump_json
from h2o_xl import KeyIndexed, Fcn, Seq, Colon, Assign, Item, Col, Xbase
import h2o_xl

print "Slice many rows"
def write_syn_dataset(csvPathname, rowCount, colCount, SEED):
    # 8 random generatators, 1 per column
    r1 = random.Random(SEED)
    dsf = open(csvPathname, "w+")

    for i in range(rowCount):
        rowData = []
        for j in range(colCount):
            r = r1.randint(0,1)
            rowData.append(r)

        rowDataCsv = ",".join(map(str,rowData))
        dsf.write(rowDataCsv + "\n")

    dsf.close()

class Basic(unittest.TestCase):
    def tearDown(self):
        h2o.check_sandbox_for_errors()

    @classmethod
    def setUpClass(cls):
        global SEED
        SEED = h2o.setup_random_seed()
        h2o.init(1, java_heap_GB=14, sandbox_ignore_errors=True)

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()

    def test_rapids_funs_1op(self):
        SYNDATASETS_DIR = h2o.make_syn_dir()
        tryList = [
            # (1000000, 5, 'cA', 200),
            (1000, 5, 'cA', 200),
            ]

        # h2b.browseTheCloud()
        for (rowCount, colCount, hex_key, timeoutSecs) in tryList:
            SEEDPERFILE = random.randint(0, sys.maxint)

            csvFilename = 'syn_' + str(SEEDPERFILE) + "_" + str(rowCount) + 'x' + str(colCount) + '.csv'
            csvPathname = SYNDATASETS_DIR + '/' + csvFilename

            print "\nCreating random", csvPathname
            write_syn_dataset(csvPathname, rowCount, colCount, SEEDPERFILE)
            parseResult = h2i.import_parse(path=csvPathname, schema='put', hex_key=hex_key, 
                timeoutSecs=timeoutSecs, doSummary=False)

            inspect = h2o_cmd.runInspect(key=hex_key)
            missingList, labelList, numRows, numCols = h2o_cmd.infoFromInspect(inspect)

            print "\n" + csvPathname, \
                "    numRows:", "{:,}".format(numRows), \
                "    numCols:", "{:,}".format(numCols)

            # should match # of cols in header or ??
            self.assertEqual(numCols, colCount,
                "parse created result with the wrong number of cols %s %s" % (numCols, colCount))
            self.assertEqual(numRows, rowCount,
                "parse created result with the wrong number of rows %s %s" % (numRows, rowCount))

            # Xbase.debugOnly = True

            REPEAT = 1
            data_key = hex_key
            data_key2 = hex_key + "_2"
            trial = 0
            good = []
            bad = []
            both = h2o_xl.xFcnOp1Set.union(h2o_xl.xFcnOp3Set)
            both = h2o_xl.xFcnOp1Set
            for fun in both:

                a = None
                try:
                    result_key = data_key + "_" + str(trial)
                    # copy the key
                    Assign(data_key2, data_key)

                    # a = Assign(result_key, Fcn(fun, KeyIndexed(data_key2, col=0), True))

                    # a = Assign(result_key, Fcn('sum', KeyIndexed(data_key2, col=0), True))
                    # a = Assign(result_key, Fcn('xorsum', KeyIndexed(data_key2, col=0), True))
                    # a = Assign(result_key, Fcn('sqrt', KeyIndexed(data_key2, col=0)))
                    # a = Assign(result_key, Fcn('ncol', KeyIndexed(data_key2, col=0)))

                    # what's wrong with mean?
                    if fun in ['ncol', 'asin', 'any.factor', 'sin', 'atan', 'tan', 'sign', 'log', 'exp', 'sqrt', 'abs', 'floor', 'ceiling', 'trunc','is.factor', 'is.na', 'any.na', 'nrow', 'tanh', 'length', 'acos', 'cos', 'sinh', 'cosh']:
                        a = Assign(result_key, Fcn(fun, KeyIndexed(data_key2, col=0)))
                        good.append(fun)
                    elif fun in ['sum', 'max', 'min', 'xorsum', 'sd']:
                        a = Assign(result_key, Fcn(fun, KeyIndexed(data_key2, col=0), True))
                        good.append(fun)
                    elif fun in ['scale']:
                        a = Assign(result_key, Fcn(fun, KeyIndexed(data_key2, col=0), False, False))
                        good.append(fun)
                    elif fun in ['round', 'signif']:
                        a = Assign(result_key, Fcn(fun, KeyIndexed(data_key2, col=0), 1))
                        good.append(fun)
                    elif fun in ['seq_len', 'rep_len']:
                        a = Assign(result_key, Fcn(fun, KeyIndexed(data_key2, col=0), 4))
                        good.append(fun)
                    elif fun in ['seq']:
                        a = Assign(result_key, Fcn(fun, KeyIndexed(data_key2, col=0), 1, 5, 1))
                        good.append(fun)
                    elif fun in ['mean']:
                        a = Assign(result_key, Fcn(fun, KeyIndexed(data_key2, col=0), 0, False))
                        good.append(fun)
                    elif fun in ['var']:
                        a = Assign(result_key, Fcn(fun, KeyIndexed(data_key2, col=0), False, False, False))
                        good.append(fun)
                    elif fun in ['match']:
                        a = Assign(result_key, Fcn(fun, KeyIndexed(data_key2, col=0), KeyIndexed(data_key2, col=0), 1, None))
                        good.append(fun)
                    elif fun in ['unique']:
                        a = Assign(result_key, Fcn(fun, KeyIndexed(data_key2, col=0), False, 10, 1))
                        good.append(fun)
                    else:
                        # bad functions kill h2o?
                        a = Assign(result_key, Fcn(fun, KeyIndexed(data_key2, col=0), None))
                        bad.append(fun)

                        # a = Fcn(fun, KeyIndexed(data_key, col=0), '%FALSE ')
                        # a = Fcn(fun, data_key, '%FALSE')
                        # a = Fcn(fun, data_key)

                    # scalars?
                    if 1==0:
                        inspect = h2o_cmd.runInspect(key=result_key)
                        missingList, labelList, numRows, numCols = h2o_cmd.infoFromInspect(inspect)
                        assert numRows==1000, numRows
                        assert numCols==1, numCols

                        print "\n" + csvPathname, \
                            "    numRows:", "{:,}".format(numRows), \
                            "    numCols:", "{:,}".format(numCols)

                except: 
                    if not a:
                        # print dump_json(a.execResult)
                        bad.append(fun)

                trial += 1

            print "good:", good
            print "bad:", bad

if __name__ == '__main__':
    h2o.unit_main()

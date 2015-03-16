import unittest, random, sys, time
sys.path.extend(['.','..','../..','py'])
import h2o2 as h2o
import h2o_cmd, h2o_browse as h2b, h2o_import as h2i, h2o_exec as h2e

from h2o_xl import Key, KeyIndexed, Fcn,Seq, Colon, Assign, Item, Expr, Col, Cut

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
        h2o.init(1,java_heap_GB=14)

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()

    def test_rapids_cut(self):
        SYNDATASETS_DIR = h2o.make_syn_dir()
        tryList = [
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


            REPEAT = 1
            data_key = hex_key
            for i in range(REPEAT):
                result_key = data_key + "_" + str(i)

                Assign('seq1', Seq(range(5)) )
                # take advantage of default params for row/col (None)
                # need the 'c' function, to make sure the key is created

                Assign('seq2', Fcn('c', Seq(range(5)) ))
                inspect = h2o_cmd.runInspect(key='seq1')
                missingList, labelList, numRows, numCols = h2o_cmd.infoFromInspect(inspect)

                Assign('seq3', Col(Seq(range(5))) )
                inspect = h2o_cmd.runInspect(key='seq2')
                missingList, labelList, numRows, numCols = h2o_cmd.infoFromInspect(inspect)

                # can't have sequence of sequences?
                # make sure key is created with c()
                Assign('seq4', Fcn('c', Seq(Colon(99,400), "#2", 1, range(1,5), range(7,10), range(50,52) )) )

                inspect = h2o_cmd.runInspect(key='seq1')
                missingList, labelList, numRows, numCols = h2o_cmd.infoFromInspect(inspect)
            
                Assign(result_key, KeyIndexed(data_key, row=Seq(range(1, 5))) )
                Assign('seq5', KeyIndexed(data_key, row=Seq(Colon(99, 400), "#2", 1, range(1,5))) )

                # they need to be same size
                # Assign('seq6', Key('seq5') + Key('seq4') + Key('seq3'))

                # doesn't like my cut? complains on FALSE
                # Assign(result_key, Cut(KeyIndexed(data_key, col=0)))
                # Assign(result_key, Cut(KeyIndexed(data_key, col=1), breaks=3))

                Assign(result_key, Fcn('min', KeyIndexed(data_key, col=1), True))
                Assign(result_key, Fcn('max', KeyIndexed(data_key, col=1), True))
                Assign(result_key, Fcn('mean', KeyIndexed(data_key, col=1), 0, False))

                Assign(result_key, KeyIndexed(data_key, row='#1'))
                Assign(result_key, KeyIndexed(data_key, row=Colon('#1', '#100')))
                Assign(result_key, KeyIndexed(data_key, row=Colon(1, 100)))
                # this should fail rapids because of reverse msb/lsb
                # illegal, detected
                # resultExpr, result = Assign(result_key, KeyIndexed(data_key, row=Colon('#100', '#1')))
                Assign(result_key, KeyIndexed(data_key, row=Colon('#-2', '#-1')))
                Assign(result_key, KeyIndexed(data_key, row=Colon(-2, -1)))
                # illegal, detected
                # resultExpr, result = Assign(result_key, KeyIndexed(data_key, row=Colon('#-1', '#-2')))
                # take advantage of number to string conversion
                Assign(result_key, KeyIndexed(data_key, row=Colon('#1', rowCount-10)))
                Assign(result_key, KeyIndexed(data_key, col=Colon('#1', colCount-1, )))

                # no assign. Expr() complains when result has no key?
                Assign(result_key, KeyIndexed(data_key, row=Colon('#1', rowCount-10)))
                Assign(result_key, KeyIndexed(data_key, col=Colon('#1', colCount-1,)))

                # do some function translation
                Assign(result_key, Fcn('==', 1, KeyIndexed(data_key, col=Colon('#1', colCount-1,))) )

                print "\n" + csvPathname, \
                    "    numRows:", "{:,}".format(numRows), \
                    "    numCols:", "{:,}".format(numCols)

if __name__ == '__main__':
    h2o.unit_main()

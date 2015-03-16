import unittest, random, sys, time
sys.path.extend(['.','..','../..','py'])
import h2o2 as h2o
import h2o_cmd, h2o_browse as h2b, h2o_import as h2i, h2o_exec as h2e
from h2o_test import verboseprint, dump_json
from h2o_xl import KeyIndexed, Fcn, Seq, Colon, Assign, Item, Col, Xbase

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

    def test_rapids_row_range(self):
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
            for i in range(REPEAT):
                result_key = data_key + "_" + str(i)
                # Assign('s1', Seq(range(5)) ).do
                Assign('s1', Seq(range(5)) )

                # take advantage of default params for row/col (None)
                # need the 'c' function, to make sure the key is created

                # first try as object, then method
                Assign('s2', Fcn('c', Seq(range(5)) ))
                print dump_json(Xbase.lastExecResult)
                print dump_json(Xbase.lastResult)

                # just combine
                Assign('s3', Col(Seq(range(5)) ))

                inspect = h2o_cmd.runInspect(key='s3')
                missingList, labelList, numRows, numCols = h2o_cmd.infoFromInspect(inspect)
                assert numRows==5
                assert numCols==1

                Assign('s2', Col(Seq(range(5))) )

                inspect = h2o_cmd.runInspect(key='s2')
                missingList, labelList, numRows, numCols = h2o_cmd.infoFromInspect(inspect)
                assert numRows==5
                assert numCols==1

                # can't have sequence of sequences?
                # make sure key is created with c()
                f = Fcn('c', Seq(Colon(99,400), "#2", 1, range(1,5), range(7,10), range(50,52) ))
                Assign('s1', f)

                f = Col(Seq(Colon(99,400), "#2", 1, range(1,5), range(7,10), range(50,52) ))
                Assign('s2', f)

                inspect = h2o_cmd.runInspect(key='s2')
                missingList, labelList, numRows, numCols = h2o_cmd.infoFromInspect(inspect)
                assert numRows==313
                assert numCols==1
            
                print "z1" 
                Assign(result_key, KeyIndexed(data_key, row=Seq(range(1, 5))) )
                print "z2" 
                Assign('s1', KeyIndexed(data_key, row=Seq(Colon(99, 400), "#2", 1, range(1,5))) )

                print "z3" 
                Assign(result_key, KeyIndexed(data_key, row='#1')).do
                print "z4" 
                Assign(result_key, KeyIndexed(data_key, row=Colon('#1', '#100')))
                print "z5" 
                Assign(result_key, KeyIndexed(data_key, row=Colon(1, 100)))
                # this should fail rapids because of reverse msb/lsb
                # illegal, detected
                # execResult, Assign(result_key, KeyIndexed(data_key, row=Colon('#100', '#1')))
                print "z6" 
                Assign(result_key, KeyIndexed(data_key, row=Colon('#-2', '#-1')))
                print "z7" 
                Assign(result_key, KeyIndexed(data_key, row=Colon(-2, -1)))
                # illegal, detected
                # execResult, Assign(result_key, KeyIndexed(data_key, row=Colon('#-1', '#-2')))
                # take advantage of number to string conversion
                print "z8" 
                Assign(result_key, KeyIndexed(data_key, row=Colon('#1', rowCount-10)))
                print "z9" 
                Assign(result_key, KeyIndexed(data_key, col=Colon('#1', colCount-1, )))

                # no assign
                print "z10" 
                result = KeyIndexed(data_key, row=Colon('#1', rowCount-10)).do()
                print "z11" 
                # result = KeyIndexed(data_key, col=Colon('#1', colCount-1,)).do()


                # do some function translation
                print "z12" 
                # result = Fcn('==', 1, KeyIndexed(data_key, col=Colon('#1', colCount-1,))).do()

                print "\n" + csvPathname, \
                    "    numRows:", "{:,}".format(numRows), \
                    "    numCols:", "{:,}".format(numCols)

if __name__ == '__main__':
    h2o.unit_main()

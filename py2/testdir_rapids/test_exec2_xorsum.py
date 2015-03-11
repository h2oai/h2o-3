import unittest, random, sys, time
sys.path.extend(['.','..','../..','py'])
import h2o2 as h2o
import h2o_browse as h2b, h2o_exec as h2e, h2o_import as h2i, h2o_cmd, h2o_util
from h2o_test import dump_json, verboseprint

# new ...ability to reference cols
# src[ src%age<17 && src%zip=95120 && ... , ]
# can specify values for enums ..values are 0 thru n-1 for n enums

exprList = [
        # !x means assign to x
        # %TRUE means remove NAs (this is default)
        # '',
        # '(= !keys (ls))',
        # '(= !x #1)',
        # r1 is unimplemented %r1 works
        # '(= !x (sum ([ %r1 "null" #0) %TRUE))',
        # '(= !x (sum ([ r1 "null" (: #0 #0)) %TRUE))',
        '(= !x (xorsum ([ %r1 "null" #0) %TRUE))',
        # '(= !x (xorsum ([ %r1 "null" (: #0 #0)) %TRUE))',
        ]

ROWS = 100000
#********************************************************************************
def write_syn_dataset(csvPathname, rowCount, colCount, expectedMin, expectedMax, SEEDPERFILE):
    dsf = open(csvPathname, 'w')
    expectedRange = (expectedMax - expectedMin)
    expectedFpSum = float(0)
    expectedUllSum = int(0)
    for row in range(rowCount):
        rowData = []
        for j in range(colCount):
            value = expectedMin + (random.random() * expectedRange)
            r = random.randint(0,1)
            if False and r==0:
                value = -1 * value
            # hack
            if 1==1:
                # value = row * 2

                # bad sum
                # value = 5555555555555 + row
                # bad
                # value = 555555555555 + row
                # value = 55555555555 + row

                # fail
                # value = 5555555555 + row
                rexp = random.randint(0,20)
                value = 2.0**rexp + 3.0*row


                r = random.randint(0,1)
                if r==0:
                    value = -1 * value

                # value = -1 * value
                # value = 2e9 + row
                # value = 3 * row

            # get the expected patterns from python
            fpResult = float(value)
            ullResult = h2o_util.doubleToUnsignedLongLong(fpResult)
            expectedFpSum += fpResult
            expectedUllSum = expectedUllSum ^ ullResult
            # print "%30s" % "expectedUll (0.16x):", "0x%0.16x" % expectedUll

            # Now that you know how many decimals you want, 
            # say, 15, just use a rstrip("0") to get rid of the unnecessary 0s:
            # can't rstrip, because it gets rid of trailing exponents  like +0 which causes NA if + 
            s = "%.16f" % value
            rowData.append(s)

        rowDataCsv = ",".join(map(str,rowData))
        dsf.write(rowDataCsv + "\n")

    dsf.close()
    # print hex(~(0xf << 60))
    # zero 4 bits of sign/exponent like h2o does, to prevent inf/nan
    expectedUllSum = expectedUllSum & ~(0xf << 60)
    return (expectedUllSum, expectedFpSum)

#********************************************************************************
class Basic(unittest.TestCase):
    def tearDown(self):
        h2o.check_sandbox_for_errors()

    @classmethod
    def setUpClass(cls):
        global SEED
        SEED = h2o.setup_random_seed()
        h2o.init(java_heap_GB=12)

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()

    def test_exec2_xorsum(self):
        SYNDATASETS_DIR = h2o.make_syn_dir()

        tryList = [
            (ROWS, 1, 'r1', 0, 10, None),
        ]

        for trial in range(10):
            ullResultList = []
            for (rowCount, colCount, hex_key, expectedMin, expectedMax, expected) in tryList:
                SEEDPERFILE = random.randint(0, sys.maxint)
                # dynamic range of the data may be useful for estimating error
                maxDelta = expectedMax - expectedMin

                csvFilename = 'syn_real_' + str(rowCount) + 'x' + str(colCount) + '.csv'
                csvPathname = SYNDATASETS_DIR + '/' + csvFilename
                csvPathnameFull = h2i.find_folder_and_filename(None, csvPathname, returnFullPath=True)
                print "Creating random", csvPathname
                (expectedUllSum, expectedFpSum)  = write_syn_dataset(csvPathname, 
                    rowCount, colCount, expectedMin, expectedMax, SEEDPERFILE)
                expectedUllSumAsDouble = h2o_util.unsignedLongLongToDouble(expectedUllSum)
                expectedFpSumAsLongLong = h2o_util.doubleToUnsignedLongLong(expectedFpSum)

                parseResult = h2i.import_parse(path=csvPathname, schema='put', hex_key=hex_key, 
                    timeoutSecs=3000, retryDelaySecs=2)
                numRows, numCols, parse_key = h2o_cmd.infoFromParse(parseResult)
                assert parse_key == hex_key
                assert numCols == colCount
                assert numRows == rowCount

                inspect = h2o_cmd.runInspect(key=hex_key)
                missingList, labelList, numRows, numCols = h2o_cmd.infoFromInspect(inspect)
                assert len(missingList) == 0

                # looking at the 8 bytes of bits for the h2o doubles
                # xorsum will zero out the sign and exponent
                for execExpr in exprList:
                    for r in range(10):
        
                        if 1==0:
                            execResult = h2o_cmd.runExec(ast=execExpr, timeoutSecs=30)
                            fpResult = execResult['scalar']
                        else:
                            (execResult, fpResult) = h2e.exec_expr(h2o.nodes[0], execExpr, resultKey='x', timeoutSecs=300)
                            # print dump_json(h2o.n0.frames(key="h"))

                        # (execResult, fpResult) = h2e.exec_expr(h2o.nodes[0], execExpr, resultKey='h', timeoutSecs=300)
                        # print dump_json(h2o.n0.frames(key="r1"))
                        print r, "execResult:", h2o.dump_json(execResult)
                        h2o_cmd.runStoreView()

                        ullResult = h2o_util.doubleToUnsignedLongLong(fpResult)
                        ullResultList.append((ullResult, fpResult))

                        print "%30s" % "ullResult (0.16x):", "0x%0.16x   %s" % (ullResult, fpResult)
                        print "%30s" % "expectedUllSum (0.16x):", "0x%0.16x   %s" % (expectedUllSum, expectedUllSumAsDouble)
                        print "%30s" % "expectedFpSum (0.16x):", "0x%0.16x   %s" % (expectedFpSumAsLongLong, expectedFpSum)

                        # allow diff of the lsb..either way
                        # if ullResult!=expectedUllSum and abs((ullResult-expectedUllSum)>3):
                        if ullResult!=expectedUllSum:
                            raise Exception("h2o didn't get the same xorsum as python. 0x%0.16x 0x%0.16x" % \
                                (ullResult, expectedUllSum))
                            print "h2o didn't get the same xorsum as python. 0x%0.16x 0x%0.16x" % \
                                (ullResult, expectedUllSum)

                h2o.check_sandbox_for_errors()

                print "first result was from a sum. others are xorsum"
                print "ullResultList:"
                for ullResult, fpResult in ullResultList:
                    print "%30s" % "ullResult (0.16x):", "0x%0.16x   %s" % (ullResult, fpResult)

                print "%30s" % "expectedUllSum (0.16x):", "0x%0.16x   %s" % (expectedUllSum, expectedUllSumAsDouble)
                print "%30s" % "expectedFpSum (0.16x):", "0x%0.16x   %s" % (expectedFpSumAsLongLong, expectedFpSum)


if __name__ == '__main__':
    h2o.unit_main()

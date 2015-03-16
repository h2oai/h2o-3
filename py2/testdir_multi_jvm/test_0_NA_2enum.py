import unittest, random, sys, time, os
sys.path.extend(['.','..','../..','py'])
import h2o2 as h2o
import h2o_cmd, h2o_import as h2i

print "This only tests mixed 0 and NA. All NA or All 0 might be different"
DO_REBALANCE = False
REBALANCE_CHUNKS = 100

# zero can be 0 (int) or 0.0000? (numeric?) 
def write_syn_dataset(csvPathname, rowCount, colCount, zero, SEED):
    r1 = random.Random(SEED)
    dsf = open(csvPathname, "w+")

    for i in range(rowCount):
        rowDataStr = []
        for j in range(colCount):
            ri1 = int(r1.triangular(0,2,0.75))
            if ri1==1:
                ri1 = 'NA'
            else:
                ri1 = zero
            
            rowDataStr.append(ri1)
        rowDataCsv = ",".join(rowDataStr)
        dsf.write(rowDataCsv + "\n")

    dsf.close()


class Basic(unittest.TestCase):
    def tearDown(self):
        h2o.check_sandbox_for_errors()

    @classmethod
    def setUpClass(cls):
        global SEED
        SEED = h2o.setup_random_seed()
        h2o.init(3)

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()

    def test_0_NA_2enum(self):
        SYNDATASETS_DIR = h2o.make_syn_dir()
        tryList = [
            (100,  30, '0', 'cC', 100),
            (100,  30, '0.0', 'cC', 100),
            (100,  30, '0.0000000', 'cC', 100),
            ]

        for (rowCount, colCount, zero, hex_key, timeoutSecs) in tryList:
            SEEDPERFILE = random.randint(0, sys.maxint)
            csvFilename = 'syn_' + str(SEEDPERFILE) + "_" + str(rowCount) + 'x' + str(colCount) + '.csv'
            csvPathname = SYNDATASETS_DIR + '/' + csvFilename

            print "\nCreating random", csvPathname
            write_syn_dataset(csvPathname, rowCount, colCount, zero, SEEDPERFILE)

            parseResult = h2i.import_parse(path=csvPathname, schema='put', hex_key=hex_key, timeoutSecs=30, doSummary=False)
            pA = h2o_cmd.ParseObj(parseResult, expectedNumRows=rowCount, expectedNumCols=colCount)
            print pA.numRows, pA.numCols, pA.parse_key

            iA = h2o_cmd.InspectObj(pA.parse_key,
                expectedNumRows=rowCount, expectedNumCols=colCount, expectedMissinglist=[])
            print iA.missingList, iA.labelList, iA.numRows, iA.numCols

            # column 0 not used here
            # assert len(expected) == 6
            # FIX! add expected and maxDelta?
            co = h2o_cmd.runSummary(key=hex_key, column=0)
            print co.label, co.type, co.missing, co.domain, sum(co.bins)
            coList = [co.base, len(co.bins), len(co.data), co.domain, co.label, co.maxs, co.mean, co.mins, co.missing,
                co.ninfs, co.pctiles, co.pinfs, co.precision, co.sigma, co.str_data, co.stride, co.type, co.zeros]

            for k,v in co:
                print k, v

            if DO_REBALANCE:
                print "Rebalancing it to create an artificially large # of chunks"
                rb_key = "rb_%s" % hex_key
                start = time.time()
                print "Rebalancing %s to %s with %s chunks" % (hex_key, rb_key, REBALANCE_CHUNKS)
                rebalanceResult = h2o.nodes[0].rebalance(source=hex_key, after=rb_key, chunks=REBALANCE_CHUNKS)
                elapsed = time.time() - start
                print "rebalance end on ", csvFilename, 'took', elapsed, 'seconds'
            else:
                rb_key = hex_key

            print "Now doing to_enum across all columns of %s" % hex_key
            for column_index in range(colCount):
                # is the column index 1-base in to_enum
                result = h2o.nodes[0].to_enum(None, src_key=hex_key, column_index=column_index+1)
                # print "\nto_enum result:", h2o.dump_json(result)
                co = h2o_cmd.runSummary(key=hex_key, column=column_index+1)

                print co.label, co.type, co.missing, co.domain, sum(co.bins)
                coList = [co.base, len(co.bins), len(co.data), co.domain, co.label, co.maxs, co.mean, co.mins, co.missing,
                    co.ninfs, co.pctiles, co.pinfs, co.precision, co.sigma, co.str_data, co.stride, co.type, co.zeros]

                if co.type != 'Enum':
                    raise Exception("column %s, which has name %s, didn't convert to Enum, is %s" % (column_index, colname, co.type))
                # I'm generating NA's ..so it should be > 0. .but it could be zero . I guess i have enough rows to get at least 1
                if co.missing<=0 or co.missing>rowCount:
                    raise Exception("column %s, which has name %s, somehow got NA cnt wrong after convert to Enum  %s %s" % 
                        (column_index, colname, co.missing, rowCount))

                if co.domain!=1: # NAs don't count?
                    # print "stats:", h2o.dump_json(stats)
                    print "column:", h2o.dump_json(co)
                    raise Exception("column %s, which has name %s, should have cardinality 1, got: %s" % (column_index, co.label, domain))



if __name__ == '__main__':
    h2o.unit_main()

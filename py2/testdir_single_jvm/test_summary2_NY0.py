import unittest, time, sys, random, math, getpass
sys.path.extend(['.','..','../..','py'])
import h2o2 as h2o
import h2o_cmd, h2o_import as h2i, h2o_util, h2o_print as h2p

def write_syn_dataset(csvPathname, rowCount, colCount, SEED, choices):
    r1 = random.Random(SEED)
    dsf = open(csvPathname, "w+")

    naCnt = [0 for j in range(colCount)]

    for i in range(rowCount):
        rowData = []
        for j in range(colCount):
            ri = random.choice(choices)
            if ri=='0' or ri==' 0':
                naCnt[j] += 1
            rowData.append(ri)
        rowDataCsv = ",".join(map(str,rowData))
        dsf.write(rowDataCsv + "\n")
    dsf.close()
    return naCnt

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

    def test_summary2_NY0(self):
        SYNDATASETS_DIR = h2o.make_syn_dir()

        choicesList = [
            ('N', 'Y', '0'),
            ('n', 'y', '0'),
            ('F', 'T', '0'),
            ('f', 't', '0'),
            (' N', ' Y', ' 0'),
            (' n', ' y', ' 0'),
            (' F', ' T', ' 0'),
            (' f', ' t', ' 0'),
        ]

        # white space is stripped
        expectedList = [
            ('N', 'Y', '0'),
            ('n', 'y', '0'),
            ('F', 'T', '0'),
            ('f', 't', '0'),
            ('N', 'Y', '0'),
            ('n', 'y', '0'),
            ('F', 'T', '0'),
            ('f', 't', '0'),
        ]

        tryList = [
            # colname, (min, 25th, 50th, 75th, max)
            (100, 200, 'x.hex', choicesList[4], expectedList[4]),
            (100, 200, 'x.hex', choicesList[5], expectedList[5]),
            (100, 200, 'x.hex', choicesList[6], expectedList[6]),
            (100, 200, 'x.hex', choicesList[7], expectedList[7]),
            (100, 200, 'x.hex', choicesList[3], expectedList[3]),
            (1000, 200, 'x.hex', choicesList[2], expectedList[2]),
            (10000, 200, 'x.hex', choicesList[1], expectedList[1]),
            (100000, 200, 'x.hex', choicesList[0], expectedList[0]),
        ]

        timeoutSecs = 10
        trial = 1
        n = h2o.nodes[0]
        lenNodes = len(h2o.nodes)

        x = 0
        timeoutSecs = 60
        for (rowCount, colCount, hex_key, choices, expected) in tryList:
            # max error = half the bin size?
        
            SEEDPERFILE = random.randint(0, sys.maxint)
            x += 1

            csvFilename = 'syn_' + "binary" + "_" + str(rowCount) + 'x' + str(colCount) + '.csv'
            csvPathname = SYNDATASETS_DIR + '/' + csvFilename
            csvPathnameFull = h2i.find_folder_and_filename(None, csvPathname, returnFullPath=True)

            print "Creating random", csvPathname
            expectedNaCnt = write_syn_dataset(csvPathname, rowCount, colCount, SEEDPERFILE, choices)

            parseResult = h2i.import_parse(path=csvPathname, schema='put', hex_key=hex_key, 
                timeoutSecs=10, doSummary=False)
            pA = h2o_cmd.ParseObj(parseResult, expectedNumRows=rowCount, expectedNumCols=colCount)
            print pA.numRows, pA.numCols, pA.parse_key

            iA = h2o_cmd.InspectObj(pA.parse_key,
                expectedNumRows=rowCount, expectedNumCols=colCount, expectedMissinglist=[])
            print iA.missingList, iA.labelList, iA.numRows, iA.numCols

            for i in range(colCount):
                # walks across the columns triggering a summary on the col desired
                # runSummary returns a column object now. inspect and parse don't. They return json.
                # maybe eventually will make them return object? But I also pass expected stuff to them
                # should I pass expected to summary? no, more complex?
                co = h2o_cmd.runSummary(key=hex_key, column=i)
                print co.label, co.type, co.missing_count, co.domain, sum(co.histogram_bins)

                print "\nComparing column %s to expected" % i
                self.assertEqual(expectedNaCnt[i], co.missing_count, "Column %s Expected %s. missing: %s is incorrect" % \
                    (i, expectedNaCnt[i], co.missing_count))
                self.assertEqual(rowCount - expectedNaCnt[i], sum(co.histogram_bins))

            h2p.green_print("\nDone with trial", trial)
            trial += 1

            h2i.delete_keys_at_all_nodes()


if __name__ == '__main__':
    h2o.unit_main()


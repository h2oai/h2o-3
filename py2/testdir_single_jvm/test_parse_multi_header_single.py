import unittest, time, sys, random, os, pprint as pp
sys.path.extend(['.','..','../..','py'])
import h2o2 as h2o
import h2o_cmd, h2o_import as h2i, h2o_util
import h2o_browse as h2b
from h2o_test import dump_json, verboseprint

# for test debug
HEADER = True
dataRowsWithHeader = 0
DO_RF = False

# Don't write headerData if None (for non-header files)
# Don't write data if rowCount is None
def write_syn_dataset(csvPathname, rowCount, headerData, rList):
    dsf = open(csvPathname, "w+")
    
    if headerData is not None:
        dsf.write(headerData + "\n")

    if rowCount is not None:        
        for i in range(rowCount):
            # two choices on the input. Make output choices random
            r = rList[random.randint(0,1)] + "," + str(random.randint(0,7))
            dsf.write(r + "\n")
        dsf.close()
        return rowCount # rows done
    else:
        dsf.close()
        return 0 # rows done

def rand_rowData(colCount):
    rowData = [random.randint(0,7) for i in range(colCount)]
    rowData1= ",".join(map(str,rowData))
    rowData = [random.randint(0,7) for i in range(colCount)]
    rowData2= ",".join(map(str,rowData))
    # RF will complain if all inputs are the same
    r = [rowData1, rowData2]
    return r

class Basic(unittest.TestCase):
    def tearDown(self):
        h2o.check_sandbox_for_errors()

    @classmethod
    def setUpClass(cls):
        global SEED
        SEED = h2o.setup_random_seed()
        h2o.init(java_heap_GB=4,use_flatfile=True)

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()
    
    def test_parse_multi_header_single(self):
        SYNDATASETS_DIR = h2o.make_syn_dir()
        csvFilename = "syn_ints.csv"
        csvPathname = SYNDATASETS_DIR + '/' + csvFilename
        headerData = "ID,CAPSULE,AGE,RACE,DPROS,DCAPS,PSA,VOL,GLEASON,output"

        # cols must be 9 to match the header above, otherwise a different bug is hit
        # extra output is added, so it's 10 total
        tryList = [
            (57, 300, 9, 'cA', 60, 0),
            # try with 1-3 data lines in the header file too
            (57, 300, 9, 'cB', 60, 1),
            (57, 300, 9, 'cC', 60, 2),
            (57, 300, 9, 'cD', 60, 3),
            ]

        trial = 0
        for (fileNum, rowCount, colCount, hex_key, timeoutSecs, dataRowsWithHeader) in tryList:
            trial += 1
            # FIX! should we add a header to them randomly???
            print "Wait while", fileNum, "synthetic files are created in", SYNDATASETS_DIR
            rowxcol = str(rowCount) + 'x' + str(colCount)
            totalCols = colCount + 1 # 1 extra for output
            totalDataRows = 0
            for fileN in range(fileNum):
                csvFilename = 'syn_' + str(fileN) + "_" + str(SEED) + "_" + rowxcol + '.csv'
                csvPathname = SYNDATASETS_DIR + '/' + csvFilename
                rList = rand_rowData(colCount)
                dataRowsDone = write_syn_dataset(csvPathname, rowCount, headerData=None, rList=rList)
                totalDataRows += dataRowsDone

            # create the header file
            # can make it pass by not doing this
            if HEADER:
                csvFilename = 'syn_header_' + str(SEED) + "_" + rowxcol + '.csv'
                csvPathname = SYNDATASETS_DIR + '/' + csvFilename
                dataRowsDone = write_syn_dataset(csvPathname, dataRowsWithHeader, headerData, rList)
                totalDataRows += dataRowsDone

            # make sure all key names are unique, when we re-put and re-parse (h2o caching issues)
            src_key = "syn_" + str(trial)
            hex_key = "syn_" + str(trial) + ".hex"

            # DON"T get redirected to S3! (EC2 hack in config, remember!)
            # use it at the node level directly (because we gen'ed the files.
            # I suppose we could force the redirect state bits in h2o.nodes[0] to False, instead?
            # put them, rather than using import files, so this works if remote h2o is used
            # and python creates the files locally
            fileList = os.listdir(SYNDATASETS_DIR)
            for f in fileList:
                h2i.import_only(path=SYNDATASETS_DIR + "/" + f, schema='put', noPrint=True)
                print f

            # fix. should we have a h2o.n0 for brevity? or h2o.n. ? so we can change it around if multi-node?
            # frames = h2o.nodes[0].frames()['frames']
            frames = h2o.n0.frames()['frames']
            frames_dict = h2o_util.list_to_dict(frames, 'key/name')

            # print "frames:", dump_json(frames)
            # print "frames_dict:", dump_json(frames_dict)

            if HEADER:
                header = h2i.find_key('syn_header')
                if not header:
                    raise Exception("Didn't find syn_header* key in the import")

            # use regex. the only files in the dir will be the ones we just created with  *fileN* match
            print "Header Key = " + header
            start = time.time()

            # does h2o-dev take a regex? or do we need to glob
            parseResult = h2i.parse_only(pattern='*'+rowxcol+'*',
                hex_key=hex_key, timeoutSecs=timeoutSecs, check_header="1") # header_from_file=header

            pA = h2o_cmd.ParseObj(parseResult, expectedNumRows=totalDataRows, expectedNumCols=totalCols)
            print pA.numRows
            print pA.numCols
            print pA.parse_key

            expectedLabelList = headerData.split(",")
            iA = h2o_cmd.InspectObj(pA.parse_key, expectedNumRows=totalDataRows, expectedNumCols=totalCols,
                expectedMissinglist=[], expectedLabelList=expectedLabelList)

            if DO_RF:
                # put in an ignore param, that will fail unless headers were parsed correctly
                if HEADER:
                    kwargs = {'sample_rate': 0.75, 'max_depth': 25, 'ntrees': 1, 'ignored_columns': "['ID','CAPSULE']"}
                else:
                    kwargs = {'sample_rate': 0.75, 'max_depth': 25, 'ntrees': 1}

                rfv = h2o_cmd.runRF(parseResult=parseResult, timeoutSecs=timeoutSecs, **kwargs)

            h2o.check_sandbox_for_errors()

if __name__ == '__main__':
    h2o.unit_main()

import unittest, random, sys, time, codecs
sys.path.extend(['.','..','../..','py'])
import h2o2 as h2o
import h2o_cmd, h2o_import as h2i


print 'not using NUL, (0x0), or " at start of line since they cause add/deletes of row count'

NO_NUL = True # the problem with NUL is covered by other tests. don't use
NO_COMMENT = True
NO_QUOTE_BEGIN = True

DEBUG = False
UTF8 = True
UTF8_MULTIBYTE = True

DISABLE_ALL_NA = False

# if we have empty rows, they mess up the row count. If we have multiple cols, it's less likely for NAs to make an empty row
CAUSE_RANDOM_NA = True

DO_SUMMARY = False # summary slow for some reason
print "We have a problem starting a line with double quote and not ending it before EOL"
print "assume that is unlikely with the distribution being used"


# 0 to 0x1d bad
if UTF8:
    # what about multi-byte UTF8
    ordinalChoices = range(0x1 if NO_NUL else 0x1e, 0x100) # doesn't include last value ..allow ff
else:  # ascii subset?
    ordinalChoices = range(0x1 if NO_NUL else 0x1e, 0x80) # doesn't include last value ..allow 7f
    # remove the comma to avoid creating extra columns

if UTF8_MULTIBYTE:
    def aSmallSet(a, b):
        return random.sample(range(a,b),100)

    if 1==0: # this full range causes too many unique enums? and we get flipped to NA
        ordinalChoicesMulti  = range(0x00001e,0x00007f) # 1byte
        ordinalChoicesMulti += range(0x000080,0x00009f) # 2byte
        ordinalChoicesMulti += range(0x0000a0,0x0003ff) # 2byte
        ordinalChoicesMulti += range(0x000400,0x0007ff) # 2byte
        ordinalChoicesMulti += range(0x000800,0x003fff) # 3byte
        ordinalChoicesMulti += range(0x004000,0x00ffff) # 3byte
        ordinalChoicesMulti += range(0x010000,0x03ffff) # 3byte
        ordinalChoicesMulti += range(0x040000,0x10ffff) # 4byte
    else:
        # just sample 100 from each. 200+ from first
        ordinalChoicesMulti  = range(0x00001e,0x00007f) # 1byte
        ordinalChoicesMulti += range(0x000080,0x00009f) # 2byte
        ordinalChoicesMulti += aSmallSet(0x0000a0,0x0003ff) # 2byte
        ordinalChoicesMulti += aSmallSet(0x000400,0x0007ff) # 2byte
        ordinalChoicesMulti += aSmallSet(0x000800,0x003fff) # 3byte
        ordinalChoicesMulti += aSmallSet(0x004000,0x00ffff) # 3byte
        ordinalChoicesMulti += aSmallSet(0x010000,0x03ffff) # 3byte
        ordinalChoicesMulti += aSmallSet(0x040000,0x10ffff) # 4byte

ordinalChoices.remove(0x2c)
ordinalChoicesMulti.remove(0x2c)

def generate_random_utf8_string(length=1, multi=False, row=0, col=0):
    # want to handle more than 256 numbers
    cList = []
    for i in range(length):
        good = False
        while not good:
            r = random.choice(ordinalChoicesMulti if multi else ordinalChoices)
            illegal1 = (NO_COMMENT and row==1 and col==1 and i==0) and (r==0x40 or r==0x23) # @ is 0x40, # is 0x23
            illegal2 = (NO_QUOTE_BEGIN and i==0) and (r==0x22) # " is 0x22
            good = not (illegal1 or illegal2)

        # we sholdn't encode it here. Then we wouldn't have to decode it to unicode before writing.
        c = unichr(r).encode('utf-8')
        cList.append(c)
    # this is a random byte string now, of type string?
    return "".join(cList)

def write_syn_dataset(csvPathname, rowCount, colCount=1, scale=1,
        colSepChar=",", rowSepChar="\n", SEED=12345678):
    # always re-init with the same seed. 
    # that way the sequence of random choices from the enum list should stay the same for each call? 
    # But the enum list is randomized
    robj = random.Random(SEED)

    if UTF8 or UTF8_MULTIBYTE:
        dsf = codecs.open(csvPathname, encoding='utf-8', mode='w+')
    else:
        dsf = open(csvPathname, "w+")

    for row in range(rowCount):
        # add some robj choices here, to get more robjness over time with multiple test runs
        rowModulo = row % 1000000 # max range in this if/elif thing

        # only two compression schemes. Well 4
        # NewChunk: uncompressed
        # C1: up to 255
        # C2: up till we force NA
        # int?: when we flip to all NA
        if rowModulo < (100000 * scale):
            # 0 will always force NA, regardless of CAUSE_RANDOM_NA
            howManyEnumsToUse = robj.choice(
                [0 if not DISABLE_ALL_NA else 1, 1]) # zero will be all cause all NA
        elif rowModulo < (200000 * scale):
            howManyEnumsToUse = robj.choice([1,2,3])
        elif rowModulo < (300000 * scale):
            howManyEnumsToUse = robj.choice([4,5])
        elif rowModulo < (400000 * scale):
            howManyEnumsToUse = robj.choice([8,9])
        elif rowModulo < (500000 * scale):
            howManyEnumsToUse = robj.choice([15,16])
        elif rowModulo < (600000 * scale):
            howManyEnumsToUse = robj.choice([31,32])
        elif rowModulo < (700000 * scale):
            howManyEnumsToUse = robj.choice([63,64])
        elif rowModulo < (800000 * scale):
            howManyEnumsToUse = robj.choice([254,255,256,257, 10000])
        else:
            # some primes
            howManyEnumsToUse = robj.choice([1,2,4,8,16,256,10000])

        howManyEnumsToUseCol2 = robj.choice([0 if not DISABLE_ALL_NA else 1, 1, 3])

        rowData = []
        # keep a list of the enum indices used..return that for comparing multiple datasets
        # we only need to compare the last one..if it matches, then we probably did the right
        # thing with random seeds
        rowIndex = []
        # keep a sum of all the index mappings for the enum chosen (for the features in a row)
        # use this to calcuate a output (that's dependent on inputs in some repeatable way)
        riIndexSum = 0
        for col in range(colCount):
            # override this if col is col 2..force it to always be C1 compression
            if col==2:
                howManyEnumsToUse = howManyEnumsToUseCol2
            # put in a small number of NAs (1%)
            if not DISABLE_ALL_NA and (   
                    (CAUSE_RANDOM_NA and robj.randint(0,99)==0) or 
                    howManyEnumsToUse==0):
                rowData.append('')
            else:
                if howManyEnumsToUse >= 256 and UTF8_MULTIBYTE:
                    r = generate_random_utf8_string(length=1, multi=True, row=row, col=col)
                else:
                    r = generate_random_utf8_string(length=1, multi=False, row=row, col=col)
                rowData.append(r)

        rowDataCsv = colSepChar.join(rowData) + rowSepChar

        if UTF8 or UTF8_MULTIBYTE:
            # decode to unicode
            decoded = rowDataCsv.decode('utf-8')
            if DEBUG:
                # I suppose by having it encoded as utf, we can see the byte representation here?
                print "str:", repr(rowDataCsv), type(rowDataCsv)
                # this has the right length..multibyte utf8 are decoded 
                print "utf8:" , repr(decoded), type(decoded)
            dsf.write(decoded)
        else:
            dsf.write(rowDataCsv)

        
    dsf.close()
    # this is for comparing whether two datasets were generated identically 
    # (last row is essentially a checksum, given the use of random generator for prior rows)

    rowIndexCsv = colSepChar.join(map(str,rowIndex)) + rowSepChar
    return rowIndexCsv 

class Basic(unittest.TestCase):
    def tearDown(self):
        h2o.check_sandbox_for_errors()

    @classmethod
    def setUpClass(cls):
        global SEED
        SEED = h2o.setup_random_seed()
        h2o.init(3,java_heap_GB=3)

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()

    def test_parse_full_rand(self):
        SYNDATASETS_DIR = h2o.make_syn_dir()

        if DEBUG:
            n = 20
        else:
            n = 1000000

        # from command line arg -long
        if 1==0:
            repeat = 1000 
            scale = 10 # scale up the # of rows
            tryList = [
                (n*scale, 3, 'cI', 300), 
            ]
        else:
            repeat = 1
            scale = 1
            tryList = [
                (n, 3, 'cI', 300), 
            ]

        lastcolsHistory = []

        for r in range(repeat):
            SEED_PER_FILE = random.randint(0, sys.maxint)
            for (rowCount, colCount, hex_key, timeoutSecs) in tryList:
                # using the comma is nice to ensure no craziness
                colSepHexString = '2c' # comma
                colSepChar = colSepHexString.decode('hex')
                colSepInt = int(colSepHexString, base=16)
                print "colSepChar:", colSepChar

                rowSepHexString = '0a' # newline
                rowSepChar = rowSepHexString.decode('hex')
                print "rowSepChar:", rowSepChar

                csvFilename = 'syn_enums_' + str(rowCount) + 'x' + str(colCount) + '.csv'
                csvPathname = SYNDATASETS_DIR + '/' + csvFilename

                print "Creating random", csvPathname
                # same enum list/mapping, but different dataset?
                start = time.time()
                lastcols = write_syn_dataset(csvPathname, rowCount, colCount, scale=1,
                    colSepChar=colSepChar, rowSepChar=rowSepChar, SEED=SEED_PER_FILE)
                elapsed = time.time() - start
                print "took %s seconds to create %s" % (elapsed, csvPathname)
                # why are we saving this?
                lastcolsHistory.append(lastcols)

                parseResult = h2i.import_parse(path=csvPathname, schema='put', hex_key=hex_key, check_header=0,
                    timeoutSecs=60, separator=colSepInt, doSummary=DO_SUMMARY)
                
                inspect = h2o_cmd.runInspect(key=hex_key)
                missingList, labelList, numRows, numCols = h2o_cmd.infoFromInspect(inspect)

                # print "missingValuesList", missingValuesList
                # for mv in missingValuesList:
                #     self.assertAlmostEqual(mv, expectedNA, delta=0.1 * mv, 
                #        msg='mv %s is not approx. expected %s' % (mv, expectedNA))

                # might have extra rows
                if numRows < rowCount:
                    raise Exception("Expect numRows %s >= rowCount %s since we can have extra eols" % (numRows, rowCount))
                # numCols should be right?
                self.assertEqual(colCount, numCols)

                # (missingValuesDict, constantValuesDict, enumSizeDict, colTypeDict, colNameDict) = \
                #    h2o_cmd.columnInfoFromInspect(parseResult['destination_key'], 
                #    exceptionOnMissingValues=DISABLE_ALL_NA)

if __name__ == '__main__':
    h2o.unit_main()

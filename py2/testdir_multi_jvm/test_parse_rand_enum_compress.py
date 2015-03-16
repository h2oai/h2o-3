import unittest, random, sys, time, codecs
sys.path.extend(['.','..','../..','py'])
import h2o2 as h2o
import h2o_cmd, h2o_import as h2i, h2o_args

DEBUG = False
UTF8 = False
UTF8_MULTIBYTE = False

DO_WITH_INT = False

ENUMS_NUM = 20000
ENUMLIST = None
REPORT_LAST_ENUM_INDICES = False
DISABLE_ALL_NA = False
CAUSE_RANDOM_NA = True

CREATE_RESPONSE_COL = False
RESPONSE_MODULO = 2
DO_SUMMARY = False # summary slow for some reason

NO_NUL = True # the problem with NUL is covered by other tests. don't use
print "We have a problem starting a line with double quote and not ending it before EOL"
print "assume that is unlikely with the distribution being used"


def massageUTF8Choices(ordinalChoices):
    # ordinalChoices.remove(0x09) # is 9 bad..apparently can cause NA

    # if NO_NUL:
    #    ordinalChoices.remove(0x00) # nul
    # ordinalChoices.remove(0x01) # hiveseparator
    # ordinalChoices.remove(0x0d) # cr
    # ordinalChoices.remove(0x0a) # lf

    # smaller range, avoiding 0-1f control chars
    # ordinalChoices = range(0x20, 0x7f) # doesn't include last value
    ordinalChoices.remove(0x3b) # semicolon
    ordinalChoices.remove(0x20) # space
    ordinalChoices.remove(0x22) # double quote
    # ordinalChoices.remove(0x27) # apostrophe. should be legal if single quotes not enabled
    ordinalChoices.remove(0x2c) # comma

    # if we always have another non-digit in there, we don't need to remove digits? (assume doesn't match real)
    # we're not checking na counts anyhow
    if 1==0:
        ordinalChoices.remove(0x30) # 0
        ordinalChoices.remove(0x31) # 1
        ordinalChoices.remove(0x32) # 2
        ordinalChoices.remove(0x33) # 3
        ordinalChoices.remove(0x34) # 4
        ordinalChoices.remove(0x35) # 5
        ordinalChoices.remove(0x36) # 6
        ordinalChoices.remove(0x37) # 7
        ordinalChoices.remove(0x38) # 8
        ordinalChoices.remove(0x39) # 9

    # ordinalChoices.remove(0x7f) # ?? h2o-dev
    # print ordinalChoices

# h2o-dev doesn't like 0 thru 1d?
if UTF8:
    # what about multi-byte UTF8
    ordinalChoices = range(0x1e, 0x100) # doesn't include last value ..allow ff
else:  # ascii subset?
    ordinalChoices = range(0x1e, 0x80) # doesn't include last value ..allow 7f

if UTF8_MULTIBYTE:
    # 000000 - 00007f 1byte
    # 000080 - 00009f 2byte
    # 0000a0 - 0003ff 2byte
    # 000400 - 0007ff 2byte
    # 000800 - 003fff 3byte
    # 004000 - 00ffff 3byte
    # 010000 - 03ffff 3byte
    # 040000 - 10ffff 4byte
    # add some UTF8 multibyte, and restrict the choices to make sure we hit these

    def aSmallSet(a, b):
        return random.sample(range(a,b),10)

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
        # just sample 10 from each. 200+ from first
        # h2o-dev doesn't like 0 thru 1d in the jaon response?
        ordinalChoicesMulti  = range(0x00001e,0x00007f) # 1byte
        ordinalChoicesMulti += aSmallSet(0x000080,0x00009f) # 2byte
        ordinalChoicesMulti += aSmallSet(0x0000a0,0x0003ff) # 2byte
        ordinalChoicesMulti += aSmallSet(0x000400,0x0007ff) # 2byte
        ordinalChoicesMulti += aSmallSet(0x000800,0x003fff) # 3byte
        ordinalChoicesMulti += aSmallSet(0x004000,0x00ffff) # 3byte
        ordinalChoicesMulti += aSmallSet(0x010000,0x03ffff) # 3byte
        ordinalChoicesMulti += aSmallSet(0x040000,0x10ffff) # 4byte

    
if UTF8:
    massageUTF8Choices(ordinalChoices)

if UTF8_MULTIBYTE:
    massageUTF8Choices(ordinalChoicesMulti)

def generate_random_utf8_string(length=1, multi=False):
    # want to handle more than 256 numbers
    cList = []
    for i in range(length):
        # to go from hex 'string" to number
        # cint = int('fd9b', 16)
        r = random.choice(ordinalChoicesMulti if multi else ordinalChoices)
        # we sholdn't encode it here. Then we wouldn't have to decode it to unicode before writing.
        c = unichr(r).encode('utf-8')
        cList.append(c)
    # this is a random byte string now, of type string?
    return "".join(cList)

# use randChars for the random chars to use
def random_enum(randChars, maxEnumSize):
    choiceStr = randChars
    r = ''.join(random.choice(choiceStr) for x in range(maxEnumSize))
    return r

# this is for ascii only
def create_enum_list(randChars="abcdefghijklmnopqrstuvwxyz", maxEnumSize=4, listSize=10):
    if DO_WITH_INT:
        enumList = range(listSize)
    else:
        if ENUMLIST:
            enumList = ENUMLIST
        else:
            enumList = [random_enum(randChars, random.randint(2,maxEnumSize)) for i in range(listSize)]
    return enumList

def write_syn_dataset(csvPathname, enumList, rowCount, colCount=1, scale=1,
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

        # never try to use more enums then in the list
        if howManyEnumsToUse > len(enumList):
            print "WARNING: you should make ENUMS_NUM %s bigger than howManyEnumsToUse: %s" % \
                (ENUMS_NUM, howManyEnumsToUse)
            howManyEnumsToUse = len(enumList)

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
                riIndex = None
                riIndexSum += 0 # don't change
                rowData.append('')
            else:
                riIndex = robj.randint(0, howManyEnumsToUse-1)
                if CREATE_RESPONSE_COL:
                    riIndexSum += riIndex

                if UTF8 or UTF8_MULTIBYTE:
                    if howManyEnumsToUse >= 256 and UTF8_MULTIBYTE:
                        r = generate_random_utf8_string(length=1, multi=True)
                    else:
                        r = generate_random_utf8_string(length=1, multi=False)
                else:
                    r = enumList[riIndex]
                rowData.append(r)

                if REPORT_LAST_ENUM_INDICES:
                    rowIndex.append(r)

        # output column
        # make the output column match odd/even row mappings.
        # change...make it 1 if the sum of the enumList indices used is odd
        if CREATE_RESPONSE_COL:
            ri = riIndexSum % RESPONSE_MODULO
            rowData.append(ri)

        rowDataCsv = colSepChar.join(map(str,rowData)) + rowSepChar

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

    # will be empty if we didn't enable REPORT_LAST_ENUM_INDICES
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
            # force 2 jvms per host!

    @classmethod
    def tearDownClass(cls):
        ### time.sleep(3600)
        h2o.tear_down_cloud()

    def test_parse_rand_enum_compress(self):
        SYNDATASETS_DIR = h2o.make_syn_dir()

        if DEBUG:
            n = 20
        else:
            n = 1000000

        # from command line arg -long
        if h2o_args.long_test_case:
            repeat = 1000 
            scale = 10 # scale up the # of rows
            tryList = [
                (n*scale, 1, 'cI', 300), 
                (n*scale, 1, 'cI', 300), 
                (n*scale, 1, 'cI', 300), 
            ]
        else:
            repeat = 1
            scale = 1
            tryList = [
                (n, 3, 'cI', 300), 
                (n, 3, 'cI', 300), 
                (n, 3, 'cI', 300), 
            ]

        lastcolsHistory = []

        enumList = create_enum_list(listSize=ENUMS_NUM)

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
                lastcols = write_syn_dataset(csvPathname, enumList, rowCount, colCount, scale=1,
                    colSepChar=colSepChar, rowSepChar=rowSepChar, SEED=SEED_PER_FILE)
                elapsed = time.time() - start
                print "took %s seconds to create %s" % (elapsed, csvPathname)
                # why are we saving this?
                lastcolsHistory.append(lastcols)

                parseResult = h2i.import_parse(path=csvPathname, schema='put', hex_key=hex_key, check_header=0,
                    timeoutSecs=30, separator=colSepInt, doSummary=DO_SUMMARY)
                parseResultA = h2i.import_parse(path=csvPathname, schema='put', hex_key=hex_key)
                # optional. only needed to extract parse_key?
                pA = h2o_cmd.ParseObj(parseResultA, expectedNumRows=rowCount, expectedNumCols=colCount)
                print pA.numRows
                print pA.numCols
                print pA.parse_key
                # this guy can take json object as first thing, or re-read with key
                iA = h2o_cmd.InspectObj(pA.parse_key,
                    expectedNumRows=rowCount, expectedNumCols=colCount, expectedMissinglist=[])

                self.assertEqual(rowCount, iA.numRows)
                self.assertEqual(colCount, iA.numCols)

if __name__ == '__main__':
    h2o.unit_main()

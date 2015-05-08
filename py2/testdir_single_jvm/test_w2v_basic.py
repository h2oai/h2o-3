import unittest, time, sys, random, re
sys.path.extend(['.','..','../..','py'])
import h2o2 as h2o
import h2o_cmd, h2o_import as h2i, h2o_jobs
from h2o_test import verboseprint, dump_json, OutputObj
import h2o_test

DO_SUMMARY=False

targetList = ['red', 'mail', 'black flag', 5, 1981, 'central park', 
    'good', 'liquor store rooftoop', 'facebook']

lol = [
    ['red','orange','yellow','green','blue','indigo','violet'],
    ['male','female',''],
    ['bad brains','social distortion','the misfits','black flag',
        'iggy and the stooges','the dead kennedys',
        'the sex pistols','the ramones','the clash','green day'],
    range(1,10),
    range(1980,2013),
    ['central park','marin civic center','madison square garden',
     'wembley arena','greenwich village',
     'liquor store rooftop','"woodstock, n.y."','shea stadium'],
    ['good','bad'],
    ['expensive','cheap','free'],
    ['yes','no'],
    ['facebook','twitter','blog',''],
    range(8,100),
    [random.random() for i in range(20)]
]

whitespaceRegex = re.compile(r"""
    ^\s*$     # begin, white space or empty space, end
    """, re.VERBOSE)

DO_TEN_INTEGERS = False
def random_enum(n):
    # pick randomly from a list pointed at by N
    if DO_TEN_INTEGERS:
        # ten choices
        return str(random.randint(0,9))
    else:
        choiceList = lol[n]
        r = str(random.choice(choiceList))
        if r in targetList:
            t = 1
        else:
            t = 0
        # need more randomness to get enums to be strings
        r2 = random.randint(0, 10000)
        return (t,"%s_%s" % (r, r2))

def write_syn_dataset(csvPathname, rowCount, colCount=1, SEED='12345678',
        colSepChar=",", rowSepChar="\n"):
    r1 = random.Random(SEED)
    dsf = open(csvPathname, "w+")
    for row in range(rowCount):
        # doesn't guarantee that 10000 rows have 10000 unique enums in a column
        # essentially sampling with replacement
        rowData = []
        lenLol = len(lol)
        targetSum = 0
        for col in range(colCount):
            (t,ri) = random_enum(col % lenLol)
            targetSum += t # sum up contributions to output choice
            # print ri
            # first two rows can't tolerate single/double quote randomly
            # keep trying until you get one with no single or double quote in the line
            if row < 2:
                while True:
                    # can't have solely white space cols either in the first two rows
                    if "'" in ri or '"' in ri or whitespaceRegex.match(ri):
                        (t,ri) = random_enum(col % lenLol)
                    else:
                        break

            rowData.append(ri)

        # output column
        avg = (targetSum+0.0)/colCount
        # ri = r1.randint(0,1)
        rowData.append(targetSum)

        # use the new Hive separator
        rowDataCsv = colSepChar.join(map(str,rowData)) + rowSepChar
        ### sys.stdout.write(rowDataCsv)
        dsf.write(rowDataCsv)
    dsf.close()

#*******************************

def create_file_with_seps(rowCount, colCount):
    # can randomly pick the row and col cases.
    ### colSepCase = random.randint(0,1)
    colSepCase = 1
    # using the comma is nice to ensure no craziness
    if (colSepCase==0):
        colSepHexString = '01'
    else:
        colSepHexString = '2c' # comma

    colSepChar = colSepHexString.decode('hex')
    colSepInt = int(colSepHexString, base=16)
    print "colSepChar:", colSepChar
    print "colSepInt", colSepInt

    rowSepCase = random.randint(0,1)
    # using this instead, makes the file, 'row-readable' in an editor
    if (rowSepCase==0):
        rowSepHexString = '0a' # newline
    else:
        rowSepHexString = '0d0a' # cr + newline (windows) \r\n

    rowSepChar = rowSepHexString.decode('hex')
    print "rowSepChar:", rowSepChar

    SEEDPERFILE = random.randint(0, sys.maxint)
    if DO_TEN_INTEGERS:
        csvFilename = 'syn_int10_' + str(rowCount) + 'x' + str(colCount) + '.csv'
    else:
        csvFilename = 'syn_enums_' + str(rowCount) + 'x' + str(colCount) + '.csv'
    csvPathname = SYNDATASETS_DIR + '/' + csvFilename

    print "Creating random", csvPathname
    write_syn_dataset(csvPathname, rowCount, colCount, SEEDPERFILE,
        colSepChar=colSepChar, rowSepChar=rowSepChar)
    
    return csvPathname


class Basic(unittest.TestCase):
    def tearDown(self):
        h2o.check_sandbox_for_errors()

    @classmethod
    def setUpClass(cls):
        global SEED
        SEED = h2o.setup_random_seed()

        h2o.init(1, java_heap_GB=12)

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()

    def test_w2v_basic(self):
        global SYNDATASETS_DIR
        SYNDATASETS_DIR = h2o.make_syn_dir()
        n = 500000
        tryList = [
            (n, 1, 'cD', 300),
            (n, 2, 'cE', 300),
            (n, 3, 'cF', 300),
            (n, 4, 'cG', 300),
            (n, 5, 'cH', 300),
            (n, 6, 'cI', 300),
            (n, 7, 'cJ', 300),
            (n, 9, 'cK', 300),
        ]

        ### h2b.browseTheCloud()
        for (rowCount, colCount, hex_key, timeoutSecs) in tryList:

            csvPathname = create_file_with_seps(rowCount, colCount)

            # just parse to make sure it's good
            parseResult = h2i.import_parse(path=csvPathname, 
                check_header=1, delete_on_done = 0, timeoutSecs=180, doSummary=False)
            pA = h2o_cmd.ParseObj(parseResult)
            iA = h2o_cmd.InspectObj(pA.parse_key)
            cA = h2o_test.OutputObj(iA.columns[0], "inspect_column")

            parse_key = pA.parse_key
            numRows = iA.numRows
            numCols = iA.numCols
            labelList = iA.labelList

            for i in range(colCount):
                print cA.type, cA.missing_count
                self.assertEqual(0, cA.missing_count, "Column %s Expected %s. missing: %s is incorrect" % (i, 0, cA.missing_count))
                self.assertEqual('string', cA.type, "Column %s Expected %s. type: %s is incorrect" % (i, 0, cA.type))

            if DO_SUMMARY:
                for i in range(colCount):
                    co = h2o_cmd.runSummary(key=parse_key, column=i)
                    print co.label, co.type, co.missing, co.domain, sum(co.bins)
                    self.assertEqual(0, co.missing_count, "Column %s Expected %s. missing: %s is incorrect" % (i, 0, co.missing_count))
                    self.assertEqual('String', co.type, "Column %s Expected %s. type: %s is incorrect" % (i, 0, co.type))


            # no cols ignored
            labelListUsed = list(labelList)
            numColsUsed = numCols
            for trial in range(1):

                parameters = {
                    'validation_frame': parse_key, # KeyIndexed False []
                    'ignored_columns': None, # string[] None []

                    'minWordFreq': 5, # int 5 []
                    'wordModel': 'SkipGram', # enum [u'CBOW', u'SkipGram']
                    'normModel': 'HSM', # enum # [u'HSM', u'NegSampling']
                    'negSampleCnt': 5,# int 5 []
                    'vecSize': 100,  # int 100
                    'windowSize': 5,  # int 5
                    'sentSampleRate': 0.001,  # float 0.001
                    'initLearningRate': 0.05,  # float 0.05
                    'epochs': 1, # int 5
                }

                model_key = 'benign_w2v.hex'
                bmResult = h2o.n0.build_model(
                    algo='word2vec', 
                    model_id=model_key,
                    training_frame=parse_key,
                    parameters=parameters, 
                    timeoutSecs=60) 
                bm = OutputObj(bmResult, 'bm')

                modelResult = h2o.n0.models(key=model_key)
                model = OutputObj(modelResult['models'][0]['output'], 'model')

                cmmResult = h2o.n0.compute_model_metrics( model=model_key, frame=parse_key, timeoutSecs=60)
                cmm = OutputObj(cmmResult, 'cmm')

                mmResult = h2o.n0.model_metrics(model=model_key, frame=parse_key, timeoutSecs=60)
                mm = OutputObj(mmResult['model_metrics'][0], 'mm')

                # not implemented?

                # prResult = h2o.n0.predict(model=model_key, frame=parse_key, timeoutSecs=60)
                # pr = OutputObj(prResult['model_metrics'][0]['predictions'], 'pr')
        
                h2o_cmd.runStoreView()

if __name__ == '__main__':
    h2o.unit_main()

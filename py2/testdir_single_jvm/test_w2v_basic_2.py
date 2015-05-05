import unittest, time, sys, random, re
sys.path.extend(['.','..','../..','py'])
import h2o2 as h2o
import h2o_cmd, h2o_import as h2i, h2o_jobs
from h2o_test import verboseprint, dump_json, OutputObj

print "This variant plays with colsep of space, and eols, more"

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
        return (t,r)

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
    colSepCase = 2
    # using the comma is nice to ensure no craziness
    if colSepCase==0:
        colSepHexString = '01'
    elif colSepCase==1:
        colSepHexString = '2c' # comma
    else:
        colSepHexString = '20' # space

    colSepChar = colSepHexString.decode('hex')
    colSepInt = int(colSepHexString, base=16)
    print "colSepChar:", colSepChar
    print "colSepInt", colSepInt

    # rowSepCase = random.randint(0,1)
    rowSepCase = 0
    # using this instead, makes the file, 'row-readable' in an editor
    if rowSepCase==0:
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

        h2o.init(1)

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()

    def test_w2v_basic_2(self):
        global SYNDATASETS_DIR
        SYNDATASETS_DIR = h2o.make_syn_dir()
        n = 100
        tryList = [
            # (n, 1, 'cD', 300),
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
            hex_key = "not_used.hex"

            # just parse to make sure it's good
            parseResult = h2i.import_parse(path=csvPathname,
                check_header=1, delete_on_done = 0, timeoutSecs=180, doSummary=False)
            pA = h2o_cmd.ParseObj(parseResult)
            iA = h2o_cmd.InspectObj(pA.parse_key)
            parse_key = pA.parse_key
            numRows = iA.numRows
            numCols = iA.numCols
            labelList = iA.labelList

            src_key = h2i.find_key('syn_.*csv')

            # no cols ignored
            labelListUsed = list(labelList)
            numColsUsed = numCols
            for trial in range(1):

                parameters = {
                    'validation_frame': parse_key, # KeyIndexed False []
                    'ignored_columns': None, # string[] None []

                    'minWordFreq': 1, # int 5 []
                    'wordModel': 'CBOW', # enum [u'CBOW', u'SkipGram']
                    'normModel': 'NegSampling', # enum # [u'HSM', u'NegSampling']
                    'negSampleCnt': 1,# int 5 []
                    'vecSize': 10,  # int 100
                    'windowSize': 2,  # int 5
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
                    timeoutSecs=10) 
                bm = OutputObj(bmResult, 'bm')

                modelResult = h2o.n0.models(key=model_key)
                model = OutputObj(modelResult['models'][0]['output'], 'model')

                cmmResult = h2o.n0.compute_model_metrics( model=model_key, frame=parse_key, timeoutSecs=60)
                cmm = OutputObj(cmmResult, 'cmm')

                mmResult = h2o.n0.model_metrics(model=model_key, frame=parse_key, timeoutSecs=60)
                mm = OutputObj(mmResult['model_metrics'][0], 'mm')

                prResult = h2o.n0.predict(model=model_key, frame=parse_key, timeoutSecs=60)
                pr = OutputObj(prResult['model_metrics'][0]['predictions'], 'pr')
        
                h2o_cmd.runStoreView()

if __name__ == '__main__':
    h2o.unit_main()

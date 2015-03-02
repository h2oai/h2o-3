import unittest, random, sys, time, re, getpass
sys.path.extend(['.','..','../..','py'])
import h2o2 as h2o
import h2o_cmd, h2o_browse as h2b, h2o_import as h2i, h2o_glm, h2o_util
import h2o_print as h2p, h2o_gbm
from h2o_xl import Key, Fcn, Assign, Cbind

# FIX! This has some abbreviated stuff from h2o...look back there for completeness, eventually
DO_QUANTILE = False

# details:
# we want to seed a random dictionary for our enums
# string.ascii_uppercase string.printable string.letters string.digits string.punctuation string.whitespace
# restricting the choices makes it easier to find the bad cases
randChars = "abeE01" + "$%+-.;|\t "
randChars = "abeE01" # bad..causes NAification. probably 1E0e is causing a problem
# randChars = "abfF01" # try this.. fails
# randChars = "abcdef" #
quoteChars = "\'\""
# don't use any quote characters. We'd have to protect combinations
quoteChars = ""
MIN_ENUM_WIDTH = 2
MAX_ENUM_WIDTH = 8
RAND_ENUM_LENGTH = True
# CUT_EXPR_CNT = 200
CUT_EXPR_CNT = 20

# ROWS=1000000
ROWS=10000
# ROWS=100

DO_PLOT = getpass.getuser()=='kevin'

DO_MEDIAN = True
MAX_QBINS = 1000
MULTI_PASS = 1

def random_enum(n, randChars=randChars, quoteChars=quoteChars):
    # randomly return None 10% of the time
    # if random.randint(0,9)==0:
    #    return 'huh' # empty string doesn't work for exec compare?

    choiceStr = randChars + quoteChars
    mightBeNumberOrWhite = True
    while mightBeNumberOrWhite:
        # H2O doesn't seem to tolerate random single or double quote in the first two rows.
        # disallow that by not passing quoteChars for the first two rows (in call to here)
        r = ''.join(random.choice(choiceStr) for x in range(n))
        mightBeNumberOrWhite = h2o_util.might_h2o_think_number_or_whitespace(r)

    return r

def create_enum_list(n=4, **kwargs):
    # Allowing length one, we sometimes form single digit numbers that cause the whole column to NA
    # see DparseTask.java for this effect
    # FIX! if we allow 0, then we allow NA?. I guess we check for no missing, so can't allow NA
    # too many retries allowing 1. try 2 min.
    if RAND_ENUM_LENGTH:
        enumList = [random_enum(n=random.randint(MIN_ENUM_WIDTH, MAX_ENUM_WIDTH), **kwargs) for i in range(n)]
    else:
        # a fixed width is sometimes good for finding badness
        enumList = [random_enum(n=MAX_ENUM_WIDTH, **kwargs) for i in range(n)]
    return enumList

def create_col_enum_list(inCount):
    # create the per-column choice lists
    colEnumList = []
    for col in range(inCount):
        enumList = create_enum_list(n=random.randint(1,4), quoteChars=quoteChars)
        colEnumList.append(enumList)
    return colEnumList
    

def write_syn_dataset(csvPathname, rowCount, inCount=1, outCount=1, SEED='12345678', 
        colSepChar=",", rowSepChar="\n", quoteChars="", colEnumList=None):
    r1 = random.Random(SEED)

    dsf = open(csvPathname, "w+")
    for row in range(rowCount):
        # doesn't guarantee that 10000 rows have 10000 unique enums in a column
        # essentially sampling with replacement
        rowData = []
        for iCol in range(inCount):
            # FIX! we should add some random NA?
            ri = random.choice(colEnumList[iCol])
            rowData.append(ri)

        # output columns. always 0-10e6 with 2 digits of fp precision
        for oCol in range(outCount):
            ri = "%.2f" % random.uniform(0, 10e6)
            rowData.append(ri)

        # use the new Hive separator
        rowDataCsv = colSepChar.join(map(str,rowData)) + rowSepChar
        ### sys.stdout.write(rowDataCsv)
        dsf.write(rowDataCsv)
    dsf.close()
    return colEnumList


class Basic(unittest.TestCase):
    def tearDown(self):
        h2o.check_sandbox_for_errors()

    @classmethod
    def setUpClass(cls):
        global SEED
        SEED = h2o.setup_random_seed()
        h2o.init(java_heap_GB=14)

    @classmethod
    def tearDownClass(cls):
        # h2o.sleep(3600)
        h2o.tear_down_cloud()

    def test_exec2_enums_rand_cut(self):
        SYNDATASETS_DIR = h2o.make_syn_dir()

        n = ROWS
        tryList = [
            (n, 10, 9, 'cE', 300), 
            ]

        # create key names to use for exec
        eKeys = ['e%s' % i for i in range(10)]

        # h2b.browseTheCloud()
        trial = 0
        for (rowCount, iColCount, oColCount, hex_key, timeoutSecs) in tryList:
            colCount = iColCount + oColCount

            hex_key = 'p'
            colEnumList = create_col_enum_list(iColCount)

            # create 100 possible cut expressions here, so we don't waste time below
            rowExprList = []
            print "Creating", CUT_EXPR_CNT, 'cut expressions'
            for j in range(CUT_EXPR_CNT):
                # init cutValue. None means no compare
                cutValue = [None for i in range(iColCount)]
                # build up a random cut expression
                cols = random.sample(range(iColCount), random.randint(1,iColCount))
                for c in cols:
                    # possible choices within the column
                    cel = colEnumList[c]
                    # for now the cutValues are numbers for the enum mappings

                    # FIX! hack. don't use encoding 0, maps to NA here? h2o doesn't like
                    # celChoice = str(random.choice(range(len(cel))))
                    celChoice = random.choice(range(len(cel)))
                    cutValue[c] = celChoice
    
                cutExprList = []

                pKey = Key('p')
                for i,c in enumerate(cutValue):
                    if c is None:   
                        continue
                    else:
                        # new ...ability to reference cols
                        # src[ src$age<17 && src$zip=95120 && ... , ]
                        # cutExprList.append('p$C'+str(i+1)+'=='+c)
                        # all column indexing in h2o-dev is with number
                        e = Fcn('==', c, pKey[:,i])
                        cutExprList.append(e)

                cutExpr = None
                for ce in cutExprList:
                    if cutExpr:
                        cutExpr = Fcn('&', cutExpr, ce)
                    else: 
                        cutExpr = ce

                print "cutExpr:", cutExpr    

                # should be two different keys in the sample
                e = random.sample(eKeys,2)
                fKey = e[0]
                eKey = e[1]

                # rowExpr = '%s[%s,];' % (hex_key, cutExpr)
                hKey = Key(hex_key)
                rowExpr = hKey[cutExpr, :]

                print "rowExpr:", rowExpr
                rowExprList.append(rowExpr)


            # CREATE DATASET*******************************************
            SEEDPERFILE = random.randint(0, sys.maxint)
            csvFilename = 'syn_enums_' + str(rowCount) + 'x' + str(colCount) + '.csv'
            csvPathname = SYNDATASETS_DIR + '/' + csvFilename

            print "Creating random", csvPathname
            write_syn_dataset(csvPathname, rowCount, iColCount, oColCount, SEEDPERFILE, colEnumList=colEnumList)

            # PARSE*******************************************************
            parseResult = h2i.import_parse(path=csvPathname, schema='put', hex_key=hex_key, timeoutSecs=30)
            numRows, numCols, parse_key = h2o_cmd.infoFromParse(parseResult)

            inspect = h2o_cmd.runInspect(key=parse_key)
            missingList, valueList, numRows, numCols = h2o_cmd.infoFromInspect(inspect)
            # print h2o.dump_json(inspect)

            # (missingValuesDict, constantValuesDict, enumSizeDict, colTypeDict, colNameDict) = \
            #    h2o_cmd.columnInfoFromInspect(parse_key, exceptionOnMissingValues=False)

            # error if any col has constant values
            # if len(constantValuesDict) != 0:
            #    raise Exception("Probably got a col NA'ed and constant values as a result %s" % constantValuesDict)

            # INIT all possible key names used***************************
            # remember. 1 indexing!

            # build up the columns
            Assign('b', [1,2,3])
            # could also append 1 col at a time, by assigning to the next col number?
            Assign('a', Cbind(['b' for i in range(colCount)]))
            
            for eKey in eKeys:
                Assign(eKey, 'a')
                ## print h2o.dump_json(e)

            xList = []
            eList = []
            fList = []
            for repeat in range(200):
                # EXEC*******************************************************
                # don't use exec_expr to avoid issues with Inspect following etc.
                randICol = random.randint(0,iColCount-1)
                randOCol = random.randint(iColCount, iColCount+oColCount-1)

                # should be two different keys in the sample
                e = random.sample(eKeys,2)
                fKey = e[0]
                eKey = e[1]

                if 1==1:
                    start = time.time()
                    Assign(fKey, random.choice(rowExprList)).do()
                    elapsed = time.time() - start
                    execTime = elapsed
                    print "exec 2 took", elapsed, "seconds."
                
                    inspect = h2o_cmd.runInspect(key=fKey)
                    missingList, valueList, numRows, numCols = h2o_cmd.infoFromInspect(inspect)

                if numRows==0 or numCols!=colCount:
                    h2p.red_print("Warning: Cut resulted in", numRows, "rows and", numCols, "cols. Quantile will abort")

                # FIX! put quantile back in?
                quantileTime = 0

                # remove all keys*******************************************************
                # what about hex_key?
                if 1==0:
                    start = time.time()
                    h2o.nodes[0].remove_all_keys()
                    elapsed = time.time() - start
                    print "remove all keys end on ", csvFilename, 'took', elapsed, 'seconds.'

                trial += 1
                xList.append(trial)
                eList.append(execTime)
                fList.append(quantileTime)


        # just get a plot of the last one (biggest)
        if DO_PLOT:
            xLabel = 'trial'
            eLabel = 'exec cut time'
            fLabel = 'quantile time'
            eListTitle = ""
            fListTitle = ""
            h2o_gbm.plotLists(xList, xLabel, eListTitle, eList, eLabel, fListTitle, fList, fLabel)



if __name__ == '__main__':
    h2o.unit_main()

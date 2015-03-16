import unittest, random, time, sys
sys.path.extend(['.','..','../..','py'])
import h2o2 as h2o
import h2o_cmd, h2o_browse as h2b, h2o_import as h2i, h2o_util

print "have to add leading/trailing whitespace and single/double quotes?"

# A time token is built up from the definition of the time subtokens
# A time token can have single quote/double quote/white space like any 
# other string/number token, and is stripped of that before parsing.
# # I'd like to see this rule met. Not sure if it's possible.
# If the type for the column is guessed to be Time, 
# then anything that doesn't match the time token definition must be NA'ed by h2o.
# 
# 
# In all cases where there are multiple integer digits, leading zeroes can be present or individually dropped.
# This could be one or two leading zeroes in some cases. Dropping all zeroes to create nothing, is not legal.
# 
# dd :  two digit day, from 00 to 31. Is there any checking for the day being legal for the particular month and year?
# MMM: three character month. Legal:
#     months = [
#         ['Jan', 'JAN', 'jan'],
#         ['Feb', 'FEB', 'feb'],
#         ['Mar', 'MAR', 'mar'],
#         ['Apr', 'APR', 'apr'],
#         ['May', 'MAY', 'may'],
#         ['Jun', 'JUN', 'jun'],
#         ['Jul', 'JUL', 'jul'],
#         ['Aug', 'AUG', 'aug'],
#         ['Sep', 'SEP', 'sep'],
#         ['Oct', 'OCT', 'oct'],
#         ['Nov', 'NOV', 'nov'],
#         ['Dec', 'DEC', 'dec']
#         ]
# 
# yy: two digit year, from 00 to 99.
# MM: two digit month, from 00 to 12.
# HH: two digit hour, from 00 to 23.
# MM: two digit minute, from 00 to 59.
# SS: two digit second, from 00 to 59.
# SSS: three digit millisecond, from 000 to 999. (note here that one or two leading zeroes can be dropped).
# 
# Subtokens can then be combined in these 4 formats. Note the "-", ":" or "." in the particular format is never optional.
# Anything that doesn't match these formats, or has a subtoken that doesn't match the legal cases, should be NA'ed.
# 
# dd-MMM-yy
# yyyy-MM-dd
# yyyy-MM-dd HH:mm:ss
# yyyy-MM-dd HH:mm:ss.SSSS
 
print "test dd-MMM-yy format. caps for month?"
print "apparently h2o NAs some cases. illegal dates in a month?"
print "seems to be that we require leading zero in year, but it's okay to not have it in the date?"

ROWS = 5
COLS = 20
RESTRICT_TO_28 = False
RESTRICT_MONTH_TO_UPPER = True

if RESTRICT_MONTH_TO_UPPER:
    months = [
        ['nullForZero'],
        ['JAN'],
        ['FEB'],
        ['MAR'],
        ['APR'],
        ['MAY'],
        ['JUN'],
        ['JUL'],
        ['AUG'],
        ['SEP'],
        ['OCT'],
        ['NOV'],
        ['DEC']
        ]
else:
    months = [
        ['nullForZero'],
        ['Jan', 'JAN', 'jan'],
        ['Feb', 'FEB', 'feb'],
        ['Mar', 'MAR', 'mar'],
        ['Apr', 'APR', 'apr'],
        ['May', 'MAY', 'may'],
        ['Jun', 'JUN', 'jun'],
        ['Jul', 'JUL', 'jul'],
        ['Aug', 'AUG', 'aug'],
        ['Sep', 'SEP', 'sep'],
        ['Oct', 'OCT', 'oct'],
        ['Nov', 'NOV', 'nov'],
        ['Dec', 'DEC', 'dec']
        ]

# increase weight for Feb
monthWeights = [1 if i!=1 else 5 for i in range(len(months))]

if RESTRICT_TO_28:
    days = map(str, range(1,29))
else:
    days = map(str, range(1,32))

# increase weight for picking near end of month
dayWeights = [1 if i<27 else 8 for i in range(len(days))]


def getRandomTimeStamp():
    # assume leading zero is option
    day = days[h2o_util.weighted_choice(dayWeights)]
    # may or may not leading zero fill the day
    # if random.randint(0,1) == 1:
    #     day = day.zfill(2) 

    # yy year
    timestampFormat = random.randint(0,5)
    timestampFormat = 0
    # always 4 digit
    yearInt = random.randint(1970, 2016)
    yearStr = str(yearInt)
    if timestampFormat==0:
        # may or may not leading zero fill the year
        if random.randint(0,1) == 1:
            if str(yearStr[-2])=='0':
                # drop the leading zero
                year = int(str(yearStr)[-1:])
            else:
                # keep leading zzero
                year = int(str(yearStr)[-2:])
        else:
            # last two digits. (always zero filled)
            year = int(str(yearStr)[-2:])

    # yyyy year
    else:
        year = yearInt


    if timestampFormat==0:
        # once we pick the month, we have to pick from the choices for the name of the month
        # monthIndex = range(1,13)[h2o_util.weighted_choice(monthWeights)]
        monthIndex = random.randint(1,12)
        month = random.choice(months[monthIndex])
    else:
        month = str(random.randint(1,12))
        # may or may not leading zero fill the month
        # if random.randint(0,1) == 1:
        #     month = month.zfill(2) 

    # use calendar to make sure the day is legal for that month/year
    import calendar
    legalDays = calendar.monthrange(yearInt, monthIndex)[1]
    if day > legalDays:
        day = legalDays

    # may or may not leading zero fill the hour
    hour = str(random.randint(0,23))
    if random.randint(0,1) == 1:
        hour = hour.zfill(2) 

    minute = str(random.randint(0,59))
    if random.randint(0,1) == 1:
        minute = minute.zfill(2) 

    second = str(random.randint(0,59))
    if random.randint(0,1) == 1:
        second = second.zfill(2) 

    milli = str(random.randint(0,999))
    # can be zero filled to 2 if <= 99
    r = random.randint(0,2) == 1
    if r==1:
        milli = milli.zfill(2) 
    elif r==2:
        milli = milli.zfill(3) 
    
    # "dd-MMM-yy" 
    # "yyyy-MM-dd", 
    # "yyyy-MM-dd HH:mm:ss" };
    # "yyyy-MM-dd HH:mm:ss.SSS", 

    if timestampFormat==0:
        a  = "%s-%s-%02d" % (day, month, year)
    elif timestampFormat==1:
        a  = "%04d-%s-%s" % (year, month, day)
    elif timestampFormat==2:
        a  = "%04d-%s-%s %s:%s:%s" % (year, month, day, hour, minute, second)
    # elif timestampFormat==3:
    else:
        a  = "%04d-%s-%s %s:%s:%s:%s" % (year, month, day, hour, minute, second, milli)
    return a

def rand_rowData(colCount=6):
    a = [getRandomTimeStamp() for fields in range(colCount)]
    # put a little white space in!
    b = ", ".join(map(str,a))
    return b

def write_syn_dataset(csvPathname, rowCount, colCount, headerData=None, rowData=None):
    dsf = open(csvPathname, "w+")
    if headerData is not None:
        dsf.write(headerData + "\n")
    for i in range(rowCount):
        rowData = rand_rowData(colCount)
        dsf.write(rowData + "\n")
    dsf.close()

class Basic(unittest.TestCase):
    def tearDown(self):
        h2o.check_sandbox_for_errors()

    @classmethod
    def setUpClass(cls):
        global SEED
        SEED = h2o.setup_random_seed()
        h2o.init(1,java_heap_GB=1,use_flatfile=True)

    @classmethod
    def tearDownClass(cls):
        ### time.sleep(3600)
        h2o.tear_down_cloud(h2o.nodes)
    
    def test_parse_time(self):
        SYNDATASETS_DIR = h2o.make_syn_dir()
        csvFilename = "syn_time.csv"
        csvPathname = SYNDATASETS_DIR + '/' + csvFilename

        headerData = None
        colCount = COLS
        # rowCount = 1000
        rowCount = ROWS
        write_syn_dataset(csvPathname, rowCount, colCount, headerData)
        
        for trial in range (20):
            rowData = rand_rowData()
            # make sure all key names are unique, when we re-put and re-parse (h2o caching issues)
            # src_key = csvFilename + "_" + str(trial)
            hex_key = csvFilename + "_" + str(trial) + ".hex"
            parseResultA = h2i.import_parse(path=csvPathname, schema='put', hex_key=hex_key)
            print "A trial #", trial
            # optional. only needed to extract parse_key?
            pA = h2o_cmd.ParseObj(parseResultA, expectedNumRows=rowCount, expectedNumCols=colCount)
            print pA.numRows
            print pA.numCols
            print pA.parse_key
            # this guy can take json object as first thing, or re-read with key
            iA = h2o_cmd.InspectObj(pA.parse_key,
                expectedNumRows=rowCount, expectedNumCols=colCount, expectedMissinglist=[])

            csvDownloadPathname = SYNDATASETS_DIR + "/csvDownload.csv"
            h2o.nodes[0].csv_download(key=pA.parse_key, csvPathname=csvDownloadPathname)

            # do a little testing of saving the key as a csv
            # remove the original parsed key. source was already removed by h2o
            if 1==0:
                h2o.nodes[0].remove_key(pA.parse_key)

            # interesting. what happens when we do csv download with time data?
            parseResultB = h2i.import_parse(path=csvDownloadPathname, schema='put', hex_key=hex_key)
            print "B trial #", trial
            pB = h2o_cmd.ParseObj(parseResultB, expectedNumRows=rowCount, expectedNumCols=colCount)
            print pB.numRows
            print pB.numCols
            print pB.parse_key
            iB = h2o_cmd.InspectObj(pB.parse_key,
                expectedNumRows=rowCount, expectedNumCols=colCount, expectedMissinglist=[])

            # these checks are redundant now
            self.assertEqual(iA.missingList, iB.missingList,
                "missingValuesList mismatches after re-parse of downloadCsv result")
            self.assertEqual(iA.numCols, iB.numCols,
                "numCols mismatches after re-parse of downloadCsv result")
            # H2O adds a header to the csv created. It puts quotes around the col numbers if no header
            # so I guess that's okay. So allow for an extra row here.
            self.assertEqual(iA.numRows, iB.numRows,
                "pA.numRows: %s pB.numRows: %s mismatch after re-parse of downloadCsv result" % \
                (iA.numRows, iB.numRows) )
            print "H2O writes the internal format (number) out for time."

            # ==> syn_time.csv <==
            # 31-Oct-49, 25-NOV-10, 08-MAR-44, 23-Nov-34, 19-Feb-96, 23-JUN-30
            # 31-Oct-49, 25-NOV-10, 08-MAR-44, 23-Nov-34, 19-Feb-96, 23-JUN-30

            # ==> csvDownload.csv <==
            # "0","1","2","3","4","5"
            # 2.5219584E12,1.293264E12,2.3437116E12,2.0504736E12,3.9829788E12,1.9110204E12

            h2o.check_sandbox_for_errors()

if __name__ == '__main__':
    h2o.unit_main()

    



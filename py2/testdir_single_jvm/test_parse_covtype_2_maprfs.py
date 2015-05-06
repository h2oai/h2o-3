import unittest, sys
sys.path.extend(['.','..','../..','py'])

import h2o2 as h2o
import h2o_cmd, h2o_import as h2i, h2o_browse as h2b
from h2o_test import find_file, dump_json, verboseprint
import time

def assertEqualMsg(a, b): assert a == b, "%s %s" % (a, b)

def parseKeyIndexedCheck(frames_result, multiplyExpected):
    # get the name of the frame?
    print ""
    print "Checking Frame.json"
    frame = frames_result['frames'][0]
    rows = frame['rows']
    columns = frame['columns']
    for i,c in enumerate(columns):
        label = c['label']
        stype = c['type']
        missing = c['missing_count']
        zeros = c['zero_count']
        domain = c['domain']
        print "column: %s label: %s type: %s missing: %s zeros: %s domain: %s" %\
            (i,label,stype,missing,zeros,domain)

        # files are concats of covtype. so multiply expected
        # assertEqualMsg(label,"C%s" % (i+1))

class Basic(unittest.TestCase):
    def tearDown(self):
        h2o.check_sandbox_for_errors()

    @classmethod
    def setUpClass(cls):
        h2o.init(1,
            enable_benchmark_log=True,
            use_maprfs=True,
            hdfs_version='mapr3.1.1',
            hdfs_name_node='mr-0x2:7222')
        # mayb these aren't set correctly with -uc and above,. Let's just set them here
        # the import below will use them to form the uri
        h2o.nodes[0].use_maprfs = True
        h2o.nodes[0].use_hdfs = False
        h2o.nodes[0].hdfs_version = 'mapr3.1.1',
        h2o.nodes[0].hdfs_name_node = 'mr-0x2:7222'


    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()

    def test_parse_covtype_2_maprfs(self):
        csvFilenameAll = [
            # this was from a hdfs dfs -ls /datasets. ..bytes
            ("covtype.data", 75169317),
            ("TEST-poker1000.csv", 23582),
            ("WU_100KRows3KCols.csv", 1120591148),
            ("airlines_all.05p.csv", 607774430),
            ("and-testing.data", 23538333),
            ("arcene2_train.both", 2715738),
            ("arcene_train.both", 2715838),
            # ("bestbuy_test.csv", 152488777),
            # ("bestbuy_train.csv", 243806953),
            ("billion_rows.csv.gz", 1758523515),
            ("covtype.13x.data", 977210917),
            ("covtype.13x.shuffle.data", 977210917),
            ("covtype.4x.shuffle.data", 300678693),
            ("covtype4x.shuffle.data", 300678693),
            ("hhp.unbalanced.012.1x11.data.gz", 6566953),
            ("hhp.unbalanced.012.data.gz", 4233715),
            ("hhp.unbalanced.data.gz", 4235293),
            ("hhp2.os.noisy.0_1.data", 48381802),
            ("hhp2.os.noisy.9_4.data", 48397103),
            ("leads.csv", 2755),
            ("prostate_long_1G.csv", 1115287100),
            # ("3G_poker_shuffle", 3145728000),
            # ("covtype.169x.data", 12703751717),
        ]

        # pick 8 randomly!
        if (1==0):
            csvFilenameList = random.sample(csvFilenameAll,8)
        # Alternatively: do the list in order! Note the order is easy to hard
        else:
            csvFilenameList = csvFilenameAll

        for (csvFilename, totalBytes) in csvFilenameList:
            totalBytes = float(totalBytes)
            timeoutSecs = 900
            multiplyExpected = 1

            # import_result = a_node.import_files(path=find_file("smalldata/logreg/prostate.csv"))
            importFolderPath = "datasets"
            csvPathname = importFolderPath + "/" + csvFilename

            start = time.time()
            parseResult  = h2i.import_parse(path=csvPathname, schema='maprfs', timeoutSecs=timeoutSecs, doSummary=False)
            elapsed = time.time() - start

            fileMBS = (totalBytes/1e6)/elapsed
            l = '{!s} jvms, {!s}GB heap, {:s} {:s} {:6.2f}MB {:6.2f} MB/sec for {:.2f} secs'.format(
                len(h2o.nodes), h2o.nodes[0].java_heap_GB, 'Parse', csvPathname, (totalBytes+0.0)/1e6, fileMBS, elapsed)
            print "\n"+l
            # h2o.cloudPerfH2O.message(l)

            # chunk_size=4194304*2
            pA = h2o_cmd.ParseObj(parseResult)
            iA = h2o_cmd.InspectObj(pA.parse_key)

            parse_key = pA.parse_key
            numRows = iA.numRows
            numCols = iA.numCols
            labelList = iA.labelList

            print iA.missingList, iA.labelList, iA.numRows, iA.numCols

            for i in range(1):
                print "Summary on column", i
                co = h2o_cmd.runSummary(key=parse_key, column=i)

            k = parseResult['frames'][0]['frame_id']['name']
            # print "parseResult:", dump_json(parseResult)
            a_node = h2o.nodes[0]
            frames_result = a_node.frames(key=k, row_count=5)
            # print "frames_result from the first parseResult key", dump_json(frames_result)
            
            # FIX! switch this to look at the summary result
            parseKeyIndexedCheck(frames_result, multiplyExpected)
            # don't want to spill keys
            h2o.nodes[0].remove_all_keys()

if __name__ == '__main__':
    h2o.unit_main()

import unittest, time, sys, random
sys.path.extend(['.','..','../..','py'])
import h2o2 as h2o
import h2o_cmd, h2o_browse as h2b, h2o_import as h2i, h2o_exec as h2e
import getpass
from h2o_test import dump_json

DO_EXPORT=False

class Basic(unittest.TestCase):
    def tearDown(self):
        h2o.check_sandbox_for_errors()

    @classmethod
    def setUpClass(cls):
        # assume we're at 0xdata with it's hdfs namenode
        h2o.init(1, use_hdfs=True, hdfs_version='hdp2.1', hdfs_name_node='172.16.2.186', java_heap_GB=12)

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()

    def test_hdfs_hdp2_1(self):
        print "\nLoad a list of files from HDFS, parse and do 1 RF tree"
        print "\nYou can try running as hduser/hduser if fail"
        # larger set in my local dir
        # fails because classes aren't integers
        #    "allstate_claim_prediction_train_set.zip",
        csvFilenameAll = [
            # "3G_poker_shuffle"
            ("and-testing.data", 60),
            ### "arcene2_train.both",
            ### "arcene_train.both",
            ### "bestbuy_test.csv",
            ("covtype.data", 60),
            ("covtype4x.shuffle.data", 60),
            # "four_billion_rows.csv",
            ("hhp.unbalanced.012.data.gz", 60),
            ("hhp.unbalanced.data.gz", 60),
            ("leads.csv", 60),
            # ("covtype.169x.data", 1200),
            ("prostate_long_1G.csv", 200),
            ("airlines_all.csv", 1200),
        ]

        # pick 8 randomly!
        if (1==0):
            csvFilenameList = random.sample(csvFilenameAll,8)
        # Alternatively: do the list in order! Note the order is easy to hard
        else:
            csvFilenameList = csvFilenameAll

        # pop open a browser on the cloud
        # h2b.browseTheCloud()

        trial = 0
        print "try importing /tmp2"
        d = h2i.import_only(path="tmp2/*", schema='hdfs', timeoutSecs=1000)
        for (csvFilename, timeoutSecs) in csvFilenameList:
            # creates csvFilename.hex from file in hdfs dir 
            print "Loading", csvFilename, 'from HDFS'
            start = time.time()
            hex_key = "a.hex"
            csvPathname = "datasets/" + csvFilename

            # Do a simple typeahead check on the directory
            # typeaheadResult 2: {
            #   "__meta": {
            #     "schema_name": "TypeaheadV2",
            #     "schema_type": "Iced",
            #     "schema_version": 2
            #   },
            #   "limit": 2,
            #   "matches": [
            #     "hdfs://172.16.2.186/datasets/15Mx2.2k.csv",
            #     "hdfs://172.16.2.186/datasets/1Mx2.2k.NAs.csv"
            #   ],
            #   "src": "hdfs://172.16.2.186/datasets/"
            # }

            typeaheadPath = "hdfs://"+ h2o.nodes[0].hdfs_name_node + "/datasets/"
            typeaheadResult = h2o.nodes[0].typeahead(src=typeaheadPath, limit=2)
            print "typeaheadResult 2:", dump_json(typeaheadResult)
            assert len(typeaheadResult['matches']) == 2

            typeaheadResult = h2o.nodes[0].typeahead(src=typeaheadPath, limit=0)
            print "typeaheadResult 0:", dump_json(typeaheadResult)
            assert len(typeaheadResult['matches']) > 2

            typeaheadResult = h2o.nodes[0].typeahead(src=typeaheadPath, limit=None)
            print "typeaheadResult 0:", dump_json(typeaheadResult)
            assert len(typeaheadResult['matches']) > 2

            typeaheadResult = h2o.nodes[0].typeahead(src=typeaheadPath, limit=-1)
            print "typeaheadResult -1:", dump_json(typeaheadResult)
            assert len(typeaheadResult['matches']) > 2

            parseResult = h2i.import_parse(path=csvPathname, schema='hdfs', hex_key=hex_key, timeoutSecs=1000)
            print "hdfs parse of", csvPathname, "took", time.time() - start, 'secs'
            pA = h2o_cmd.ParseObj(parseResult)
            iA = h2o_cmd.InspectObj(pA.parse_key)
            parse_key = pA.parse_key
            numRows = iA.numRows
            numCols = iA.numCols
            labelList = iA.labelList

            if DO_EXPORT:
                start = time.time()
                print "Saving", csvFilename, 'to HDFS'
                print "Using /tmp2 to avoid the '.' prefixed files in /tmp2 (kills import)"
                print "Unique per-user to avoid permission issues"
                username = getpass.getuser()
                csvPathname = "tmp2/a%s.%s.csv" % (trial, username)
                # reuse the file name to avoid running out of space
                csvPathname = "tmp2/a%s.%s.csv" % ('_h2o_export_files', username)

                path = "hdfs://"+ h2o.nodes[0].hdfs_name_node + "/" + csvPathname
                h2o.nodes[0].export_files(src_key=hex_key, path=path, force=1, timeoutSecs=timeoutSecs)
                print "export_files of", hex_key, "to", path, "took", time.time() - start, 'secs'
                trial += 1

                print "Re-Loading", csvFilename, 'from HDFS'
                start = time.time()
                hex_key = "a2.hex"
                time.sleep(2)
                d = h2i.import_only(path=csvPathname, schema='hdfs', timeoutSecs=1000)
                print h2o.dump_json(d)
                parseResult = h2i.import_parse(path=csvPathname, schema='hdfs', hex_key=hex_key, timeoutSecs=1000)
                print "hdfs re-parse of", csvPathname, "took", time.time() - start, 'secs'



if __name__ == '__main__':
    h2o.unit_main()

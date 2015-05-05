import unittest, random, sys, time
sys.path.extend(['.','..','../..','py'])
import h2o2 as h2o
import h2o_cmd, h2o_import as h2i, h2o_jobs as h2j
from h2o_test import OutputObj

DO_PCA_SCORE = True

def write_syn_dataset(csvPathname, rowCount, colCount, SEED):
    r1 = random.Random(SEED)
    dsf = open(csvPathname, "w+")

    for i in range(rowCount):
        rowData = []
        for j in range(colCount):
            ri = r1.randint(0,1)
            # put every column in twice, to create dependent cols!
            # the extra cols will have 0 for pca component values
            # add a little noise to them, to see if PCA sees it's mostly the same
            rowData.append(ri)
            # duplicate, but flip it 1 out of 10
            rj = r1.randint(0,10)
            if rj==0:
                # flip it (0/1)
                ri = 0 if ri else 1
            rowData.append(ri)

        # PCA doesn't use/need an output
        # ri = r1.randint(0,1)
        # rowData.append(ri)

        rowDataCsv = ",".join(map(str,rowData))
        dsf.write(rowDataCsv + "\n")

    dsf.close()

class Basic(unittest.TestCase):
    def tearDown(self):
        h2o.check_sandbox_for_errors()

    @classmethod
    def setUpClass(cls):
        global SEED
        SEED = h2o.setup_random_seed()
        h2o.init(1,java_heap_GB=10)

    @classmethod
    def tearDownClass(cls):
        ### time.sleep(3600)
        h2o.tear_down_cloud()

    def test_PCA_many_cols(self):
        SYNDATASETS_DIR = h2o.make_syn_dir()

        tryList = [
            (10000, 10, 'cA', 300), 
            (10000, 50, 'cB', 300), 
            (10000, 100, 'cC', 300), 
            # (10000, 500, 'cH', 300), 
            # (10000, 1000, 'cI', 300), 
            ]

        for (rowCount, colCount, hex_key, timeoutSecs) in tryList:
            print (rowCount, colCount, hex_key, timeoutSecs)
            SEEDPERFILE = random.randint(0, sys.maxint)
            csvFilename = 'syn_' + "binary" + "_" + str(rowCount) + 'x' + str(colCount) + '.csv'
            csvPathname = SYNDATASETS_DIR + '/' + csvFilename
            print "Creating random", csvPathname
            write_syn_dataset(csvPathname, rowCount, colCount, SEEDPERFILE)

            # PARSE ****************************************
            modelKey = 'PCAModelKey'
            scoreKey = 'PCAScoreKey'

            # Parse ****************************************
            parseResult = h2i.import_parse(bucket=None, path=csvPathname, schema='put',
                hex_key=hex_key, timeoutSecs=timeoutSecs, doSummary=False)
            pA = h2o_cmd.ParseObj(parseResult)
            iA = h2o_cmd.InspectObj(pA.parse_key)
            parse_key = pA.parse_key
            numRows = iA.numRows
            numCols = iA.numCols
            labelList = iA.labelList

            print "\n" + csvPathname, \
                "    numRows:", "{:,}".format(numRows), \
                "    numCols:", "{:,}".format(numCols)

            # PCA(tolerance iterate)****************************************
            for tolerance in [i/10.0 for i in range(11)]:
                parameters = {
                    # 'tolerance': tolerance,
                    # 'standardize': 1,
                    'k': 1,
                }
                model_key = 'pca.hex'
                bmResult = h2o.n0.build_model(
                    algo='pca',
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

#                 kwargs = params.copy()
#                 PCAResult = {'python_elapsed': 0, 'python_%timeout': 0}
#                 start = time.time()
#                 h2o_cmd.runPCA(parseResult=parseResult, timeoutSecs=timeoutSecs, noPoll=True, **kwargs)
#                 h2j.pollWaitJobs(timeoutSecs=300, pollTimeoutSecs=120, retryDelaySecs=2)
#                 elapsed = time.time() - start
#                 PCAResult['python_elapsed']  = elapsed
#                 PCAResult['python_%timeout'] = 1.0*elapsed / timeoutSecs
#                 print "PCA completed in",     PCAResult['python_elapsed'], "seconds.", \
#                       "%f pct. of timeout" % (PCAResult['python_%timeout'])
#                 
#                 pcaView = h2o_cmd.runPCAView(modelKey=modelKey)
#                 h2o_pca.simpleCheckPCA(self,pcaView)
#                 h2o_pca.resultsCheckPCA(self,pcaView)
# 
#                 # Logging to a benchmark file
#                 algo = "PCA " + " tolerance=" + str(tolerance)
#                 l = '{:d} jvms, {:d}GB heap, {:s} {:s} {:6.2f} secs'.format(
#                     len(h2o.nodes), h2o.nodes[0].java_heap_GB, algo, csvFilename, PCAResult['python_elapsed'])
#                 print l
#                 h2o.cloudPerfH2O.message(l)
# 
#                 pcaInspect = pcaView
#                 # errrs from end of list? is that the last tree?
#                 sdevs = pcaInspect["pca_model"]["sdev"] 
#                 print "PCA: standard deviations are :", sdevs
# 
#                 propVars = pcaInspect["pca_model"]["propVar"]
#                 print "PCA: Proportions of variance by eigenvector are :", propVars
# 
#                 num_pc = pcaInspect['pca_model']['num_pc']
#                 print "The number of standard deviations obtained: ", num_pc
# 
# 
#                 if DO_PCA_SCORE:
#                     # just score with same data
#                     score_params = {
#                         'model_id': scoreKey,
#                         'model': modelKey,
#                         'num_pc': num_pc,
#                         'source':  hex_key,
#                     }
#                     kwargs = score_params.copy()
#                     pcaScoreResult = h2o.nodes[0].pca_score(timeoutSecs=timeoutSecs, noPoll=True, **kwargs)
#                     h2j.pollWaitJobs(timeoutSecs=300, pollTimeoutSecs=120, retryDelaySecs=2)
#                     print "PCAScore completed in", pcaScoreResult['python_elapsed'], "seconds. On dataset: ", csvPathname
#                     print "Elapsed time was ", pcaScoreResult['python_%timeout'], "% of the timeout"
# 
#                     # Logging to a benchmark file
#                     algo = "PCAScore " + " num_pc=" + str(score_params['num_pc'])
#                     l = '{:d} jvms, {:d}GB heap, {:s} {:s} {:6.2f} secs'.format(
#                         len(h2o.nodes), h2o.nodes[0].java_heap_GB, algo, csvFilename, pcaScoreResult['python_elapsed'])
#                     print l
#                     h2o.cloudPerfH2O.message(l)
# 

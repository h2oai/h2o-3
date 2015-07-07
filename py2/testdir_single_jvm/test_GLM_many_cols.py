import unittest, time, sys, random
sys.path.extend(['.','..','../..','py'])
import h2o2 as h2o
import h2o_cmd, h2o_import as h2i, h2o_jobs, h2o_glm
from h2o_test import verboseprint, dump_json, OutputObj

def write_syn_dataset(csvPathname, rowCount, colCount, SEED):
    r1 = random.Random(SEED)
    dsf = open(csvPathname, "w+")

    for i in range(rowCount):
        rowData = []
        for j in range(colCount):
            ri = r1.randint(0,1)
            rowData.append(ri)

        ri = r1.randint(0,1)
        rowData.append(ri)

        rowDataCsv = ",".join(map(str,rowData))
        dsf.write(rowDataCsv + "\n")

    dsf.close()


class Basic(unittest.TestCase):
    def tearDown(self):
        h2o.nodes[0].log_download()
        h2o.check_sandbox_for_errors()

    @classmethod
    def setUpClass(cls):
        global SEED
        SEED = h2o.setup_random_seed()
        h2o.init(1,java_heap_GB=13)

    @classmethod
    def tearDownClass(cls):
        ### time.sleep(3600)
        h2o.tear_down_cloud()

    def test_GLM_many_cols(self):
        SYNDATASETS_DIR = h2o.make_syn_dir()

        tryList = [
            # (2, 100, 'cA', 300), 
            # (4, 200, 'cA', 300), 
            (10000, 1000, 'cB', 300), 
            (10000, 3000, 'cC', 500), 
            ]

        ### h2b.browseTheCloud()
        lenNodes = len(h2o.nodes)

        for (rowCount, colCount, hex_key, timeoutSecs) in tryList:
            SEEDPERFILE = random.randint(0, sys.maxint)
            # csvFilename = 'syn_' + str(SEEDPERFILE) + "_" + str(rowCount) + 'x' + str(colCount) + '.csv'
            csvFilename = 'syn_' + "binary" + "_" + str(rowCount) + 'x' + str(colCount) + '.csv'
            csvPathname = SYNDATASETS_DIR + '/' + csvFilename

            print "Creating random", csvPathname
            write_syn_dataset(csvPathname, rowCount, colCount, SEEDPERFILE)

            parseResult = h2i.import_parse(path=csvPathname, schema='put', hex_key=hex_key, timeoutSecs=180, doSummary=False)
            pA = h2o_cmd.ParseObj(parseResult)
            iA = h2o_cmd.InspectObj(pA.parse_key)
            parse_key = pA.parse_key
            numRows = iA.numRows
            numCols = iA.numCols
            labelList = iA.labelList

            expected = []
            allowedDelta = 0

            labelListUsed = list(labelList)
            response = 'C' + str(len(labelListUsed)-1) # last column
            labelListUsed.remove(response)
            numColsUsed = numCols - 1
            for trial in range(1):
                # family [u'gaussian', u'binomial', u'poisson', u'gamma', u'tweedie']
                # link [u'family_default', u'identity', u'logit', u'log', u'inverse', u'tweedie']
                # can we do classification with probabilities?
                # are only lambda and alpha grid searchable?
                parameters = {
                    'validation_frame': parse_key,
                    'ignored_columns': None,
                    # FIX! for now just use a column that's binomial
                    'response_column': response, # can't take index now?
                    # FIX! when is this needed? redundant for binomial?
                    'balance_classes': False,
                    'max_after_balance_size': None,
                    'standardize': False,
                    'family': 'binomial',
                    'link': None,
                    'alpha': '[1e-4]',
                    'lambda': '[0.5,0.25, 0.1]',
                    'prior1': None,
                    'lambda_search': None,
                    'nlambdas': None,
                    'lambda_min_ratio': None,
                    # 'use_all_factor_levels': False,
                }
                model_key = 'many_cols_glm.hex'
                bmResult = h2o.n0.build_model(
                    algo='glm',
                    model_id=model_key,
                    training_frame=parse_key,
                    parameters=parameters,
                    timeoutSecs=300)
                bm = OutputObj(bmResult, 'bm')

                modelResult = h2o.n0.models(key=model_key)
                model = OutputObj(modelResult['models'][0]['output'], 'model')

                h2o_glm.simpleCheckGLM(self, model, parameters, labelList, labelListUsed)

                cmmResult = h2o.n0.compute_model_metrics(model=model_key, frame=parse_key, timeoutSecs=60)
                cmm = OutputObj(cmmResult, 'cmm')

                mmResult = h2o.n0.model_metrics(model=model_key, frame=parse_key, timeoutSecs=60)
                mm = OutputObj(mmResult, 'mm')

                prResult = h2o.n0.predict(model=model_key, frame=parse_key, timeoutSecs=60)
                pr = OutputObj(prResult['model_metrics'][0]['predictions'], 'pr')




if __name__ == '__main__':
    h2o.unit_main()

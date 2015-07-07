import unittest, time, sys, random
sys.path.extend(['.','..','../..','py'])
import h2o2 as h2o
import h2o_cmd, h2o_import as h2i, h2o_jobs, h2o_glm
from h2o_test import verboseprint, dump_json, OutputObj
from tabulate import tabulate


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

    def test_GLM_covtype(self):
        importFolderPath = "standard"
        csvFilename = "covtype.data"
        hex_key = "covtype.hex"
        bucket = "home-0xdiag-datasets"
        csvPathname = importFolderPath + "/" + csvFilename

        parseResult = h2i.import_parse(bucket=bucket, path=csvPathname, hex_key=hex_key, 
            check_header=1, timeoutSecs=180, doSummary=False)
        pA = h2o_cmd.ParseObj(parseResult)
        iA = h2o_cmd.InspectObj(pA.parse_key)
        parse_key = pA.parse_key
        numRows = iA.numRows
        numCols = iA.numCols
        labelList = iA.labelList

        expected = []
        allowedDelta = 0

        labelListUsed = list(labelList)
        labelListUsed.remove('C54')
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
                'response_column': 'C54',
                # FIX! when is this needed? redundant for binomial?
                'balance_classes': False,
                'max_after_balance_size': None,
                'standardize': False,
                'family': 'binomial', 
                'link': None, 
                'alpha': '[1e-4]',
                'lambda': '[0.5,0.25, 0.1]',
                'lambda_search': None,
                'nlambdas': None,
                'lambda_min_ratio': None,
                # 'use_all_factor_levels': False,
            }

            model_key = 'covtype_glm.hex'
            bmResult = h2o.n0.build_model(
                algo='glm',
                model_id=model_key,
                training_frame=parse_key,
                parameters=parameters,
                timeoutSecs=60)
            bm = OutputObj(bmResult, 'bm')


            modelResult = h2o.n0.models(key=model_key)
            model = OutputObj(modelResult['models'][0]['output'], 'model')
            h2o_glm.simpleCheckGLM(self, model, parameters, labelList, labelListUsed)

            cmmResult = h2o.n0.compute_model_metrics(model=model_key, frame=parse_key, timeoutSecs=60)
            cmm = OutputObj(cmmResult, 'cmm')

            mcms = OutputObj({'data': cmm.max_criteria_and_metric_scores.data}, 'mcms')
            m1 = mcms.data[1:]
            h0 = mcms.data[0]
            print "\nmcms", tabulate(m1, headers=h0)

            thms = OutputObj(cmm.thresholds_and_metric_scores, 'thms')
            cmms = OutputObj({'cm': cmm.confusion_matrices}, 'cmms')

            if 1==0:
                print ""
                for i,c in enumerate(cmms.cm):
                    print "\ncmms.cm[%s]" % i, tabulate(c)
                print ""
                

            mmResult = h2o.n0.model_metrics(model=model_key, frame=parse_key, timeoutSecs=60)
            mm = OutputObj(mmResult['model_metrics'][0], 'mm')

            prResult = h2o.n0.predict(model=model_key, frame=parse_key, timeoutSecs=60)
            pr = OutputObj(prResult['model_metrics'][0]['predictions'], 'pr')


            # too slow!
            # h2o_cmd.runStoreView(timeoutSecs=30)

if __name__ == '__main__':
    h2o.unit_main()

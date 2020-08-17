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

    def test_GLM_basic_1(self):
        importFolderPath = "logreg"
        csvFilename = "benign.csv"
        hex_key = "benign.hex"
        csvPathname = importFolderPath + "/" + csvFilename

        parseResult = h2i.import_parse(bucket='smalldata', path=csvPathname, hex_key=hex_key, check_header=1, 
            timeoutSecs=180, doSummary=False)
        pA = h2o_cmd.ParseObj(parseResult)
        iA = h2o_cmd.InspectObj(pA.parse_key)
        parse_key = pA.parse_key
        numRows = iA.numRows
        numCols = iA.numCols
        labelList = iA.labelList

        expected = []
        allowedDelta = 0

        # loop, to see if we get same centers

        labelListUsed = list(labelList)
        labelListUsed.remove('STR')
        labelListUsed.remove('FNDX') # response removed also
        numColsUsed = numCols - 2
        for trial in range(1):
            # family [u'gaussian', u'binomial', u'poisson', u'gamma', u'tweedie']
            # link [u'family_default', u'identity', u'logit', u'log', u'inverse', u'tweedie']
            # can we do classification with probabilities?
            # are only lambda and alpha grid searchable?

            # glm parameters:

            # model_id Key<Model> False None []
            # training_frame Key<Frame> False None []
            # validation_frame Key<Frame> False None []
            # ignored_columns string[] False None []
            # drop_na20_cols boolean False False []
            # score_each_iteration boolean False False []
            # response_column VecSpecifier False None []
            # balance_classes boolean False False []
            # class_sampling_factors float[] False None []
            # max_after_balance_size float False 5.0 []
            # max_confusion_matrix_size int False 20 []
            # family enum False gaussian [u'gaussian', u'binomial', u'poisson', u'gamma']
            # solver enum False IRLSM [u'AUTO', u'IRLSM', u'L_BFGS']

            # alpha double[] False None []

            # lambda double[] False None []
            # lambda_search boolean False False []
            # lambda_min_ratio double False -1.0 []
            # nlambdas int False -1 []

            # standardize boolean False True []
            # max_iterations int False -1 []
            # beta_epsilon double False 0.0001 []
            # link enum False family_default [u'family_default', u'identity', u'logit', u'log', u'inverse', u'tweedie']
            # prior double False -1.0 []
            # use_all_factor_levels boolean False False []
            # beta_constraints Key<Frame> False None []
            # max_active_predictors int False -1 []

            parameters = {
                'ignored_columns': '["STR"]',
                'response_column': 'FNDX',
                # FIX! when is this needed? redundant for binomial?
                'balance_classes': False,
                'max_after_balance_size': None,
                'standardize': False,
                'family': 'binomial', 
                'link': None, 
                'alpha': '[1e-4]',
                'lambda': '[0.5]',
                'prior1': None,
                'lambda_search': None,
                'nlambdas': None,
                'lambda_min_ratio': None,
                # 'use_all_factor_levels': False,
            }

            model_key = 'benign_glm.hex'
            bmResult = h2o.n0.build_model(
                algo='glm',
                model_id=model_key,
                training_frame=parse_key,
                parameters=parameters,
                timeoutSecs=10)
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
            if 1==0:
                cmms = OutputObj({'cm': cmm.confusion_matrices}, 'cmms')
                print ""
                for i,c in enumerate(cmms.cm):
                    print "\ncmms.cm[%s]" % i, tabulate(c)
                print ""
                

            mmResult = h2o.n0.model_metrics(model=model_key, frame=parse_key, timeoutSecs=60)
            mm = OutputObj(mmResult['model_metrics'][0], 'mm')

            prResult = h2o.n0.predict(model=model_key, frame=parse_key, timeoutSecs=60)
            pr = OutputObj(prResult['model_metrics'][0]['predictions'], 'pr')

            # h2o_cmd.runStoreView()

if __name__ == '__main__':
    h2o.unit_main()

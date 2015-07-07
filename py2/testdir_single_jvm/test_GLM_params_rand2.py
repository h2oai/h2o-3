import unittest, time, sys, random
sys.path.extend(['.','..','../..','py'])
import h2o2 as h2o
import h2o_cmd, h2o_import as h2i, h2o_jobs, h2o_glm
from h2o_test import verboseprint, dump_json, OutputObj
from tabulate import tabulate
from h2o_xl import Key, Assign

def define_params():
    paramDict = {
        # FIX! when is this needed? redundant for binomial?
        'balance_classes': [True, False, None],
        'class_sampling_factors': [0.1, 0.2, "0.1, 0.2", None],
        'max_after_balance_size': [100.0, 1000.0, None],
        # 'solver': ['ADMM', 'L_BFGS', None],
        'solver': ['L_BFGS', None],
        'max_iterations': [1, 3, 15, None],
        'drop_na20_cols': [None, 0, 1],
        'standardize': [None, 0, 1],

        'nlambdas': [None, 1,2,5], # number of lambdas to be used in a search
        'lambda_min_ratio': [None, .1, 0.9], # ratio of lambda max. Evidently can't take 1 ?
        'lambda': [0, 1e-8, 1e-4, 1e-3],
        'lambda_search': [None, 0, 1], # FIX! what if lambda is set when lambda_search=1

        # 'use_all_factor_levels': [None, 0, 1],
        'alpha': [0,0.2,0.4],
        'family': ['family_default', 'gaussian', 'binomial', 'poisson', None],
        'link': ['logit', 'log', 'inverse', 'tweedie', None],
        'ignored_columns': [1,'"C1"','1,2','"C1","C2"'],
        'standardize': [None, 0,1],
        # 'intercept': [None, 0, 1],
        # 'non_negative': [None, 0,1], # require coefficents to be non-negative
        # 'variable_importances': [None, 0, 1],
        }

    return paramDict

class Basic(unittest.TestCase):
    def tearDown(self):
        h2o.check_sandbox_for_errors()

    @classmethod
    def setUpClass(cls):
        global SEED
        print "hardwiring seed for now"
        SEED = h2o.setup_random_seed(seed=6418304027311682180)
        h2o.init(1)

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()

    def test_GLM_params_rand2(self):
        importFolderPath = "covtype"
        csvFilename = "covtype.20k.data"
        hex_key = "covtype20k.hex"
        binomial_key = "covtype20k.b.hex"
        b = Key(hex_key)
        csvPathname = importFolderPath + "/" + csvFilename

        parseResult = h2i.import_parse(bucket='smalldata', path=csvPathname, hex_key=hex_key,
            check_header=1, timeoutSecs=180, doSummary=False)

        ## columnTypeDict = {54: 'Enum'}
        columnTypeDict = None
        parseResult = h2i.import_parse(bucket='smalldata', path=csvPathname, hex_key=binomial_key, 
            columnTypeDict=columnTypeDict,
            check_header=1, timeoutSecs=180, doSummary=False)

        # don't have to make it enum, if 0/1 (can't operate on enums like this)
        # make 1-7 go to 0-6. 0 isn't there.
        Assign(b[:,54], b[:,54]-1)
        # make 1 thru 6 go to 1
        Assign(b[:,54], b[:,54]!=0)
        # now we have just 0 and 1

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
        numColsUsed = numCols

        paramDict = define_params()
        for trial in range(5):
            # family [u'gaussian', u'binomial', u'poisson', u'gamma', u'tweedie']
            # link [u'family_default', u'identity', u'logit', u'log', u'inverse', u'tweedie']
            # can we do classification with probabilities?
            # are only lambda and alpha grid searchable?

            # params is mutable. This is default.
            parameters = {
                'response_column': 'C55',
                'alpha': 0.1,
                # 'lambda': 1e-4, 
                'lambda': 0,
            }
            h2o_glm.pickRandGlmParams(paramDict, parameters)

            if 'family' not in parameters or parameters['family']=='binomial':
                bHack = binomial_key
            else:
                bHack = hex_key

            co = h2o_cmd.runSummary(key=binomial_key, column=54)
            print "binomial_key summary:", co.label, co.type, co.missing_count, co.domain, sum(co.histogram_bins)
            co = h2o_cmd.runSummary(key=hex_key, column=54)
            print "hex_key summary:", co.label, co.type, co.missing_count, co.domain, sum(co.histogram_bins)

            # fix stupid params
            fixList = ['alpha', 'lambda', 'ignored_columns', 'class_sampling_factors']
            for f in fixList:
                if f in parameters:
                    parameters[f] = "[%s]" % parameters[f]

            model_key = 'rand_glm.hex'
            bmResult = h2o.n0.build_model(
                algo='glm',
                model_id=model_key,
                training_frame=bHack,
                parameters=parameters,
                timeoutSecs=10)
            bm = OutputObj(bmResult, 'bm')

            modelResult = h2o.n0.models(key=model_key)
            model = OutputObj(modelResult['models'][0]['output'], 'model')
            h2o_glm.simpleCheckGLM(self, model, parameters, labelList, labelListUsed, allowNaN=True)

            cmmResult = h2o.n0.compute_model_metrics(model=model_key, frame=parse_key, timeoutSecs=60)
            cmm = OutputObj(cmmResult, 'cmm')

            # FIX! when is this legal
            doClassification = False
            if doClassification:
                mcms = OutputObj({'data': cmm.max_criteria_and_metric_scores.data}, 'mcms')
                m1 = mcms.data[1:]
                h0 = mcms.data[0]
                print "\nmcms", tabulate(m1, headers=h0)

            if doClassification:
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

            # h2o_cmd.runStoreView()

if __name__ == '__main__':
    h2o.unit_main()

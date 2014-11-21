import unittest, time, sys, random
sys.path.extend(['.','..','../..','py'])
import h2o, h2o_cmd, h2o_import as h2i, h2o_jobs
from h2o_test import verboseprint, dump_json


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

    class GLMOutput(object):
        def __init__(self, output):
            assert isinstance(output, dict)
            for k,v in output.iteritems():
                setattr(self, k, v) # achieves self.k = v

        def __iter__(self):
            for attr, value in self.__dict__.iteritems():
                yield attr, value

    def test_GLM_basic_1(self):
        importFolderPath = "logreg"
        csvFilename = "benign.csv"
        hex_key = "benign.hex"
        csvPathname = importFolderPath + "/" + csvFilename

        parseResult = h2i.import_parse(bucket='smalldata', path=csvPathname, hex_key=hex_key, checkHeader=1, 
            timeoutSecs=180, doSummary=False)
        numRows, numCols, parse_key = h2o_cmd.infoFromParse(parseResult)
        inspectResult = h2o_cmd.runInspect(key=parse_key)
        missingList, labelList, numRows, numCols = h2o_cmd.infoFromInspect(inspectResult)

        expected = []
        allowedDelta = 0

        # loop, to see if we get same centers

        # no cols ignored
        labelListUsed = list(labelList)
        numColsUsed = numCols
        for trial in range(1):
            # family [u'gaussian', u'binomial', u'poisson', u'gamma', u'tweedie']
            # link [u'family_default', u'identity', u'logit', u'log', u'inverse', u'tweedie']
            # can we do classification with probabilities?
            # are only lambda and alpha grid searchable?
            parameters = {
                'validation_frame': parse_key,
                'ignored_columns': '[STR]',
                'score_each_iteration': True,
                'response_column': 'FNDX',
                # FIX! when is this needed? redundant for binomial?
                'do_classification': True,
                'balance_classes': False,
                'max_after_balance_size': None,
                'standardize': False,
                'family': 'binomial', 
                'link': None, 
                'tweedie_variance_power': None,
                'tweedie_link_power': None,
                'alpha': '[1e-4]',
                'lambda': '[0.5]',
                'prior1': None,
                'lambda_search': None,
                'nlambdas': None,
                'lambda_min_ratio': None,
                'higher_accuracy': True,
                'use_all_factor_levels': False,
                # NPE with n_folds 2?
                'n_folds': 1,
            }


            model_key = 'benign_glm.hex'
            glmResult = h2o.n0.build_model(
                algo='glm', 
                destination_key=model_key,
                training_frame=parse_key,
                parameters=parameters, 
                timeoutSecs=10) 

            gr = self.GLMOutput(glmResult)
            for k,v in gr:
                if k != 'parameters':
                    print "gr", k, dump_json(v)

            modelResult = h2o.n0.models(key=model_key)

            mr = self.GLMOutput(modelResult['models'][0]['output'])
            for k,v in mr:
                if k != 'parameters':
                    print "mr", k, dump_json(v)

            cmmResult = h2o.n0.compute_model_metrics(
                model=model_key, 
                frame=parse_key, 
                timeoutSecs=60)

            print "cmmResult", dump_json(cmmResult)

            mmResult = h2o.n0.model_metrics(
                model=model_key, 
                frame=parse_key, 
                timeoutSecs=60)
    
            print "mmResult", dump_json(mmResult)

            # this prints too
            # tuplesSorted, iters, mse, names = \
            #    h2o_glm.simpleCheckGLM(self, modelResult, parameters, numRows, numColsUsed, labelListUsed)
            
            h2o_cmd.runStoreView()

if __name__ == '__main__':
    h2o.unit_main()

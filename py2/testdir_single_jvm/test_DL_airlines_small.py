import unittest, time, sys, random, string
sys.path.extend(['.','..','../..','py'])
import h2o2 as h2o
import h2o_cmd, h2o_import as h2i, h2o_jobs
from h2o_test import verboseprint, dump_json, OutputObj

class Basic(unittest.TestCase):
    def tearDown(self):
        h2o.check_sandbox_for_errors()

    @classmethod
    def setUpClass(cls):
        global SEED
        SEED = h2o.setup_random_seed()
        h2o.init()

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()

    def test_DL_airlines_small(self):
        h2o.nodes[0].remove_all_keys()
        csvPathname_train = 'airlines/AirlinesTrain.csv.zip'
        csvPathname_test  = 'airlines/AirlinesTest.csv.zip'
        hex_key = 'train.hex'
        validation_key = 'validation.hex'
        timeoutSecs = 60
        parseResult  = h2i.import_parse(bucket='smalldata', path=csvPathname_train, hex_key=hex_key, timeoutSecs=timeoutSecs, doSummary=False)
        pA = h2o_cmd.ParseObj(parseResult)
        iA = h2o_cmd.InspectObj(pA.parse_key)

        parseResultV = h2i.import_parse(bucket='smalldata', path=csvPathname_test, hex_key=validation_key, timeoutSecs=timeoutSecs, doSummary=False)
        pAV = h2o_cmd.ParseObj(parseResultV)
        iAV = h2o_cmd.InspectObj(pAV.parse_key)

        #Making random id
        identifier = ''.join(random.sample(string.ascii_lowercase + string.digits, 10))
        model_key = 'deeplearning_' + identifier + '.hex'

        parameters = {
            'validation_frame': validation_key, # KeyIndexed None
            'ignored_columns': "['IsDepDelayed_REC']", # string[] None
            'response_column': 'IsDepDelayed', # string None
            'loss': 'CrossEntropy'
        }
        expectedErr = 0.32 ## expected validation error for the above model
        relTol = 0.15 ## 15% rel. error tolerance due to Hogwild!

        timeoutSecs = 60
        start = time.time()

        bmResult = h2o.n0.build_model(
            algo='deeplearning',
            model_id=model_key,
            training_frame=hex_key,
            parameters=parameters,
            timeoutSecs=timeoutSecs)
        bm = OutputObj(bmResult, 'bm')

        print 'deep learning took', time.time() - start, 'seconds'

        modelResult = h2o.n0.models(key=model_key)
        model = OutputObj(modelResult['models'][0]['output'], 'model')
#        print "model:", dump_json(model)

        cmmResult = h2o.n0.compute_model_metrics(model=model_key, frame=validation_key, timeoutSecs=60)
        cmm = OutputObj(cmmResult, 'cmm')

        mmResult = h2o.n0.model_metrics(model=model_key, frame=validation_key, timeoutSecs=60)
        mm = OutputObj(mmResult['model_metrics'][0], 'mm')

        prResult = h2o.n0.predict(model=model_key, frame=validation_key, timeoutSecs=60)
        pr = OutputObj(prResult['model_metrics'][0]['predictions'], 'pr')

        h2o_cmd.runStoreView()

        actualErr = model['errors']['valid_err']
        print "expected classification error: " + format(expectedErr)
        print "actual   classification error: " + format(actualErr)

        if actualErr != expectedErr and abs((expectedErr - actualErr)/expectedErr) > relTol:
            raise Exception("Scored classification error of %s is not within %s %% relative error of %s" %
                            (actualErr, float(relTol)*100, expectedErr))

if __name__ == '__main__':
    h2o.unit_main()

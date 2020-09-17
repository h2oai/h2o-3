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

    def test_DL_mnist(self):
        h2o.nodes[0].remove_all_keys()
        csvPathname_train = 'laptop/mnist/train.csv.gz'
        csvPathname_test  = 'laptop/mnist/test.csv.gz'
        hex_key = 'mnist_train.hex'
        validation_key = 'mnist_test.hex'
        timeoutSecs = 60
        parseResult  = h2i.import_parse(bucket='bigdata', path=csvPathname_train, hex_key=hex_key, timeoutSecs=timeoutSecs, doSummary=False)
        pA = h2o_cmd.ParseObj(parseResult)
        iA = h2o_cmd.InspectObj(pA.parse_key)
        numCols = iA.numCols
        labelList = iA.labelList
        parseResultV = h2i.import_parse(bucket='bigdata', path=csvPathname_test, hex_key=validation_key, timeoutSecs=timeoutSecs, doSummary=False)

        response = numCols-1

        #Making random id
        identifier = ''.join(random.sample(string.ascii_lowercase + string.digits, 10))
        model_key = 'deeplearning_' + identifier + '.hex'

        parameters = {
            'validation_frame': validation_key, # KeyIndexed None
            'ignored_columns': None, # string[] None
            'response_column': labelList[response], # string None
            'balance_classes': None, # boolean false
            'max_after_balance_size': None, # float Infinity

            'keep_cross_validation_splits': None, # boolean false
            'checkpoint': None, # Key None
            'overwrite_with_best_model': None, # boolean true
            'expert_mode': None, # boolean false
            'autoencoder': None, # boolean false
            # 'use_all_factor_levels': None, # boolean true
            # [u'Tanh', u'TanhWithDropout', u'Rectifier', u'RectifierWithDropout', u'Maxout', u'MaxoutWithDropout']
            'activation': 'RectifierWithDropout', # enum Rectifier 
            'hidden': '[117,131,129]', # int[] [200, 200]
            'epochs': 2.0, # double 10.0
            'train_samples_per_iteration': None, # long -2
            'target_ratio_comm_to_comp': None, # double 0.02
            'seed': None, # long 1679194146842485659
            'adaptive_rate': False, # boolean true
            'rho': None, # double 0.99
            'epsilon': None, # double 1.0E-8
            'rate': None, # double 0.005
            'rate_annealing': None, # double 1.0E-6
            'rate_decay': None, # double 1.0
            'momentum_start': 0.5, # double 0.0
            'momentum_ramp': 100000, # double 1000000.0
            'momentum_stable': 0.9, # double 0.0
            'nesterov_accelerated_gradient': None, # boolean true
            'input_dropout_ratio': 0.2, # double 0.0
            'hidden_dropout_ratios': None, # double[] None (this can grid?)
            'l1': 1e-5, # double 0.0
            'l2': 1e-7, # double 0.0
            'max_w2': 15, # float Infinity
            'initial_weight_distribution': None, # enum UniformAdaptive [u'UniformAdaptive', u'Uniform', u'Normal']
            'initial_weight_scale': None, # double 1.0
            'loss': 'CrossEntropy', # enum MeanSquare [u'Automatic', u'MeanSquare', u'CrossEntropy']
            'score_interval': None, # double 5.0
            'score_training_samples': None, # long 10000
            'score_validation_samples': None, # long 0
            'score_duty_cycle': None, # double 0.1
            'classification_stop': None, # double 0.0
            'regression_stop': None, # double 1.0E-6
            'quiet_mode': None, # boolean false
            'max_confusion_matrix_size': None, # int 20
            'balance_classes': None, # boolean false
            'class_sampling_factors': None, # float[] None
            'max_after_balance_size': None, # float Infinity
            'score_validation_sampling': None, # enum Uniform [u'Uniform', u'Stratified']
            'diagnostics': None, # boolean true
            'variable_importances': None, # boolean false
            'fast_mode': None, # boolean true
            'ignore_const_cols': None, # boolean true
            'force_load_balance': None, # boolean true
            'replicate_training_data': None, # boolean false
            'single_node_mode': None, # boolean false
            'shuffle_training_data': None, # boolean false
            'missing_values_handling': None, # enum MeanImputation [u'Skip', u'MeanImputation']
            'sparse': None, # boolean false
            'col_major': None, # boolean false
            'average_activation': None, # double 0.0
            'sparsity_beta': None, # double 0.0
        }
        expectedErr = 0.057 ## expected validation error for the above model
        relTol = 0.20 ## 20% rel. error tolerance due to Hogwild!

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

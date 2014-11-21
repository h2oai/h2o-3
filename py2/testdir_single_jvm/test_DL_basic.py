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

    class DLOutput(object):
        def __init__(self, output):
            assert isinstance(output, dict)
            for k,v in output.iteritems():
                setattr(self, k, v) # achieves self.k = v

        def __iter__(self):
            for attr, value in self.__dict__.iteritems():
                yield attr, value

    def test_DL_basic(self):
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

        # no cols ignored
        labelListUsed = list(labelList)
        numColsUsed = numCols
        for trial in range(1):
            parameters = {
                'validation_frame': parse_key, # Frame None
                'ignored_columns': None, # string[] None
                'score_each_iteration': None, # boolean false
                'response_column': None, # string None
                'do_classification': None, # boolean false
                'balance_classes': None, # boolean false
                'max_after_balance_size': None, # float Infinity
                'n_folds': None, # int 0

                'keep_cross_validation_splits': None, # boolean false
                'checkpoint': None, # Key None
                'override_with_best_model': None, # boolean true
                'expert_mode': None, # boolean false
                'autoencoder': None, # boolean false
                'use_all_factor_levels': None, # boolean true
                # [u'Tanh', u'TanhWithDropout', u'Rectifier', u'RectifierWithDropout', u'Maxout', u'MaxoutWithDropout']
                'activation': None, # enum Rectifier 
                'hidden': None, # int[] [200, 200]
                'epochs': None, # double 10.0
                'train_samples_per_iteration': None, # long -2
                'target_ratio_comm_to_comp': None, # double 0.02
                'seed': None, # long 1679194146842485659
                'adaptive_rate': None, # boolean true
                'rho': None, # double 0.99
                'epsilon': None, # double 1.0E-8
                'rate': None, # double 0.005
                'rate_annealing': None, # double 1.0E-6
                'rate_decay': None, # double 1.0
                'momentum_start': None, # double 0.0
                'momentum_ramp': None, # double 1000000.0
                'momentum_stable': None, # double 0.0
                'nesterov_accelerated_gradient': None, # boolean true
                'input_dropout_ratio': None, # double 0.0
                'hidden_dropout_ratios': None, # double[] None (this can grid?)
                'l1': None, # double 0.0
                'l2': None, # double 0.0
                'max_w2': None, # float Infinity
                'initial_weight_distribution': None, # enum UniformAdaptive [u'UniformAdaptive', u'Uniform', u'Normal']
                'initial_weight_scale': None, # double 1.0
                'loss': None, # enum MeanSquare [u'Automatic', u'MeanSquare', u'CrossEntropy']
                'score_interval': None, # double 5.0
                'score_training_samples': None, # long 10000
                'score_validation_samples': None, # long 0
                'score_duty_cycle': None, # double 0.1
                'classification_stop': None, # double 0.0
                'regression_stop': None, # double 1.0E-6
                'quiet_mode': None, # boolean false
                'max_confusion_matrix_size': None, # int 20
                'max_hit_ratio_k': None, # int 10
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


            model_key = 'benign_dl.hex'
            dlResult = h2o.n0.build_model(
                algo='deeplearning', 
                destination_key=model_key,
                training_frame=parse_key,
                parameters=parameters, 
                timeoutSecs=10) 

            gr = self.DLOutput(dlResult)
            for k,v in gr:
                if k != 'parameters':
                    print "gr", k, dump_json(v)

            modelResult = h2o.n0.models(key=model_key)

            mr = self.DLOutput(modelResult['models'][0]['output'])
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
            #    h2o_dl.simpleCheckDL(self, modelResult, parameters, numRows, numColsUsed, labelListUsed)
            
            h2o_cmd.runStoreView()

if __name__ == '__main__':
    h2o.unit_main()

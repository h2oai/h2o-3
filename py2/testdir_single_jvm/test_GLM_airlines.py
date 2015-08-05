import unittest, sys, time
sys.path.extend(['.','..','../..','py'])
import h2o2 as h2o
import h2o_cmd, h2o_import as h2i, h2o_glm
from h2o_test import dump_json, verboseprint, OutputObj
from tabulate import tabulate


class Basic(unittest.TestCase):
    def tearDown(self):
        h2o.check_sandbox_for_errors()

    @classmethod
    def setUpClass(cls):
        # h2o.init(1, use_hdfs=True, hdfs_version='cdh5', hdfs_name_node='172.16.2.180', java_heap_GB=14)
        h2o.init(1, java_heap_GB=24)


    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()

    def test_GLM_airlines(self):
        files = [
                 # ('airlines', 'airlines_all.csv', 'airlines_all.hex', 1800, 'IsDepDelayed')
                 ('airlines', 'year2013.csv', 'airlines_all.hex', 1800, 'IsDepDelayed')
                ]

        for importFolderPath, csvFilename, trainKey, timeoutSecs, response in files:
            # PARSE train****************************************
            csvPathname = importFolderPath + "/" + csvFilename

            model_key = 'GLMModelKey'
            parseResult = h2i.import_parse(bucket='home-0xdiag-datasets', path=csvPathname, schema='local', hex_key=trainKey, timeoutSecs=timeoutSecs)

            pA = h2o_cmd.ParseObj(parseResult)
            iA = h2o_cmd.InspectObj(pA.parse_key)
            parse_key = pA.parse_key
            numRows = iA.numRows
            numCols = iA.numCols
            labelList = iA.labelList

            labelListUsed = list(labelList)
            numColsUsed = numCols

            parameters = {
                'validation_frame': parse_key,
                'ignored_columns': '["CRSDepTime","CRSArrTime","ActualElapsedTime","CRSElapsedTime","AirTime","ArrDelay","DepDelay","TaxiIn","TaxiOut","Cancelled","CancellationCode","Diverted","CarrierDelay","WeatherDelay","NASDelay","SecurityDelay","LateAircraftDelay","IsArrDelayed"]',
                'response_column': response,
                # FIX! when is this needed? redundant for binomial?
                'balance_classes': False,
                'max_after_balance_size': None,
                'standardize': False,
                'family': 'binomial',
                'link': None,
                'alpha': '[0]',
                'lambda': '[0.5]',
                'prior1': None,
                'lambda_search': None,
                'nlambdas': None,
                'lambda_min_ratio': None,
                # 'use_all_factor_levels': False,
            }

            model_key = 'airlines_glm.hex'
            bmResult = h2o.n0.build_model(
                algo='glm',
                model_id=model_key,
                training_frame=parse_key,
                parameters=parameters,
                timeoutSecs=300)
            bm = OutputObj(bmResult, 'bm')

            modelResult = h2o.n0.models(key=model_key)
            model = OutputObj(modelResult['models'][0]['output'], 'model')
            t0 = len(model.coefficients_table.data[0])
            t1 = len(model.coefficients_table.data[1])

            # not sure what the exact number should be, but it's gotta be less than the cols in the dataset?
            # Whoa! forgot GLM expands enums to individiual coefficients. Would really need to look at all the domains and sum plus other cols?
            # assert t0 <= numColsUsed, "%s %s" % (t0, numColsUsed)
            # assert t1 <= numColsUsed, "%s %s" % (t1, numColsUsed)

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

if __name__ == '__main__':
    h2o.unit_main()

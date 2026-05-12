import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.hglm import H2OHGLMEstimator as hglm

# This test will generate synthetic HGLM dataset.  If given to a HGLM model, it should be able to perform well with 
# this dataset since the assumptions associated with HGLM are used to generate the dataset.  However, pay attention
# to the data types and you may have to cast enum columns to factors manually since during the save, column types
# information may be lost.
#
# Apart from saving the dataset using h2o.download_csv, remember to save the column  types as
# np.save('my_file.npy', dictionary) np.save('my_file.npy', varDict) 
#
# when you want to load the dataset, remember to load the types dictionary as
# types_dict = np.load('my_file.npy',allow_pickle='TRUE').item()
#
# then load your synthetic dataset specifying the column type as
# train = h2o.import_file("mydata.csv", col_types=types_dict)
def test_define_dataset():
    family = 'gaussian' # can be any valid GLM families
    nrow = 40000
    nenum = 3
    nreal = 3 # last one is the response
    # to generate data in hglm_test/gaussian_0GC_123R_all5Numeric_p2noise_p08T_wIntercept_standardize.csv, 1 cat, 5 numeric
    # 1 response, seed = 12345 
    # startval = [1.9011867, -1.2616812,  0.4293167,  0.9802659,  0.7680827, -0.6359531]

    # gaussian_0GC_123R_all5Numeric_p2noise_p08T_woIntercept_standardize.csv

    # to generate data in hglm_test/gaussian_0GC_123R_all5Enum_p5oise_p08T_wIntercept_standardize.csv, 6 cat, 0 numeric
    # 1 response, seed = 12345
    # startval = [0.7906251,  1.8005780, -3.5665564, -0.8804172, -1.5809320,  1.5188019, -1.6089287,  1.7509011, 
    #             -0.5286826, -1.1203812, -2.3159930,  0.1674759, -0.9065857, -0.7587694, -0.8578529,  0.3007900, 
    #             1.5765745,  1.1725489, -0.6935900, -1.1467158,  1.3960304, -1.7078175, -2.8960526,  0.9847858, 
    #             -1.0951275,  0.1393349, -0.6782085, 3.3711444, -2.0059428,  1.3293327, -0.5083064,  2.7324153, 
    #             0.2036385, -1.6967069,  0.699569, -0.4288891]
    # hglm_test/gaussian_0GC_123R_all5Enum_p5oise_p08T_woIntercept_standardize.csv

    # to generate data in hglm_test/gaussian_0GC_123R_6enum_5num_1p5oise_p08T_wIntercept_standardize.csv, seed=12345,
    # startval = [3.93013069,  0.54472937,  1.00317237,  0.45930296,  2.41925257, -3.09530556, -3.56112954,  1.63825546,
    #             -0.09974517, 0.09546386, -0.67192248, -0.71572626,  0.78566524, -0.58579001, -1.91637762,  0.85650108,
    #             0.91881537,  2.35773321, -0.20756380,  0.40147277, -1.10384921,  0.75619311, -0.57409532,  1.44300300,
    #             2.01180669, -1.90782107,-0.41173998, -0.50159384,  1.22944372, -1.18281946, -2.96645841,  2.14272813,
    #             -0.32555483, -1.00719124,  0.74755600,  1.09900559, 2.30948122, 1.23596162,  0.66856774, -2.56878032,
    #             0.05599762]
    # hglm_test/gaussian_0GC_123R_6enum_5num_1p5oise_p08T_woIntercept_standardize.gz
    # hglm_test/gaussian_0GC_678R_6enum_5num_p05oise_p08T_wIntercept_standardize
    # hglm_test/gaussian_0GC_678R_6enum_5num_p05oise_p08T_woIntercept_standardize
    # hglm_test/gaussian_0GC_1267R_6enum_5num_p08oise_p08T_wIntercept_standardize  
    # hglm_test/gaussian_0GC_1267R_6enum_5num_p08oise_p08T_woIntercept_standardize


    # hglm_test/gaussian_0GC_allenum_allRC_2p5noise_p08T_wIntercept_standardize
    #startval = [1.10825995, -0.37625500,  0.01522888, -2.33646889, -1.39787749,  0.10817416, -0.48015694,  2.47842056,
    #             -3.45931533, 0.25396556, -2.52770259,  0.96282659, -2.40216594, -2.79117384, -2.21220306]    
    # hglm_test/gaussian_0GC_allnumeric_allRC_2p5noise_p08T_woIntercept_standardize
    #startval = [-0.9414337, -2.0222721, -2.4312540]

    # hglm_test/gaussian_0GC_allRC_2enum2numeric_3noise_p08T_wIntercept_standardize
    startval = [-1.4313612,  0.6795744,  1.9795154, -3.1187255,  0.2058840, -1.6596187,  0.3460812, -0.7809777,
               1.6617960, -0.5174034, 1.8273497, -2.4161541,  0.9474324,  2.3616221,  0.7710148,  0.2706556,  1.0541668]
    # hglm_test/gaussian_0GC_allRC_2enum2numeric_3noise_p08T_woIntercept_standardize
    enum_columns = pyunit_utils.random_dataset_enums_only(nrow, nenum, factorL=8, misFrac=0.0)
    real_columns = pyunit_utils.random_dataset_real_only(nrow, nreal, realR = 2,  misFrac=0.0)
    dataset = enum_columns.cbind(real_columns)
    dataset.set_name(dataset.ncol-1, "response")
    cnames = dataset.names
    group_column=cnames[0]
    random_intercept = False
    vare = 3
    varu = 0.08
    random_columns = [cnames[1], cnames[2], cnames[3], cnames[4]]
    hglmDataSet = generate_dataset(family, dataset, group_column, random_columns, startval, random_intercept, 
                                   vare, varu)
    print("Done!")
    #h2o.download_csv(hglmDataSet, "/Users/wendycwong/temp/dataset.csv") # save dataset

  
def generate_dataset(family, trainData, group_column, random_columns, startval, random_intercept, vare, varu):
    myX = trainData.names
    myY = 'response'
    myX.remove(myY)
    myX.remove(group_column)

    names_without_response = trainData.names
    names_without_response.remove(myY)

    m = hglm(family=family, max_iterations=0, random_columns=random_columns, group_column=group_column, 
             tau_u_var_init = varu, tau_e_var_init = vare, random_intercept = random_intercept, gen_syn_data=True, 
             seed = 12345, initial_fixed_effects=startval)
    m.train(training_frame=trainData, y = "response", x =myX)
    f2 = m.predict(trainData)   
    finalDataset = trainData[names_without_response]
    finalDataset = finalDataset.cbind(f2[0])
    finalDataset.set_name(col=finalDataset.ncols-1, name='response')

    return finalDataset




if __name__ == "__main__":
    pyunit_utils.standalone_test(test_define_dataset)
else:
    test_define_dataset()

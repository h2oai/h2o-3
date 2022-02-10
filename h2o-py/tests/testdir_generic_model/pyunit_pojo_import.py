import h2o
from h2o.estimators import H2OGradientBoostingEstimator, H2OGeneralizedLinearEstimator
from tests import pyunit_utils
import os
import sys
from pandas.testing import assert_frame_equal


TEMPLATE = """
import java.util.HashMap;
import java.util.Map;
import hex.genmodel.GenModel;
import hex.genmodel.annotations.ModelPojo;

public class %s extends GenModel {

    public hex.ModelCategory getModelCategory() { return hex.ModelCategory.Regression; }
    public boolean isSupervised() { return true; }
    public int nfeatures() { return 19; }
    public int nclasses() { return 1; } // use "1" for regression

    // Names of columns used by model
    public static final String[] NAMES = new String[] {
            "Bias",
            "MaxWindPeriod",
            "ChangeWindDirect",
            "PressureChange",
            "ChangeTempMag",
            "EvapMM",
            "MaxWindSpeed",
            "Temp9am",
            "RelHumid9am",
            "Cloud9am",
            "WindSpeed9am",
            "Pressure9am",
            "Temp3pm",
            "RelHumid3pm",
            "Cloud3pm",
            "WindSpeed3pm",
            "Pressure3pm",
            "RainToday",
            "TempRange"
    };

    // Derived features (we calculate ourselves in score0 implementation)
    private static final String[] CALCULATED = new String[] {
            "ChangeTemp",
            "ChangeTempDir"
    };

    // Column domains, null means column is numerical
    public static final String[][] DOMAINS = new String[][] {
            /* Bias */ null,
            /* MaxWindPeriod */ {"NA", "earlyAM", "earlyPM", "lateAM", "latePM"},
            /* ChangeWindDirect */ {"c", "l", "n", "s"},
            /* PressureChange */ {"down", "steady", "up"},
            /* ChangeTempMag */ {"large", "small"},
            /* EvapMM */ null,
            /* MaxWindSpeed */ null,
            /* Temp9am */ null,
            /* RelHumid9am */ null,
            /* Cloud9am */ null,
            /* WindSpeed9am */ null,
            /* Pressure9am */ null,
            /* Temp3pm */ null,
            /* RelHumid3pm */ null,
            /* Cloud3pm */ null,
            /* WindSpeed3pm */ null,
            /* Pressure3pm */ null,
            /* RainToday */ null,
            /* TempRange */ null,
            /* RISK_MM */ null
    };

    private final GenModel glm;
    private final GenModel gbm;

    // for each sub-model, mapping of the main model input and of the calculated columns to the sub-model input
    private final Map<String, int[]> mappings;

    // map of feature names to feature indices in the input array
    private final Map<String, Integer> featureMap;

    /**
     * POJO constructor, creates instances of the sub-models and initializes
     * helper structures for mapping input schema to the submodel schemas (mapping)
     * and creates a map of feature names to indices to make value-lookups in code more readable. 
     */
    public %s() { 
        super(NAMES, DOMAINS, "RISK_MM"); // response name goes here
        glm = new %s();
        gbm = new %s();
        mappings = makeMappings(glm, gbm);
        featureMap = new HashMap<>(NAMES.length);
        for (int i = 0; i < NAMES.length; i++) {
            featureMap.put(NAMES[i], i);
        }
    }

    @Override
    public String getUUID() { return "MyComplexPojo1"; } // just to show there can be anything here

    // Important to override - BUG in POJO import for regression, will not work without this - FIXME
    @Override
    public int getNumResponseClasses() {
        return 1;
    }

    @Override
    public final double[] score0(double[] data, double[] preds) {
        // (1) Show how to create derived feature (one numerical, the other one categorical)
        // ChangeTemp = Temp3pm - Temp9am
        double changeTemp = fNum("Temp3pm", data) - fNum("Temp9am", data);
        double changeTempDir = changeTemp >= 0 ? 1 : 0; // changeTempDir is categorical: 0 == "down", 1 == "up"
        double[] calculated = {
                changeTemp,
                changeTempDir
        };

        // (2) Show how to score multiple models
        double[] glmPreds = score0SubModel(glm, data, calculated);
        double[] gbmPreds = score0SubModel(gbm, data, calculated);

        // (3) Show how to make decisions based on availability of an input (NA handling)
        double bias = fNum("Bias", data);
        if (!isNA(bias)) { // defined
            // (4) Show to plug in a custom formula
            preds[0] = glmPreds[0] * bias + (1 - bias) * gbmPreds[0];
        } else {
            String changeWindDirect = fCat("ChangeWindDirect", data);
            // (5) Show how to return default values
            if (isNA(changeWindDirect)) { // NA case, use default prediction
                preds[0] = 1;
            } else { // non-NA case, plug-in a formula based on categorical value
                // (6) Show how to handle decisions based on categorical variable (different segments)
                switch (changeWindDirect) {
                    case "c":
                    case "l":
                        preds[0] = glmPreds[0] * 2;
                        break;
                    case "n":
                        preds[0] = (glmPreds[0] + gbmPreds[0]) / 2;
                        break;
                    case "s":
                        preds[0] = gbmPreds[0];
                        break;
                    default:
                        preds[0] = -1;
                }
            }
        }
        return preds;
    }

    private static boolean isNA(double val) {
        return Double.isNaN(val);
    }

    private static boolean isNA(String val) {
        return val == null;
    }

    private double fNum(String feature, double[] data) {
        Integer idx = featureMap.get(feature);
        if (idx == null)
            throw new IllegalArgumentException("Column '" + feature + "' is not part of model features.");
        return data[idx];
    }

    private String fCat(String feature, double[] data) {
        Integer idx = featureMap.get(feature);
        if (idx == null)
            throw new IllegalArgumentException("Column '" + feature + "' is not part of model features.");
        if (Double.isNaN(data[idx]))
            return null;
        int level = (int) data[idx];
        return DOMAINS[idx][level];
    }

    /**
     * Scores a given sub-model - input is the original input row and also the calculated features.
     * Input and calculated feature are mapped to the input of the sub-model.  
     */
    private double[] score0SubModel(GenModel model, double[] data, double[] calculated) {
        int[] mapping = mappings.get(model.getUUID());
        double[] subModelData = makeModelInput(data, calculated, mapping);
        double[] subModelPreds = new double[model.getPredsSize()];
        return model.score0(subModelData, subModelPreds);
    }

    private Map<String, int[]> makeMappings(GenModel... models) {
        Map<String, int[]> mappings = new HashMap<>();
        for (GenModel model : models) {
            int[] mapping = mapInputNamesToModelNames(model);
            mappings.put(model.getUUID(), mapping);
        }
        return mappings;
    }

    private static double[] makeModelInput(double[] data, double[] calculated, int[] mapping) {
        double[] input = new double[mapping.length];
        for (int i = 0; i < input.length; i++) {
            int p = mapping[i];
            if (p >= 0) {
                input[i] = data[p];
            } else {
                input[i] = calculated[-p - 1];
            }
        }
        return input;
    }
    
    private int[] mapInputNamesToModelNames(GenModel subModel) {
        int[] map = new int[subModel.nfeatures()];
        for (int i = 0; i < map.length; i++) {
            String name = subModel._names[i];
            int p = indexOf(NAMES, name);
            if (p < 0) {
                p = indexOf(CALCULATED, name);
                assert p >= 0 : "'" + name + "' needs to be one of the sub-model features or be a calculated feature.";
                p = -p - 1;
            }
            map[i] = p;
        }
        return map;
    }
    
    private static int indexOf(String[] ar, String element) {
        for (int i = 0; i < ar.length; i++) {
            if (ar[i].equals(element))
                return i;
        }
        return -1;
    }

}

// ===GLM===

%s

// ===GBM===

%s
"""


# Expand the template and embed POJOs for the submodels in a single java file 
def generate_combined_pojo(glm_model, gbm_model):
    glm_pojo_src = get_embeddable_pojo_source(glm_model)
    gbm_pojo_src = get_embeddable_pojo_source(gbm_model)

    results_dir = pyunit_utils.locate("results")
    combined_pojo_name = "Combined_" + glm_model.model_id + "_" + gbm_model.model_id
    combined_pojo_path = os.path.join(results_dir, combined_pojo_name + ".java")
    combined_pojo_src = TEMPLATE % (combined_pojo_name, combined_pojo_name,
                                    glm_model.model_id, gbm_model.model_id, glm_pojo_src, gbm_pojo_src)
    with open(combined_pojo_path, "w") as combined_file:
        combined_file.write(combined_pojo_src)
    return combined_pojo_path


def get_embeddable_pojo_source(model):
    pojo_path = model.download_pojo(path=os.path.join(pyunit_utils.locate("results"), model.model_id + ".java"))
    return make_pojo_embeddable(pojo_path)


# To simplify the workflow we are embedding all models (POJO) in the same Java file
# There can be only one "public" class in a Java file, this method will make the POJO package private
# so that we can put it in the same Java file as the main POJO
def make_pojo_embeddable(pojo_path):
    pojo_lines = []
    with open(pojo_path, 'r') as pojo_file:
        pojo_lines = pojo_file.readlines()
        class_idx = next(filter(lambda idx: pojo_lines[idx].startswith("public class"), range(len(pojo_lines))))
        pojo_lines[class_idx] = pojo_lines[class_idx].replace("public class", "class")  # make package private
        pojo_lines = pojo_lines[class_idx-1:]
    return "".join(pojo_lines)


def generate_and_import_combined_pojo():
    if sys.version_info[0] < 3:  # Python 2
        print("This example needs Python 3.x+")
        return

    weather_orig = h2o.import_file(path=pyunit_utils.locate("smalldata/junit/weather.csv"))
    weather = weather_orig  # working copy

    features = list(set(weather.names) - {"Date", "RainTomorrow", "Sunshine"})
    features.sort()
    response = "RISK_MM"

    glm_model = H2OGeneralizedLinearEstimator()
    glm_model.train(x=features, y=response, training_frame=weather)
    glm_preds = glm_model.predict(weather)

    gbm_model = H2OGradientBoostingEstimator(ntrees=5)
    gbm_model.train(x=features, y=response, training_frame=weather)
    gbm_preds = gbm_model.predict(weather)

    # Drop columns that we will calculate in POJO manually (we will recreate them in POJO to be the exact same)
    weather = weather.drop("ChangeTemp")
    weather = weather.drop("ChangeTempDir")

    combined_pojo_path = generate_combined_pojo(glm_model, gbm_model)
    print("Combined POJO was stored in: " + combined_pojo_path)

    # FIXME: https://h2oai.atlassian.net/browse/PUBDEV-8561 We need to make this work for upload_mojo as well
    pojo_model = h2o.import_mojo(combined_pojo_path)

    # Testing begins

    # Sanity test - test parameterization that delegates to GLM
    weather["Bias"] = 1  # behave like GLM
    pojo_glm_preds = pojo_model.predict(weather)
    assert_frame_equal(pojo_glm_preds.as_data_frame(), glm_preds.as_data_frame())

    # Sanity test - test parameterization that delegates to GBM
    weather["Bias"] = 0  # behave like GBM
    pojo_gbm_preds = pojo_model.predict(weather)
    assert_frame_equal(pojo_gbm_preds.as_data_frame(), gbm_preds.as_data_frame())

    # Test per-segment specific behavior, segments are defined by ChangeWindDirect
    weather["Bias"] = float("NaN")
    for change_wind_dir in weather["ChangeWindDirect"].levels()[0]:
        weather_cwd = weather[weather["ChangeWindDirect"] == change_wind_dir]
        weather_orig_cwd = weather_orig[weather_orig["ChangeWindDirect"] == change_wind_dir]
        pojo_weather_cwd_preds = pojo_model.predict(weather_cwd)
        if change_wind_dir == "c" or change_wind_dir == "l":
            expected = glm_model.predict(weather_orig_cwd) * 2
            assert_frame_equal(pojo_weather_cwd_preds.as_data_frame(), expected.as_data_frame())
        elif change_wind_dir == "n":
            expected = (glm_model.predict(weather_orig_cwd) + gbm_model.predict(weather_orig_cwd)) / 2
            assert_frame_equal(pojo_weather_cwd_preds.as_data_frame(), expected.as_data_frame())
        elif change_wind_dir == "s":
            expected = gbm_model.predict(weather_orig_cwd)
            assert_frame_equal(pojo_weather_cwd_preds.as_data_frame(), expected.as_data_frame())


if __name__ == "__main__":
    pyunit_utils.standalone_test(generate_and_import_combined_pojo)
else:
    generate_and_import_combined_pojo()

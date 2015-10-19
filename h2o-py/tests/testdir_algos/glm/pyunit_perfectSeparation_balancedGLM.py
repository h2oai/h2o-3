

import h2o, tests

def perfectSeparation_balanced():

    
    

    print("Read in synthetic balanced dataset")
    data = h2o.import_file(path=tests.locate("smalldata/synthetic_perfect_separation/balanced.csv"))

    print("Fit model on dataset")
    model = h2o.glm(x=data[["x1", "x2"]], y=data["y"], family="binomial", lambda_search=True,alpha=[0.5], Lambda=[1e-8])

    print("Extract models' coefficients and assert reasonable values (ie. no greater than 50)")
    print("Balanced dataset")
    coef = [c[1] for c in model._model_json['output']['coefficients_table'].cell_values if c[0] != "Intercept"]
    for c in coef:
        assert c < 50, "coefficient is too large"


pyunit_test = perfectSeparation_balanced

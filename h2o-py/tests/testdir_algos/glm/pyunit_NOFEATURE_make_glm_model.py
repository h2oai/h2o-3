

import h2o, tests

def pyunit_make_glm_model():
    # TODO: PUBDEV-1717
    pros = h2o.import_file(tests.locate("smalldata/prostate/prostate.csv"))
    model = h2o.glm(x=pros[["AGE","DPROS","DCAPS","PSA","VOL","GLEASON"]], y=pros["CAPSULE"], family="gaussian", alpha=[0])
    new_betas = {"AGE":0.5, "DPROS":0.5, "DCAPS":0.5, "PSA":0.5, "VOL":0.5, "GLEASON":0.5}

    names = '['
    for n in new_betas.keys(): names += "\""+n+"\","
    names = names[0:len(names)-1]+"]"
    betas = '['

    for b in new_betas.values(): betas += str(b)+","
    betas = betas[0:len(betas)-1]+"]"
    res = h2o.H2OConnection.post_json("MakeGLMModel",model=model._id,names=names,beta=betas)


pyunit_test = pyunit_make_glm_model


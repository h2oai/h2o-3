import h2o
h2o.init()
path = h2o.system_file("prostate.csv")
h2o_df = h2o.import_file(path)
gamma_inverse = h2o.glm(y = "DPROS", x = ["AGE","RACE","CAPSULE","DCAPS","PSA","VOL"], training_frame = h2o_df, family = "gamma", link = "inverse")
gamma_log = h2o.glm(y="DPROS", x = ["AGE","RACE","CAPSULE","DCAPS","PSA","VOL"], training_frame = h2o_df, family = "gamma", link = "log")

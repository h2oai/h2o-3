import h2o
h2o.init()
path = h2o.system_file("prostate.csv")
h2o_df = h2o.import_file(path)
h2o_df["CAPSULE"] = h2o_df["CAPSULE"].asfactor()
h2o_df.summary()

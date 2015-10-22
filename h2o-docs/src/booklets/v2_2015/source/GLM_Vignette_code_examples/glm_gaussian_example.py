import h2o
h2o.init()
path = h2o.system_file("prostate.csv")
h2o_df = h2o.import_file(path)
gaussian_fit = h2o.glm(y = "VOL", x = ["AGE", "RACE", "PSA", "GLEASON"], training_frame = h2o_df, family = "gaussian")

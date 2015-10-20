



def upload_import_small():
    # Connect to a pre-existing cluster
    

    various_datasets = ["smalldata/iris/iris.csv", "smalldata/iris/iris_wheader.csv", "smalldata/prostate/prostate.csv",
                        "smalldata/prostate/prostate_woheader.csv.gz"]

    for dataset in various_datasets:
        uploaded_frame = h2o.upload_file(pyunit_utils.locate(dataset))
        imported_frame = h2o.import_file(pyunit_utils.locate(dataset))

        rows_u, cols_u = uploaded_frame.dim
        rows_i, cols_i = imported_frame.dim

        assert rows_u == rows_i, "Expected same number of rows regardless of method. upload: {0}, import: " \
                                 "{1}.".format(rows_u, rows_i)

        assert cols_u == cols_i, "Expected same number of cols regardless of method. upload: {0}, import: " \
                                 "{1}.".format(cols_u, cols_i)


upload_import_small()

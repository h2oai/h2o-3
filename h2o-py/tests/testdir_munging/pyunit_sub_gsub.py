



def sub_gsub_check():
    # Connect to a pre-existing cluster
    

    frame = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris.csv"), col_types=["numeric","numeric","numeric","numeric","string"])

    # single column (frame)
    frame["C5"] = frame["C5"].gsub("s", "z")
    assert frame[0,4] == "Iriz-zetoza", "Expected 'Iriz-zetoza', but got {0}".format(frame[0,4])

    frame["C5"]= frame["C5"].sub("z", "s")
    assert frame[1,4] == "Iris-zetoza", "Expected 'Iris-zetoza', but got {0}".format(frame[1,4])


    # single column (vec)
    vec = frame["C5"]
    vec = vec.sub("z", "s")
    assert vec[2,0] == "Iris-setoza", "Expected 'Iris-setoza', but got {0}".format(vec[2,0])

    vec = vec.gsub("s", "z")
    assert vec[3,0] == "Iriz-zetoza", "Expected 'Iriz-zetoza', but got {0}".format(vec[3,0])


sub_gsub_check()

import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils





def svd_1_golden():


    print "Importing USArrests.csv data..."
    arrestsH2O = h2o.upload_file(pyunit_utils.locate("smalldata/pca_test/USArrests.csv"))

    print "Compare with SVD"
    fitH2O = h2o.svd(x=arrestsH2O[0:4], nv=4, transform="NONE", max_iterations=2000)

    print "Compare singular values (D)"
    h2o_d = fitH2O._model_json['output']['d']
    r_d = [1419.06139509772, 194.825846110138, 45.6613376308754, 18.0695566224677]
    print "R Singular Values: {0}".format(r_d)
    print "H2O Singular Values: {0}".format(h2o_d)
    for r, h in zip(r_d, h2o_d): assert abs(r - h) < 1e-6, "H2O got {0}, but R got {1}".format(h, r)

    print "Compare right singular vectors (V)"
    h2o_v = h2o.as_list(h2o.get_frame(fitH2O._model_json['output']['v_key']['name']), use_pandas=False)
    h2o_v = zip(*h2o_v)
    h2o_v.pop(0)
    r_v = [[-0.04239181, 0.01616262, -0.06588426, 0.99679535],
           [-0.94395706, 0.32068580, 0.06655170, -0.04094568],
           [-0.30842767, -0.93845891, 0.15496743, 0.01234261],
           [-0.10963744, -0.12725666, -0.98347101, -0.06760284]]
    print "R Right Singular Vectors: {0}".format(r_v)
    print "H2O Right Singular Vectors: {0}".format(h2o_v)
    for rl, hl in zip(r_v, h2o_v):
        for r, h in zip(rl, hl): assert abs(abs(r) - abs(float(h))) < 1e-5, "H2O got {0}, but R got {1}".format(h, r)

    print "Compare left singular vectors (U)"
    h2o_u = h2o.as_list(h2o.get_frame(fitH2O._model_json['output']['u_key']['name']), use_pandas=False)
    h2o_u = zip(*h2o_u)
    h2o_u.pop(0)
    r_u = [[-0.1716251, 0.096325710, 0.06515480, 0.15369551],
           [-0.1891166, 0.173452566, -0.42665785, -0.17801438],
           [-0.2155930, 0.078998111, 0.02063740, -0.28070784],
           [-0.1390244, 0.059889811, 0.01392269, 0.01610418],
           [-0.2067788, -0.009812026, -0.17633244, -0.21867425],
           [-0.1558794, -0.064555293, -0.28288280, -0.11797419]]
    print "R Left Singular Vectors: {0}".format(r_u)
    print "H2O Left Singular Vectors: {0}".format(h2o_u)
    for rl, hl in zip(r_u, h2o_u):
        for r, h in zip(rl, hl): assert abs(abs(r) - abs(float(h))) < 1e-5, "H2O got {0}, but R got {1}".format(h, r)



if __name__ == "__main__":
    pyunit_utils.standalone_test(svd_1_golden)
else:
    svd_1_golden()

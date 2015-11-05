import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils



import random

def getLambdaModel():
	
	

	print("Read data")
	prostate = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))

	myX = ["AGE","RACE","DPROS","DCAPS","PSA","VOL","GLEASON"]
	myY = "CAPSULE"
	family = random.choice(["gaussian","binomial"])
	print(family)

	print("Do lambda search and build models")
	if family == "gaussian":
		model = h2o.glm(x=prostate[myX], y=prostate[myY], family=family, standardize=True, use_all_factor_levels=True, lambda_search=True)
	else:
		model = h2o.glm(x=prostate[myX], y=prostate[myY].asfactor(), family=family, standardize=True, use_all_factor_levels=True, lambda_search=True)

	print("the models were built over the following lambda values: ")
	all_lambdas = model.models(1).lambda_all()
	print(all_lambdas)

	for i in range(10):
		Lambda = random.sample(all_lambdas,1)
		print("For Lambda we get this model:")
		m1 = h2o.getGLMLambdaModel(model.models(random.randint(0,len(model.models()-1)),Lambda=Lambda))
		m1.show()
		print("this model should be same as the one above:")
		m2 = h2o.getGLMLambdaModel(model.models(random.randint(0,len(model.models()-1)),Lambda=Lambda))
		m2.show()
		assert m1==m2, "expected models to be equal"





if __name__ == "__main__":
    pyunit_utils.standalone_test(getLambdaModel)
else:
	getLambdaModel()

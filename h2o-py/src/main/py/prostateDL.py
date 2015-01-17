import h2o

cluster = h2o.H2OConnection()            # connect to existing cluster  
df = h2o.H2OFrame(remote_fname="smalldata/logreg/prostate.csv")

del df['ID']                             # remove ID
df['CAPSULE'] = df['CAPSULE'].asfactor() # make CAPSULE categorical
vol = df['VOL']
vol[vol==0] = None                       # 0 VOL means 'missing'

r = vol.runif()                          # random train/test split
train = df[r< 0.8]
test  = df[r>=0.8]

# See that the data is ready
print train.describe()
print test.describe()

# Run DeepLearning
dl = h2o.H2ODeepLearning(dataset=train,validation_dataset=test,x="CAPSULE",epochs=100,hidden=[20,20,20])

print dl.metrics()

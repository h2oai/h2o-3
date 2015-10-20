import numpy as np

def test_negate():

  a = np.random.randn(100,1).tolist()
  d = h2o.H2OFrame(a)

  assert d[~(d['C1']>0),'C1'] == d[(d['C1']<=0), 'C1']
  assert d[~(d['C1']<=0),'C1'] == d[(d['C1']>0),'C1']

test_negate()

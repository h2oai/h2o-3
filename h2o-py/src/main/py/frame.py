import csv

# A Frame will one day be a pointer to an h2o cloud
# For now, see if we can make (small) 2-D associative arrays,
# and overload everything.
class Frame(object):
  def __init__(self, file):
    with open(file, 'rb') as csvfile:
      __reader__ = csv.reader(csvfile)
      self.vecs=None
      for row in __reader__:
        if self.vecs==None: 
          self.vecs = []
          for name in row:
            self.vecs.append(Vec(name,[]))
        else:
          for i,data in enumerate(row):
            self.vecs[i].append(data)

  def __str__(self):
    return self.vecs.__repr__()

  def __getitem__(self,i):
    return self.vecs[i]


class Vec(object):
  def __init__(self, name, data):
    self.name = name
    self.data = data

  def append(self,data):
    __x__ = data
    try:
      __x__ = float(data)
    except ValueError:
      pass
    self.data.append(__x__)
    
  def __repr__(self):
    return self.name+" "+self.data.__str__()

  def __getitem__(self,i):
    return self.data[i]

  def __add__(self,i):
    if isinstance(i,int):
      return Vec(self.name+"+"+str(i),[i+x for x in self.data])
    if isinstance(i,Vec):
      return Vec(self.name+"+"+i.name,[x+y for x,y in zip(self.data,i.data)])
    raise NotImplementedError


f = Frame("smalldata/iris/iris_wheader.csv")
print f[0].name     # Column header
print f[0][2]       # column 0, row 2 value
print f[0]+2        # Add 2 to every element
print f[0]+f[1]     # Add 2 columns

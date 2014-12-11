import csv

# A Frame will one day be a pointer to an h2o cloud
# For now, see if we can make (small) 2-D associative arrays,
# and overload everything.
class Frame(object):
  def __init__(self, file=None, vecs=None):
    if file != None:
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
    elif vecs!= None:
      self.vecs=vecs

  def __str__(self):
    return self.vecs.__repr__()

  def __getitem__(self,i):
    if isinstance(i,int):
      return self.vecs[i]
    if isinstance(i,str):
      for v in self.vecs:
        if i==v.name:
          return v
      raise ValueError("Name "+i+" not in Frame")
    if isinstance(i,slice):
      return Frame(vecs=self.vecs[i])
    raise NotImplementedError

  def __len__(self):
    return len(self.vecs)

  def __add__(self,i):
    if len(self)==0:
      return self;
    if isinstance(i,Frame):
      if len(i) != len(self):
        raise ValueError("Frame len()="+len(self)+" cannot be broadcast across len(i)="+len(i))
      return Frame(vecs=[x+y for x,y in zip(self.vecs,i.vecs)])
    if isinstance(i,Vec):
      if len(i) != len(self.vecs[0]):
        raise ValueError("Vec len()="+len(self.vecs[0])+" cannot be broadcast across len(i)="+len(i))
      return Frame(vecs=[x+i for x in self.vecs])
    if isinstance(i,int):
      return Frame(vecs=[x+i for x in self.vecs])
    raise NotImplementedError

  def __radd__(self,i):
    return self+i # Add is associative

  def __del__(self):
    print "del of "+str([v.name for v in self.vecs ])

###
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
    if isinstance(i,Vec):
      if len(i) != len(self):
        raise ValueError("Vec len()="+len(self)+" cannot be broadcast across len(i)="+len(i))
      return Vec(self.name+"+"+i.name,[x+y for x,y in zip(self.data,i.data)])
    if isinstance(i,int):
      return Vec(self.name+"+"+str(i),[i+x for x in self.data])
    raise NotImplementedError

  def __radd__(self,i):
    return self+i # Add is associative

  def __len__(self):
    return len(self.data)

  def __del__(self):
    print "del of "+self.name

######################################################
#
#

a = Frame(file="smalldata/iris/iris_wheader.csv")[0:4]

print a[0].name     # Column header
print a[0][2]       # column 0, row 2 value
print a["sepal_len"][2] # Column 0, row 2 value
print a[0]+2        # Add 2 to every element; broadcast a constant
print a[0]+a[1]     # Add 2 columns; broadcast parallel add
print sum(a["sepal_len"])/len(a[0])

try:
  print a["Sepal_len"] # Error, mispelt column name
except ValueError,e:
  pass                          # Expected error

b = Frame(file="smalldata/iris/iris_wheader.csv")[0:4]
c = a+b
d = c+c+sum(a)
e = c+a+1
print e


import h2o

h2o.init()

left = {'name' :('Cliff','Arno','Tomas','Spencer'),
        'age'  :('>dirt','middle','middle','whippersnapper')}
rite = {'name' :('Cliff','Arno','Tomas','Michael'),
        'skill':('hacker','science','linearmath','sparkling')}
l_fr = h2o.H2OFrame(python_obj=left)
r_fr = h2o.H2OFrame(python_obj=rite)

l_fr.describe()
l_fr.show()
r_fr.describe()    
r_fr.show()

print l_fr.merge(r_fr,False,False)
print l_fr.merge(r_fr,True ,False)
print l_fr.merge(r_fr,False,True )
print l_fr.merge(r_fr,True ,True )

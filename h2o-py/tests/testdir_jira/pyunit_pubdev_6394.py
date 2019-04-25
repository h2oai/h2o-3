from h2o import H2OFrame
from tests import pyunit_utils

def pubdev_6394():
    # JUnit tests are to be found in RapidsTest class
    
    data = [['location'],
             ['Ｘ県 Ａ市'],
             ['Ｘ県 Ｂ市'],
             ['Ｘ県 Ｂ市'],
             ['Ｙ県 Ｃ市'],
             ['Ｙ県 Ｃ市']]


    originalFrame = H2OFrame(data, header=True, column_types=['enum'])
    
    assert originalFrame.type('location') == 'enum'
    assert originalFrame.categories() ==  ['Ｘ県 Ａ市', 'Ｘ県 Ｂ市', 'Ｙ県 Ｃ市']
    
    # Reduce cardinality of 'location' column to 2 by reducing existing categorical values to ['Ｘ県','Y県']
    expectedCategories = ['Ｘ県', 'Ｙ県']
    transformedFrame = originalFrame['location'].gsub(' .*', '')
    print(transformedFrame)
    
    assert transformedFrame.ncols == 1
    assert transformedFrame.nrows == originalFrame.nrows
    assert transformedFrame.type('C1') == 'enum'
    assert transformedFrame['C1'].categories() == expectedCategories
    
    # Test gsub without changing the cardinality

    data = [['location'],
            ['ab'],
            ['ac'],
            ['ad'],
            ['ae'],
            ['af']]

    originalFrame = H2OFrame(data, header=True, column_types=['enum'])
    assert originalFrame.type('location') == 'enum'
    assert originalFrame.categories() ==  ['ab', 'ac','ad', 'ae', 'af']

    expectedCategories = ['b', 'c', 'd', 'e', 'f']
    transformedFrame = originalFrame['location'].gsub('a', '')
    print(transformedFrame)
    
    assert transformedFrame.ncols == 1
    assert transformedFrame.nrows == originalFrame.nrows
    assert transformedFrame.type('C1') == 'enum'
    assert transformedFrame['C1'].categories() == expectedCategories
    
if __name__ == "__main__":
    pyunit_utils.standalone_test(pubdev_6394)
else:
    pubdev_6394()

# TODO autogen this file from the H2O manual / sphinx.


# Note: to include links from one content to another, place an <a href='help:foo.bar'> inside the content. To include an external link, include a regular <a href='http://foo.bar'>. Any external links within the content will be automatically updated to open up in a _blank target window.
Steam.Help =
  'model.key':
    title: 'Model Key'
    content: ''
  'model.method':
    title: 'Model Method'
    content: ''
  'model.category':
    title: 'Model Category'
    content: ''
  'model.response_column':
    title: 'Model Response Column'
    content: ''
  'model.category.binomial':
    title: 'Binomial'
    content: ''
  'model.method.deep_learning':
    title: 'Deep Learning'
    content: ''
  'model.method.speed_drf':
    title: 'SpeeDRF'
    content: ''
  'model.method.drf':
    title: 'DRF'
    content: ''
  'model.method.glm':
    title: 'GLM'
    content: ''
  'model.method.gbm':
    title: 'GBM'
    content: ''

do ->
  [h2, p, ul, li, icon, span] = geyser.generate words 'h2 p ul li i.fa.fa-question-circle span'
  Steam.Help.home = 
    title: 'Welcome to H<sub>2</sub>O'
    content: geyser.render [
      p 'H<sub>2</sub>O by 0xdata brings better algorithms to big data. H<sub>2</sub>O is the open source math and machine learning platform for speed and scale. With H<sub>2</sub>O, enterprises can use all of their data (instead of sampling) in real-time for better predictions. Data Scientists can take both simple and sophisticated models to production from H<sub>2</sub>O the same interactive platform used for modeling, within R and JSON. H<sub>2</sub>O is also used as an algorithms library for Making Hadoop Do Math.'
      h2 'User Guide'
      ul [
        li [ icon(), span ' General' ]
        li [ icon(), span ' Data' ]
        li [ icon(), span ' Model' ]
        li [ icon(), span ' Score' ]
        li [ icon(), span ' Administration' ]
      ]
      h2 'Walkthroughs'
      ul [
        li [ icon(), span ' GLM' ]
        li [ icon(), span ' GLM Grid' ]
        li [ icon(), span ' K Means' ]
        li [ icon(), span ' Random Forest' ]
        li [ icon(), span ' PCA' ]
        li [ icon(), span ' GBM' ]
        li [ icon(), span ' GBM Grid' ]
      ]
    ]


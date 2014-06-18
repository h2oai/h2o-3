Steam.FileView = (_, _file) ->
  _isParsed = node$ no
  _parseData = node$ null

  parse = ->
    key = head _file.data.keys
    _.promptParseFiles key, (action, result) ->
      switch action
        when 'confirm'
          _parseData result 
          #TODO might want this inside of _file
          _isParsed yes
          

  data: _file.data
  title: _file.title
  timestamp: _file.timestamp
  parse: parse
  isParsed: _isParsed
  parseData: _parseData
  template: 'file-view'


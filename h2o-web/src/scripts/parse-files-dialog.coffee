Steam.ParseFilesDialog = (_, _sourceKey, _go) ->
  _destinationKey = node$ ''
  _deleteOnDone = node$ no # TODO pick up defaults from metadata
  _errorMessage = node$ ''
  
  confirm = -> 
    _.requestParseFiles _sourceKey, _destinationKey(), _deleteOnDone(), (error, response) ->
      if error
        _errorMessage error.data.errmsg
      else
        _errorMessage ''
        _go 'confirm', response

  cancel = -> _go 'cancel'

  sourceKey: _sourceKey
  destinationKey: _destinationKey
  deleteOnDone: _deleteOnDone
  errorMessage: _errorMessage
  confirm: confirm
  cancel: cancel
  template: 'parse-files-dialog'

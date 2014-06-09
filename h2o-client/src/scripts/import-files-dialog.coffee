Steam.ImportFilesDialog = (_, _go) ->
  _filePath = node$ ''
  _errorMessage = node$ ''
  
  confirm = -> 
    filePath = _filePath()
    _.requestImportFiles filePath, (error, response) ->
      console.log error, response
      if error
        _errorMessage error.data.errmsg
      else
        _errorMessage ''
        _go 'confirm', response

  cancel = -> _go 'cancel'

  filePath: _filePath
  errorMessage: _errorMessage
  confirm: confirm
  cancel: cancel
  template: 'import-files-dialog'

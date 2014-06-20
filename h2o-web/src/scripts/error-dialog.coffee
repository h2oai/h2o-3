Steam.ErrorDialog = (_, _title, _message, error, _go) ->

  #TODO clean this up
  if isObject _message
    message = JSON.stringify _message, null, 2
  else
    message = _message

  confirm = -> _go 'confirm'
  cancel = -> _go 'cancel'

  title: _title
  message: message
  confirm: confirm
  cancel: cancel
  error: error
  template: 'error-dialog'
  


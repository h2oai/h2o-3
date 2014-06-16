Steam.ErrorDialog = (_, _title, _message, error, _go) ->

  confirm = -> _go 'confirm'
  cancel = -> _go 'cancel'

  title: _title
  message: _message
  confirm: confirm
  cancel: cancel
  error: error
  template: 'error-dialog'
  


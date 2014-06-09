Steam.AlertDialog = (_, _title, _message, _go) ->

  confirm = -> _go 'confirm'
  cancel = -> _go 'cancel'

  title: _title
  message: _message
  confirm: confirm
  cancel: cancel
  template: 'alert-dialog'
  

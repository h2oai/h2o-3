Steam.ConfirmDialog = (_, _message, _opts, _go) ->

  confirm = -> _go 'confirm'
  cancel = -> _go 'cancel'

  title: _opts.title or 'Confirm'
  confirmCaption: _opts.confirmCaption or 'Yes'
  cancelCaption: _opts.cancelCaption or 'No'
  message: _message
  confirm: confirm
  cancel: cancel
  template: 'confirm-dialog'
  

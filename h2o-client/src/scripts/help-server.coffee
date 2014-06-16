Steam.HelpServer = (_) ->
  createHelpView = (title, content) ->
    $ "<h1>#{title}</h1><div>#{content}</div>"

  link$ _.man, (id = 'home') ->
    entry = Steam.Help[id]
    if entry
      $help = createHelpView entry.title, entry.content or 'No help available.'
      $('a', $help).each ->
        $self = $ @
        url = $self.attr 'href'
        if url
          if 0 is url.indexOf 'help:'
            # Set link to call back into the help routine.
            $self.removeAttr 'href'
            $self.click -> _.help url.substr 1 + url.indexOf ':'
          else
            # Set link without ids to open in a new window.
            $self.attr 'target', '_blank'
      $help
    else
      createHelpView 'Help not found', 'Could not find help content for this item.'


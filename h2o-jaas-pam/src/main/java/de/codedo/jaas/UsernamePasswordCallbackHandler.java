package de.codedo.jaas;

import java.io.IOException;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;

public class UsernamePasswordCallbackHandler extends Object implements CallbackHandler
{
	private String _user;
	private String _password;

	public UsernamePasswordCallbackHandler(String user, String password)
	{
		super();
		_user = user;
		_password = password;
	}

	@Override
	public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException
	{
		for (Callback callback : callbacks)
		{
			if (callback instanceof NameCallback)
			{
				handleName((NameCallback)callback);
			}
			else if (callback instanceof PasswordCallback)
			{
				handlePassword((PasswordCallback)callback);
			}
			else
			{
				throw new UnsupportedCallbackException(callback);
			}
		}
	}

	private void handleName(NameCallback callback)
	{
		callback.setName(_user);
	}

	private void handlePassword(PasswordCallback callback)
	{
		char[] passwordChars = _password.toCharArray();
		callback.setPassword(passwordChars);
	}
}

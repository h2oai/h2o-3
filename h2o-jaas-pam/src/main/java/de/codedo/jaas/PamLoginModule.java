package de.codedo.jaas;

import java.io.IOException;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.FailedLoginException;
import javax.security.auth.login.LoginException;
import javax.security.auth.spi.LoginModule;

import org.jvnet.libpam.PAM;
import org.jvnet.libpam.PAMException;
import org.jvnet.libpam.UnixUser;

public class PamLoginModule extends Object implements LoginModule
{
	public static final String SERVICE_KEY = "service";

	private PAM _pam;
	private Subject _subject;
	private CallbackHandler _callbackHandler;
	private Map<String, ?> _options;

	private String _username;
	private String _password;

	private boolean _authSucceeded;
	private PamPrincipal _principal;

	public PamLoginModule()
	{
		super();
		_authSucceeded = false;
	}

	@Override
	public void initialize(Subject subject, CallbackHandler callbackHandler, Map<String, ?> sharedState, Map<String, ?> options)
	{
		_subject = subject;
		_callbackHandler = callbackHandler;
		_options = new HashMap<>(options);
	}

	@Override
	public boolean login() throws LoginException
	{
		initializePam();
		obtainUserAndPassword();
		return performLogin();
	}

	private void initializePam() throws LoginException
	{
		String service = (String)_options.get(SERVICE_KEY);
		if (service == null)
		{
			throw new LoginException("Error: PAM service was not defined");
		}
		createPam(service);
	}

	private void createPam(String service) throws LoginException
	{
		try
		{
			_pam = new PAM(service);
		}
		catch (PAMException ex)
		{
			LoginException le = new LoginException("Error initializing PAM");
			le.initCause(ex);
			throw le;
		}
	}

	private void obtainUserAndPassword() throws LoginException
	{
		if (_callbackHandler == null)
		{
			throw new LoginException("Error: no CallbackHandler available  to gather authentication information from the user");
		}

		try
		{
			NameCallback nameCallback = new NameCallback("username");
			PasswordCallback passwordCallback = new PasswordCallback("password", false);

			invokeCallbackHandler(nameCallback, passwordCallback);

			initUserName(nameCallback);
			initPassword(passwordCallback);
		}
		catch (IOException | UnsupportedCallbackException ex)
		{
			LoginException le = new LoginException("Error in callbacks");
			le.initCause(ex);
			throw le;
		}
	}

	private void invokeCallbackHandler(NameCallback nameCallback, PasswordCallback passwordCallback) throws IOException, UnsupportedCallbackException
	{
		Callback[] callbacks = new Callback[2];
		callbacks[0] = nameCallback;
		callbacks[1] = passwordCallback;

		_callbackHandler.handle(callbacks);
	}

	private void initUserName(NameCallback nameCallback)
	{
		_username = nameCallback.getName();
	}

	private void initPassword(PasswordCallback passwordCallback)
	{
		char[] password = passwordCallback.getPassword();
		_password = new String(password);

		passwordCallback.clearPassword();
	}

	private boolean performLogin() throws LoginException
	{
		try
		{
			UnixUser user = _pam.authenticate(_username, _password);
			_principal = new PamPrincipal(user);
			_authSucceeded = true;

			return true;
		}
		catch (PAMException ex)
		{
			LoginException le = new FailedLoginException("Invalid username or password");
			le.initCause(ex);
			throw le;
		}
	}

	@Override
	public boolean commit() throws LoginException
	{
		if (_authSucceeded == false)
		{
			return false;
		}

		if (_subject.isReadOnly())
		{
			cleanup();
			throw new LoginException("Subject is read-only");
		}

		Set<Principal> principals = _subject.getPrincipals();
		if (principals.contains(_principal) == false)
		{
			principals.add(_principal);
		}

		return true;
	}

	@Override
	public boolean abort() throws LoginException
	{
		if (_authSucceeded == false)
		{
			return false;
		}

		cleanup();
		return true;
	}

	@Override
	public boolean logout() throws LoginException
	{
		if (_subject.isReadOnly())
		{
			cleanup();
			throw new LoginException("Subject is read-only");
		}

		_subject.getPrincipals().remove(_principal);

		cleanup();
		return true;
	}

	private void cleanup()
	{
		_authSucceeded = false;
		_username = null;
		_password = null;
		_principal = null;
		_pam.dispose();
	}
}

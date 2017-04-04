package de.codedo.jaas;

import java.security.Principal;
import java.util.Collections;
import java.util.Set;

import org.jvnet.libpam.UnixUser;

public class PamPrincipal extends Object implements Principal
{
	private String _userName;
	private String _gecos;
	private String _homeDir;
	private String _shell;
	private int _uid;
	private int _gid;
	private Set<String> _groups;

	public PamPrincipal(UnixUser user)
	{
		super();
		_userName = user.getUserName();
		_gecos = user.getGecos();
		_homeDir = user.getDir();
		_shell = user.getShell();
		_uid = user.getUID();
		_gid = user.getGID();
		_groups = Collections.unmodifiableSet(user.getGroups());
	}

	@Override
	public String getName()
	{
		return _userName;
	}

	public String getGecos()
	{
		return _gecos;
	}

	public String getHomeDir()
	{
		return _homeDir;
	}

	public String getShell()
	{
		return _shell;
	}

	public int getUid()
	{
		return _uid;
	}

	public int getGid()
	{
		return _gid;
	}

	public Set<String> getGroups()
	{
		return _groups;
	}
}

package nc.ftc.inspection.model;

import java.util.List;

/**
 * This class exists so that I can pass information on a user to the frontend without giving away their salt and hashedpw 
 * @author Trey
 *
 */
public class SimpleUser {
	public String username;
	public String realName;
	public List<String> permissionsList;
	public int role;
	
	public SimpleUser(User user) {
		this.username = user.getUsername();
		this.realName = user.getRealName();
		this.role = user.getType();
		this.permissionsList = user.getPermissionsList();
	}
	public String getUsername() {
		return username;
	}

	public String getRealName() {
		return realName;
	}

	public int getRole() {
		return role;
	}
	public List<String> getPermissionsList() {
		return permissionsList;
	}
}

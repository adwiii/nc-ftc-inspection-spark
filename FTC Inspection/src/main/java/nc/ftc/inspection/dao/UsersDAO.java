/*
 * Copyright (c) 2016-2018 Thomas Barnette and Trey Woodlief
 * All Rights Reserved.
 */

package nc.ftc.inspection.dao;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import nc.ftc.inspection.RemoteUpdater;
import nc.ftc.inspection.Server;
import nc.ftc.inspection.Update;
import nc.ftc.inspection.model.User;

import org.mindrot.jbcrypt.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The UsersDAO contains static methods for all User data access operations. It provides methods 
 * to create, delete, and modify Users and their permissions. 
 *
 */
public class UsersDAO {
	static Logger log ;
	static{
		if(!Server.redirected) {
			Server.redirectError();
		}
		log = LoggerFactory.getLogger(UsersDAO.class);
	}
	
	static final String PASSWORD_SQL = "SELECT hashedPassword, salt, type, realName, changed FROM users where username = ?";
	static final String GET_ALL_SQL = "SELECT username, hashedPassword, salt, type, realName, changed FROM users";
	static final SQL UPDATE_PASSWORD_SQL = new SQL(1, "UPDATE users SET hashedPassword = ?, salt = ?, changed=1 WHERE username = ?");
	static final SQL UPDATE_TYPE_SQL = new SQL(2, "UPDATE users SET type = ?, changed=1 WHERE username = ?");
	static final SQL NEW_USER_SQL = new SQL(3, "INSERT INTO users VALUES (?,?,?,?,?,0)");
	static final String IMPORT_USER_SQL = "INSERT INTO users VALUES (?,?,?,?,?,?);";
	static final String EVENT_ROLE_SQL = "SELECT role FROM roles WHERE username = ? AND eventCode = ?";
	static final SQL ASSIGN_ROLE_SQL = new SQL(4, "INSERT OR REPLACE INTO roles VALUES (?,?,?)");
	static final SQL DELETE_USER = new SQL(5, "DELETE FROM users WHERE username = ?;");
	public static final Map<Integer, SQL> queryMap = new HashMap<>(); 
	private static RemoteUpdater updater = RemoteUpdater.getInstance();
	
	
	/**
	 * Static initialization of the remote updater SQL mapping. This mapping allows the 
	 * sending of SQL Query ID and parameter list to the remote server for updates.
	 */
	static {
		Field[] fields = UsersDAO.class.getDeclaredFields();
		for(Field f : fields) {
			if(f.getType().equals(SQL.class)) {
				SQL s = null;
				try {
					s = (SQL) f.get(null);
				} catch (IllegalArgumentException e) {
					e.printStackTrace();
					continue;
				} catch (IllegalAccessException e) {
					e.printStackTrace();
					continue;
				}
				if(s == null)continue;
				if(queryMap.containsKey(s.id)) {
					System.err.println("DUPLICATE SQL MAPPING IN UsersDAO: "+s.id);
				}
				queryMap.put(s.id, s);
			}
		}
	}

	
	/**
	 * Authenticates a user given the username and plaintext password.
	 * @param username The username to verify.
	 * @return The user object, or null if invalid.
	 */
	public static User authenticate(String username, String pw){
		if (username == null || username.isEmpty()) {
			return null;
		}
		username = username.toLowerCase();
		User user = getUser(username);
		if(user == null) return null;
		String hashedPassword = BCrypt.hashpw(pw, user.getSalt());
		
		//FIXME should this use BCrypt.checkpw()?
		if (hashedPassword.equals(user.getHashedPw())) {
			return user;
		}
		return null;
	}
	
	/**
	 * Updates the password of a given user, and sets changed flag
	 * @param username The username to update
	 * @param oldPw The current pw, used to verify
	 * @param newPw The new password in plaintext
	 * @return True if successful, false if failed (due to either no username found or incorrect current username).
	 */
	public static boolean updatePassword(String username, String oldPw, String newPw, boolean adminOverride){
		if (username == null || username.isEmpty()) {
			return false;
		}
		username = username.toLowerCase();
		try(Connection conn = DriverManager.getConnection(Server.GLOBAL_DB)){
			//if the admin is overriding, then we don't need to authenticate
			User user = adminOverride ? getUser(username) : authenticate(username, oldPw);
			if(user == null){
				return false;
			}
			
			user.setSalt(BCrypt.gensalt());
			user.setHashedPw(BCrypt.hashpw(newPw, user.getSalt()));
			
			PreparedStatement ps = conn.prepareStatement(UPDATE_PASSWORD_SQL.sql);
			ps.setString(1, user.getHashedPw());
			ps.setString(2, user.getSalt());
			ps.setString(3, user.getUsername());
			int affected = ps.executeUpdate();
			if(affected > 1){
				throw new RuntimeException("WE HAD >1 USER ENTRIES -- something messed up");
			}
			updater.enqueue(new Update(null, Update.USER_DB_UPDATE, null, UPDATE_PASSWORD_SQL.id, user.getHashedPw(), user.getSalt(), user.getUsername() ));
			return affected == 1;
		}catch(Exception e){
			log.error("Error updating password for {}", username, e);
		}
		return false;
	}
	
	/**
	 * Adds a new user to the database. Returns true is the operation succeeds. If a user with that name already exists or the current password is
	 * incorrect, returns false. Their password changed flag is set to false.
	 * @param username The new username to add
	 * @param hashedPw Their plaintext password
	 * @param type The type of the new user.
	 * @param realName The user's real name
	 * @return true is successful, false if user already exists or password is incorrect.
	 */
	public static boolean addUser(String username, String password, String realName, int type){
		if (username == null || username.isEmpty()) {
			return false;
		}
		username = username.toLowerCase();
		try(Connection conn = DriverManager.getConnection(Server.GLOBAL_DB)){
			log.info("Adding new user: {}", username);
			PreparedStatement ps = conn.prepareStatement(NEW_USER_SQL.sql);
			String salt = BCrypt.gensalt();
			String hashedPw = BCrypt.hashpw(password, salt);
			ps.setString(1, username);
			ps.setString(2, hashedPw);
			ps.setInt(3, type);
			ps.setString(4, salt);
			ps.setString(5,realName);
			int affected = ps.executeUpdate(); //Existing user will throw SQL exception for PK violation
			if(affected > 1){
				throw new RuntimeException("WTF?");
			}
			updater.enqueue(new Update(null, Update.USER_DB_UPDATE, null, NEW_USER_SQL.id, username, hashedPw,type, salt, realName ));
			return affected == 1;
		}catch(Exception e){
			if(e instanceof SQLException){
				if(e.getMessage().contains("[SQLITE_CONSTRAINT]")){
					return false;
				}
			}
			log.error("Error adding user {}", username, e);
		}
		return false;
	}
	
	/**
	 * Deletes the User with the specified username from the system. If no such user exists, 
	 * nothing occurs. Returns true if the delete was successful, false if failed or no such user.
	 * @param username The username to delete
	 * @return true if delete, false if no change occurred.
	 */
	public static boolean deleteUser(String username) {
		if (username == null || username.isEmpty()) {
			return false;
		}
		try(Connection conn = DriverManager.getConnection(Server.GLOBAL_DB)){
			PreparedStatement ps = conn.prepareStatement(DELETE_USER.sql);
			ps.setString(1, username);
			return 1 == ps.executeUpdate();
		}catch(Exception e){
			log.error("Error deleting user {}", username, e);
			return false;
		}
	}

	/**
	 * Returns a User object for the user with the given username.
	 * @param username The username to query
	 * @return The User object associated with the passd username.
	 */
	public static User getUser(String username) {
		if (username == null || username.isEmpty()) {
			return null;
		}
		username = username.toLowerCase();
		try(Connection conn = DriverManager.getConnection(Server.GLOBAL_DB)){
			PreparedStatement ps = conn.prepareStatement(PASSWORD_SQL);
			ps.setString(1, username);
			ResultSet rs = ps.executeQuery();
			if(!rs.next()){
				log.warn("User lookup failed: {}", username);
				return null;
			}
			return new User(username, rs.getString(1), rs.getString(2), rs.getInt(3), rs.getString(4), rs.getBoolean(5));
			
		}catch(Exception e){
			log.error("Error getting user {}", username, e);
		}
		return null;
	}
	
	/**
	 * Adds the specified role to the User with the specified username.
	 * @param username The username of the User to edit.
	 * @param role The new role to add to the user.
	 * @return true if sucessfully set, false otherwise.
	 */
	public static boolean addRole(String username, int role) {
		if (username == null || username.isEmpty()) {
			return false;
		}
		username = username.toLowerCase();
		User user = getUser(username);
		int newRole = user.getType() | role;
		return updateRole(username, newRole);
	}
	
	/**
	 * Removes the specified from the specified user.
	 * @param username The username to edit
	 * @param role The role to remove from the user.
	 * @return true if removal successfull, false otherwise.
	 */
	public static boolean removeRole(String username, int role) {
		if (username == null || username.isEmpty()) {
			return false;
		}
		username = username.toLowerCase();
		User user = getUser(username);
		int newRole = user.getType() & ~role;
		return updateRole(username, newRole);
	}
	
	/**
	 * Update Role - This private method is used by addRole and removeRole to edit the bit-encoding of the 
	 * Users than stores what permissions they have.
	 * @param username The username being edited
	 * @param newRole The new value of the permission bit-encoding
	 * @return true if update successfull, false otherwise.
	 */
	private static boolean updateRole(String username, int newRole) {
		if (username == null || username.isEmpty()) {
			return false;
		}
		username = username.toLowerCase();
		try(Connection conn = DriverManager.getConnection(Server.GLOBAL_DB)){
			PreparedStatement ps = conn.prepareStatement(UPDATE_TYPE_SQL.sql);
			ps.setString(2, username);
			ps.setInt(1, newRole);
			int affected = ps.executeUpdate();
			if (affected > 1) {
				throw new IllegalArgumentException("We had duplicate usernames, everything is on fire!");
			}
			updater.enqueue(new Update(null, Update.USER_DB_UPDATE, null, UPDATE_TYPE_SQL.id, newRole, username));
			return affected == 1;
		}catch(Exception e){
			log.error("Error updating role for {}", username, e);
		}
		return false;
	}
	
	/**
	 * Returns a list of all users in the system. SystemAdmin users are omitted from the list, as they cannot be edited.
	 * @return The List of Users.
	 */
	public static List<User> getAllUsers() {
		ArrayList<User> users = new ArrayList<User>();
		try(Connection conn = DriverManager.getConnection(Server.GLOBAL_DB)){
			PreparedStatement ps = conn.prepareStatement(GET_ALL_SQL);
			ResultSet rs = ps.executeQuery();
			while(rs.next()) {
				users.add(new User(rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getString(5), rs.getBoolean(6)));
			}
			
		}catch(Exception e){
			log.error("Error getting users list", e);
		}
		return users;
	}
	
	/**
	 * @deprecated Roles are no longer event-specific.
	 * Returns the integer identifying the role of the given user at the specified event.
	 * If the given user is not assigned to work the given event, or the given event/user doesn't exist, returns -1.
	 * @param username The username to query
	 * @param eventCode The event 
	 * @return The encoding of the User's role at the event.
	 */
	public static int getRoleAtEvent(String username, String eventCode){
		if (username == null || username.isEmpty()) {
			return 0;
		}
		username = username.toLowerCase();
		try(Connection conn = DriverManager.getConnection(Server.GLOBAL_DB)){
			PreparedStatement ps = conn.prepareStatement(EVENT_ROLE_SQL);
			ps.setString(1, username);
			ps.setString(2, eventCode);
			ResultSet rs = ps.executeQuery();
			if(!rs.next()){
				return -1;
			}
			return rs.getInt(0);
		}catch(Exception e){
			e.printStackTrace();
			return -1;
		}
	}
	
	/**
	 * @deprecated Roles are no longer event-specific.
	 * Assigns the given role to the specified user for the specified event.
	 * @param eventCode The event to assign the role for.
	 * @param username The username of the user to assign
	 * @param role The role to assign
	 * @return true if assignment successful, false otherwise.
	 */
	public static boolean assignRole(String eventCode, String username, int role){
		if (username == null || username.isEmpty()) {
			return false;
		}
		username = username.toLowerCase();
		try(Connection conn = DriverManager.getConnection(Server.GLOBAL_DB)){
			PreparedStatement ps = conn.prepareStatement(ASSIGN_ROLE_SQL.sql);
			ps.setString(1, username);
			ps.setString(2,  eventCode);
			ps.setInt(3,  role);
			int affected = ps.executeUpdate();
			updater.enqueue(new Update(eventCode, Update.USER_DB_UPDATE, null, ASSIGN_ROLE_SQL.id, username, eventCode, role));
			return affected == 1;
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return false;
	}

	/**
	 * Executes the operation specified by the remote update packet.
	 * @param v Mapping of String parameters to replace in the SQL. 
	 * @param p The parameter list. p[0] is the ID of the SQL query to execute.
	 * @return true if the operation is successful, false otherwise.
	 */
	public static boolean executeRemoteUpdate(Map<String, String> v, Object[] p) {
		if(p == null)return true;
		if(p.length == 0)return true;
		String sql = queryMap.get(new Double(p[0].toString()).intValue()).sql;
		if(v != null) {
			for(Entry<String, String> entry : v.entrySet()) {
				sql = sql.replaceAll(entry.getKey(), entry.getValue());
			}
		}
		log.info("Executing Update (user): "+sql+" "+Arrays.toString(p));
		try (Connection local = DriverManager.getConnection(Server.GLOBAL_DB)){			
			PreparedStatement ps = local.prepareStatement(sql);
			for(int i = 1; i < p.length; i++) {
				//params 1 -indexed, so this works beautifully!
				ps.setObject(i, p[i]);
			}
			ps.execute();
			return true;
		} catch (SQLException e) {
			log.error("ERROR IN REMOTE UPDATE: "+sql, e);
			return false;
		}
	}

	/**
	 * Adds the Users in the passed list to the system. If a username already exists, their information is unaffected.
	 * @param newUsers The List of Users to add.
	 * @return The number of Users added.
	 */
	public static int importUsers(List<User> newUsers) {
		
		try (Connection local = DriverManager.getConnection(Server.GLOBAL_DB)){
			PreparedStatement ps;
			int added = 0;
			for(User user : newUsers) {
				ps = local.prepareStatement(IMPORT_USER_SQL);
				ps.setString(1, user.getUsername());
				ps.setString(2, user.getHashedPw());
				ps.setInt(3, user.getType());
				ps.setString(4, user.getSalt());
				ps.setString(5, user.getRealName());
				ps.setBoolean(6, user.hasChangedPw());
				try {
					added += ps.executeUpdate();
				}catch(Exception e) {
					//user already exists
				}
			}			
			return added;
		} catch (SQLException e) {
			log.error("Error importing users", e);
			return -1;
		}
		
	}

}

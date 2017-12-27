package nc.ftc.inspection.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import nc.ftc.inspection.Server;
import nc.ftc.inspection.model.User;

import org.mindrot.jbcrypt.*;

public class UsersDAO {
	
	static final String PASSWORD_SQL = "SELECT hashedPassword, salt, type, realName, changed FROM users where username = ?";
	static final String UPDATE_PASSWORD_SQL = "UPDATE users SET hashedPassword = ?, salt = ?, changed=1 WHERE username = ?";
	static final String NEW_USER_SQL = "INSERT INTO users VALUES (?,?,?,?,?,0)";
	static final String EVENT_ROLE_SQL = "SELECT role FROM roles WHERE username = ? AND eventCode = ?";
	static final String ASSIGN_ROLE_SQL = "INSERT OR REPLACE INTO roles VALUES (?,?,?)";
	

	
	/**
	 * Authenticates a user given the username and plaintext password.
	 * @param username The username to verify.
	 * @return The user object, or null if invalid.
	 */
	public static User authenticate(String username, String pw){
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
	public static boolean updatePassword(String username, String oldPw, String newPw){
		try(Connection conn = DriverManager.getConnection(Server.GLOBAL_DB)){
			User user = authenticate(username, oldPw);
			if(user == null){
				return false;
			}
			
			user.setSalt(BCrypt.gensalt());
			user.setHashedPw(BCrypt.hashpw(newPw, user.getSalt()));
			
			PreparedStatement ps = conn.prepareStatement(UPDATE_PASSWORD_SQL);
			ps.setString(1, user.getHashedPw());
			ps.setString(2, user.getSalt());
			ps.setString(3, user.getUsername());
			int affected = ps.executeUpdate();
			if(affected > 1){
				throw new RuntimeException("OMFG WE HAD >1 USER ENTRIES -- WE DONE SCREWED UP");
			}
			return affected == 1;
		}catch(Exception e){
			e.printStackTrace();
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
		try(Connection conn = DriverManager.getConnection(Server.GLOBAL_DB)){
			PreparedStatement ps = conn.prepareStatement(NEW_USER_SQL);
			String salt = BCrypt.gensalt();
			String hashedPw = BCrypt.hashpw(password, salt);
			//TODO fill these in.
			ps.setString(1, username);
			ps.setString(2, hashedPw);
			ps.setInt(3, type);
			ps.setString(4, salt);
			ps.setString(5,realName);
			int affected = ps.executeUpdate(); //Existing user will throw SQL exception for PK violation
			if(affected > 1){
				throw new RuntimeException("WTF?");
			}
			return affected == 1;
		}catch(Exception e){
			if(e instanceof SQLException){
				if(e.getMessage().contains("[SQLITE_CONSTRAINT]")){
					return false;
				}
			}
			e.printStackTrace();
		}
		return false;
	}

	
	public static User getUser(String username) {
		if (username == null || username.isEmpty()) {
			return null;
		}
		try(Connection conn = DriverManager.getConnection(Server.GLOBAL_DB)){
			PreparedStatement ps = conn.prepareStatement(PASSWORD_SQL);
			ps.setString(1, username);
			ResultSet rs = ps.executeQuery();
			if(!rs.next()){
				return null;
			}
			return new User(username, rs.getString(1), rs.getString(2), rs.getInt(3), rs.getString(4), rs.getBoolean(5));
			
		}catch(Exception e){
			e.printStackTrace();
		}
		return null;
	}
	
	/**
	 * Returns the integer identifying the role of the given user at the specified event.
	 * If the given user is not assigned to work the given event, or the given event/user doesn't exist, returns -1.
	 * @param username
	 * @param eventCode
	 * @return
	 */
	public static int getRoleAtEvent(String username, String eventCode){
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
	 * Assigns the given role to the specified usr for the specified event.
	 * @param eventCode
	 * @param user The username of the user to assign
	 * @param role
	 * @return
	 */
	public static boolean assignRole(String eventCode, String user, int role){
		try(Connection conn = DriverManager.getConnection(Server.GLOBAL_DB)){
			PreparedStatement ps = conn.prepareStatement(ASSIGN_ROLE_SQL);
			ps.setString(1, user);
			ps.setString(2,  eventCode);
			ps.setInt(3,  role);
			int affected = ps.executeUpdate();
			return affected == 1;
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return false;
	}
	

}

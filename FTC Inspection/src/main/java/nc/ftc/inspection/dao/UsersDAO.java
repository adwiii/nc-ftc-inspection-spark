package nc.ftc.inspection.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import nc.ftc.inspection.Server;


public class UsersDAO {
	
	static final String PASSWORD_SQL = "SELECT hashedPassword FROM users where username = ?";
	static final String UPDATE_PASSWORD_SQL = "UPDATE users SET hashedPassword = ? WHERE username = ? AND hashedPassword = ?";
	static final String NEW_USER_SQL = "INSERT INTO users VALUES (?,?,?)";
	
	/**
	 * Looks up the hashed password for a given user.
	 * @param username The username to retrieved password for.
	 * @return The hashed password, or null if no username.
	 */
	public String getHashedPassword(String username){
		try(Connection conn = DriverManager.getConnection(Server.GLOBAL_DB)){
			PreparedStatement ps = conn.prepareStatement(PASSWORD_SQL);
			ps.setString(1, username);
			ResultSet rs = ps.executeQuery();
			if(!rs.next()){
				return null;
			}
			String hash = rs.getString(1);
			return hash;
		}catch(Exception e){
			e.printStackTrace();
			return null;
		}
	}
	
	/**
	 * Updates the password of a given user.
	 * @param username The username to edit
	 * @param oldHashedPw The current hashed pw, used to verify
	 * @param newHashedPw The hash of the new password
	 * @return True if successful, false if failed (due to either no username found or incorrect current username).
	 */
	public boolean updatePassword(String username, String oldHashedPw, String newHashedPw){
		try(Connection conn = DriverManager.getConnection(Server.GLOBAL_DB)){
			PreparedStatement ps = conn.prepareStatement(UPDATE_PASSWORD_SQL);
			ps.setString(1, newHashedPw);
			ps.setString(2, username);
			ps.setString(3, oldHashedPw);
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
	 * incorrect, returns false. 
	 * @param username The new username to add
	 * @param hashedPw Their hashed password
	 * @param type The type of the new user.
	 * @return true is successful, false if user already exists or password is incorrect.
	 */
	public boolean addUser(String username, String hashedPw, int type){
		try(Connection conn = DriverManager.getConnection(Server.GLOBAL_DB)){
			PreparedStatement ps = conn.prepareStatement(NEW_USER_SQL);
			ps.setString(1, username);
			ps.setString(2, hashedPw);
			ps.setInt(3, type);
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

}

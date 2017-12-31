package nc.ftc.inspection.dao;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.mindrot.jbcrypt.BCrypt;

import com.google.common.io.Files;

import nc.ftc.inspection.Server;
import nc.ftc.inspection.model.Remote;

public class ConfigDAO {
	
	public static final String[] CREATE_CONFIG_DB_SQL = {
			//the keys for posting to this server. Each key can only update one event, + global + users
		"CREATE TABLE clientKeys (event VARCHAR PRIMARY KEY, pw VARCHAR);",
		//keys for servers I post to.
		"CREATE TABLE remotes (host VARCHAR PRIMARY KEY, pw VARCHAR);"
		//any other local config info can go here
	};
	
	static final String GET_KEY = "SELECT pw FROM clientKeys WHERE event=?";
	static final String GET_REMOTES = "SELECT * FROM remotes";
	static final String SAVE_KEY = "INSERT OR REPLACE INTO clientKeys VALUES(?,?)";
	
	static final String SAVE_REMOTE = "INSERT OR REPLACE INTO remotes VALUES(?,?)";
	
	public static void runStartupCheck() {
		boolean exists = new File(Server.DB_PATH+"config.db").exists();
		if(!exists) {
			try(Connection conn = DriverManager.getConnection(Server.CONFIG_DB)){
				Statement sql = conn.createStatement();
				sql.addBatch(CREATE_CONFIG_DB_SQL[0]);
				for(int i = 1; i < CREATE_CONFIG_DB_SQL.length; i++){
					sql.addBatch(CREATE_CONFIG_DB_SQL[i]);
				}
				sql.executeBatch();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}	
	
	/**
	 * 
	 * @param event
	 * @param key The unhashed key
	 * @return
	 */
	public static boolean checkKey(String event, String key) {
		try(Connection conn = DriverManager.getConnection(Server.CONFIG_DB)){
			PreparedStatement ps = conn.prepareStatement(GET_KEY);
			ps.setString(1, event);
			ResultSet rs = ps.executeQuery();
			if(!rs.next())return false;
			return BCrypt.checkpw(key, rs.getString(1));
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return false;
	}
	
	public static boolean saveKey(String event, String key) {
		try(Connection conn = DriverManager.getConnection(Server.CONFIG_DB)){
			PreparedStatement ps = conn.prepareStatement(SAVE_KEY);
			ps.setString(1, event);
			ps.setString(2, BCrypt.hashpw(key, BCrypt.gensalt()));
			return ps.executeUpdate() == 1;
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return false;
	}
	
	public static boolean saveRemote(String host, String key) {
		try(Connection conn = DriverManager.getConnection(Server.CONFIG_DB)){
			PreparedStatement ps = conn.prepareStatement(SAVE_KEY);
			ps.setString(1, host);
			ps.setString(2, key);
			return ps.executeUpdate() == 1;
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return false;
	}
	
	
	public static List<Remote> getRemotes() {
		try(Connection conn = DriverManager.getConnection(Server.CONFIG_DB)){
			PreparedStatement ps = conn.prepareStatement(GET_REMOTES);
			ResultSet rs = ps.executeQuery();
			List<Remote> list = new ArrayList<>();
			while(rs.next()) {
				list.add(new Remote(rs.getString(1), rs.getString(2)));
			}
			return list;
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return null;
	}

}

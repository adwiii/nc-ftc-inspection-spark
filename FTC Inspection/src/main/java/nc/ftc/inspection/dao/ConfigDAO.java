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

import nc.ftc.inspection.Key;
import nc.ftc.inspection.Server;
import nc.ftc.inspection.model.Remote;

public class ConfigDAO {
	//NOTHING IN HERE SHOULD BE REMOTE UPDATABLE!
	public static final String[] CREATE_CONFIG_DB_SQL = {
			//the keys for posting to this server. Each key can only update one event, + global + users
		"CREATE TABLE clientKeys (event VARCHAR PRIMARY KEY, pw VARCHAR, verified BOOLEAN);",
		//keys for servers I post to.
		"CREATE TABLE remotes (host VARCHAR, pw VARCHAR, event VARCHAR, PRIMARY KEY (host, event));",
		
	//	"CREATE TABLE failed (host VARCHAR, event VARCHAR, id INTEGER, event VARCHAR, type INTEGER, map VARCHAR, param VARCHAR, PRIMARY KEY (host, event);"
		//any other local config info can go here
	};
	
	static final String GET_KEYS = "SELECT code, verified FROM global.events LEFT JOIN clientKeys ON code=event;";
	static final String GET_KEY = "SELECT pw FROM clientKeys WHERE event=?";
	static final String GET_REMOTES = "SELECT * FROM remotes";
	static final String GET_REMOTE_KEY = "SELECT pw FROM remotes WHERE host=? AND event=?";
	static final String SAVE_KEY = "INSERT OR REPLACE INTO clientKeys VALUES(?,?, ?)";	
	static final String SAVE_REMOTE = "INSERT OR REPLACE INTO remotes VALUES(?,?, ?)";
	static final String DELETE_KEY = "DELETE FROM clientKeys WHERE event=?";
	static final String DELETE_REMOTE = "DELETE FROM remotes WHERE host=? AND event=?";
	static final String IS_VERIFIED = "SELECT pw,verified FROM clientKeys WHERE event=?";
	static final String VERIFY = "UPDATE clientKeys SET verified=1 WHERE event=?";
	//static final String SAVE_FAILED = "INSERT INTO ";
	
	public static void runStartupCheck() {
		boolean exists = new File(Server.DB_PATH+"config.db").exists();
		if(!exists) {
			System.out.println("Creating config database!");
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
			ps.setBoolean(3,  false);
			return ps.executeUpdate() == 1;
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return false;
	}
	
	public static boolean saveRemote(String host, String key, String event) {
		try(Connection conn = DriverManager.getConnection(Server.CONFIG_DB)){
			PreparedStatement ps = conn.prepareStatement(SAVE_REMOTE);
			ps.setString(1, host);
			ps.setString(2, key);
			ps.setString(3, event);
			return ps.executeUpdate() == 1;
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return false;
	}
	
	public static boolean deleteRemote(String host, String event) {
		try(Connection conn = DriverManager.getConnection(Server.CONFIG_DB)){
			PreparedStatement ps = conn.prepareStatement(DELETE_REMOTE);
			ps.setString(1, host);
			ps.setString(2, event);
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
				list.add(new Remote(rs.getString(1), rs.getString(2), rs.getString(3)));
			}
			return list;
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	public static String getRemoteKey(String host, String event) {
		try(Connection conn = DriverManager.getConnection(Server.CONFIG_DB)){
			PreparedStatement ps = conn.prepareStatement(GET_REMOTE_KEY);
			ps.setString(1, host);
			ps.setString(2, event);
			ResultSet rs = ps.executeQuery();
			if(!rs.next())return null;
			return rs.getString(1);
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	public static String verifyClientKey(String event, String key) {
		try(Connection conn = DriverManager.getConnection(Server.CONFIG_DB)){
			PreparedStatement ps = conn.prepareStatement(IS_VERIFIED);
			ps.setString(1,  event);
			ResultSet rs = ps.executeQuery();
			if(!rs.next()) {
				return "No key for event "+event;
			}
			if(rs.getBoolean(2)) {
				return "Already verified";
			}
			if(BCrypt.checkpw(key, rs.getString(1))) {
				ps = conn.prepareStatement(VERIFY);
				ps.setString(1, event);
				if(ps.executeUpdate() == 1) {
					return "OK";
				}
				return "Error validating key";
			}
			return "Invalid key";
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return "ERROR";
	}
	
	public static boolean deleteClientKey(String event) {
		try(Connection conn = DriverManager.getConnection(Server.CONFIG_DB)){
			PreparedStatement ps = conn.prepareStatement(DELETE_KEY);
			ps.setString(1, event);
			return ps.executeUpdate() == 1;
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return false;
	}
	
	public static List<Key> getKeys(){
		try(Connection conn = DriverManager.getConnection(Server.CONFIG_DB)){
			Statement s = conn.createStatement();
			s.execute(EventDAO.ATTACH_GLOBAL.replaceAll(":path", Server.DB_PATH));
			ResultSet rs = s.executeQuery(GET_KEYS);
			List<Key> keys = new ArrayList<Key>();
			while(rs.next()) {
				Object verified = rs.getObject(2); //SQL NULL instead of true/false means not created
				keys.add(new Key(rs.getString(1), rs.getBoolean(2), verified != null));
			}
			return keys;
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return null;
	}
}

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nc.ftc.inspection.RemoteUpdater;
import nc.ftc.inspection.Server;
import nc.ftc.inspection.Update;
import nc.ftc.inspection.model.Feedback;
import nc.ftc.inspection.model.Team;

public class GlobalDAO {
	
	private static final String MASTER_TEAM_LIST_SQL = "SELECT * FROM teamInfo";
	private static final SQL NEW_TEAM_SQL = new SQL(1,"INSERT INTO teamInfo VALUES (?, ?, NULL)");
	private static final SQL EDIT_TEAM_SQL = new SQL(2,"INSERT OR REPLACE INTO teamInfo VALUES (?,?,?)"); 
	private static final String GET_TEAM_NAME_SQL = "SELECT name FROM teamInfo WHERE number = ?";
	private static final String ADD_FEEDBACK_SQL = "INSERT INTO feedback VALUES (?, ?);";
	private static final String GET_FEEDBACK_SQL = "SELECT * FROM feedback;";
	private static final SQL ADD_NEW_TEAM_SQL = new SQL(3,"INSERT INTO teamInfo VALUES (?, ?, ?);");
	private static final SQL ADD_NEW_TEAMS_OVERWRITE = new SQL(4,"INSERT OR REPLACE INTO teamInfo VALUES (?, ?, ?);");
	public static final Map<Integer, SQL> queryMap = new HashMap<>(); 
	private static RemoteUpdater updater = RemoteUpdater.getInstance();
	
	static Logger log = LoggerFactory.getLogger(GlobalDAO.class);
	static {
		Field[] fields = GlobalDAO.class.getDeclaredFields();
		System.out.println(fields.length);
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
					System.err.println("DUPLICATE SQL MAPPING IN GlobalDAO: "+s.id);
				}
				queryMap.put(s.id, s);
			}
		}
	}
	public static String getTeamName(int team) {
		if(team < 0) {
			return "Test Team "+(team * -1);
		}
		try(Connection global = DriverManager.getConnection(Server.GLOBAL_DB)){
			PreparedStatement ps = global.prepareStatement(GET_TEAM_NAME_SQL);
			ps.setInt(1,  team);
			ResultSet rs = ps.executeQuery();
			if(!rs.next())return "";
			return rs.getString(1);
		} catch (SQLException e) {
			log.error("Error getting team name for {}", team, e);
		}
		return null;
	}
	
	public static List<Team> getMasterTeamList(){
		try(Connection global = DriverManager.getConnection(Server.GLOBAL_DB)){
			PreparedStatement ps = global.prepareStatement(MASTER_TEAM_LIST_SQL);
			ResultSet rs = ps.executeQuery();
			List<Team> list = new ArrayList<>();
			while(rs.next()){
				list.add(new Team(rs.getInt(1), rs.getString(2)));
			}
			return list;
		} catch (SQLException e) {
			log.error("Error getting master team list", e);
		}
		return null;
	}
	
	public static boolean addNewTeam(int number, String name){
		try(Connection global = DriverManager.getConnection(Server.GLOBAL_DB)){
			PreparedStatement ps = global.prepareStatement(NEW_TEAM_SQL.sql);
			ps.setInt(1,  number);
			ps.setString(2,  name);
			int affected = ps.executeUpdate();
			updater.enqueue(new Update(null, Update.GLOBAL_DB_UPDATE, null, NEW_TEAM_SQL.id, number, name));
			return affected == 1;
		} catch (SQLException e) {
			log.error("Error adding new team", e);
		}
		return false;
	}
	
	public static boolean editTeamName(int number, String name, String location){
		try(Connection global = DriverManager.getConnection(Server.GLOBAL_DB)){
			log.info("Setting team info for {}: name = {}, loc = {}", number, name, location);
			PreparedStatement ps = global.prepareStatement(EDIT_TEAM_SQL.sql);
			ps.setInt(1,  number);
			ps.setString(2,  name);
			ps.setString(3,  location);
			int affected = ps.executeUpdate();
			updater.enqueue(new Update(null, Update.GLOBAL_DB_UPDATE, null, EDIT_TEAM_SQL.id, name, number));
			return affected == 1;
		} catch (SQLException e) {
			log.error("Error editing team {}", number, e);
		}
		return false;
	}
	
	public static boolean saveFeedback(String feedback) {
		try(Connection global = DriverManager.getConnection(Server.GLOBAL_DB)){
			PreparedStatement ps = global.prepareStatement(ADD_FEEDBACK_SQL);
			ps.setString(1,  feedback);
			ps.setLong(2, System.currentTimeMillis());
			int affected = ps.executeUpdate();
			return affected == 1;
		} catch (SQLException e) {
			log.error("Error saving feedback");
		}
		return false;
	}
	
	public static List<Feedback> getFeedback(){
		try(Connection global = DriverManager.getConnection(Server.GLOBAL_DB)){
			PreparedStatement ps = global.prepareStatement(GET_FEEDBACK_SQL);
			ResultSet rs = ps.executeQuery();
			List<Feedback> list = new ArrayList<>();
			while(rs.next()){
				list.add(new Feedback(rs.getString(1), rs.getLong(2)));
			}
			return list;
		} catch (SQLException e) {
			log.error("Error getting feedback");
		}
		return null;
	}
	
	public static boolean executeRemoteUpdate(Map<String, String> v, Object[] p) {
		if(p == null)return true;
		if(p.length == 0)return true;
		String sql = queryMap.get(new Double(p[0].toString()).intValue()).sql;
		if(v != null) {
			for(Entry<String, String> entry : v.entrySet()) {
				sql = sql.replaceAll(entry.getKey(), entry.getValue());
			}
		}
		log.info("Executing Update (global): "+sql+" "+Arrays.toString(p));
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

	public static int addNewTeams(List<Team> teams, boolean overwrite) {
		try (Connection local = DriverManager.getConnection(Server.GLOBAL_DB)){			
			PreparedStatement ps;
			int added = 0;
			for(Team t : teams) {
				try {
					log.info("Adding team {}", t.getNumber());
					ps = local.prepareStatement(overwrite ? ADD_NEW_TEAMS_OVERWRITE.sql : ADD_NEW_TEAM_SQL.sql);
					ps.setInt(1, t.getNumber());
					ps.setString(2, t.getName());
					ps.setString(3, t.getLocation());
					added += ps.executeUpdate();
					updater.enqueue(new Update(null, Update.GLOBAL_DB_UPDATE, null, overwrite ? ADD_NEW_TEAMS_OVERWRITE.id : ADD_NEW_TEAM_SQL.id, t.getNumber(), t.getName(), t.getLocation()));
				}catch(Exception e) {
					//team already in system.
				}
			}
			return added;
		} catch (SQLException e) {
			log.error("Error importing teams!", e);
			return -1;
		}
	}
	
	public static int addNewTeams(List<Team> teams) {
		return addNewTeams(teams, false);
	}

}

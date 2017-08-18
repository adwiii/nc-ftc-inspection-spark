package nc.ftc.inspection.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import nc.ftc.inspection.Server;
import nc.ftc.inspection.model.Event;

public class EventDAO {
	public static final SimpleDateFormat EVENT_DATE_FORMAT = new SimpleDateFormat("MM/dd/yyyy");
	static final String CREATE_EVENT_SQL = "INSERT INTO events(code, name, [date], status) VALUES(?,?,?,0)";
	static final String[] CREATE_EVENT_DB_SQL ={ 
											"ATTACH DATABASE ':code.db' AS local;" , 
											"CREATE TABLE local.teams(number INTEGER PRIMARY KEY);",
											"CREATE TABLE local.formData (formID VARCHAR(2), type VARCHAR(1), row INTEGER, columns INTEGER, description VARCHAR, rule VARCHAR(128), PRIMARY KEY (formID, row));",
											"CREATE TABLE local.formRows (cbIndex INTEGER, formID VARCHAR(2), row INTEGER, PRIMARY KEY(cbIndex, formID), FOREIGN KEY(formID, row) references formData(formId, row));",
											"CREATE TABLE local.formStatus(team INTEGER REFERENCES teams(number), formID VARCHAR(2), cbIndex INTEGER, status BOOLEAN, PRIMARY KEY (team, formID, cbIndex), FOREIGN KEY (formID, cbIndex) REFERENCES formRows(formID, cbIndex));" ,
											"CREATE TABLE local.formComments(team INTEGER REFERENCES teams(number), formID VARCHAR(2), comment VARCHAR, PRIMARY KEY (team, formID));",
											"CREATE TABLE local.preferences (id VARCHAR, value VARCHAR);",
											"CREATE TABLE local.inspection (team INTEGER PRIMARY KEY REFERENCES teams(number), ci BOOLEAN, hw BOOLEAN, sw BOOLEAN, fld BOOLEAN, sc BOOLEAN);",
											"INSERT INTO local.formData SELECT * FROM formData;",
											"INSERT INTO local.formRows SELECT * FROM formRows;"										
											};//TODO create each local table and populate it with SELECT INTO
	static final String SET_EVENT_STATUS_SQL = "UPDATE events SET STATUS = ? WHERE code = ?;";
	static final String ADD_TEAM_SQL = "INSERT INTO teams VALUES (?);";
	static final String GET_EVENT_LIST_SQL = "SELECT * FROM events;";
	static final String GET_EVENT_SQL = "SELECT * FROM events WHERE code = ?;";
	
	protected static Connection getLocalDB(String code) throws SQLException{
		return DriverManager.getConnection(Server.DB_PATH+code);
	}
	
	public static List<Event> getEvents(){
		try(Connection conn = DriverManager.getConnection(Server.GLOBAL_DB)){
			PreparedStatement ps = conn.prepareStatement(CREATE_EVENT_SQL);
			ResultSet rs = ps.executeQuery();
			List<Event> result = new ArrayList<Event>();
			while(rs.next()){
				Event e = new Event(rs.getString(0), rs.getString(1), rs.getInt(3), rs.getDate(2));
				result.add(e);
			}
			return result;
		}catch(Exception e){
			e.printStackTrace();
		}
		return null;
	}
	
	public static Event getEvent(String code){
		try(Connection conn = DriverManager.getConnection(Server.GLOBAL_DB)){
			PreparedStatement ps = conn.prepareStatement(CREATE_EVENT_SQL);
			ps.setString(1, code);
			ResultSet rs = ps.executeQuery();
			if(!rs.next())return null;
			return new Event(rs.getString(0), rs.getString(1), rs.getInt(3), rs.getDate(2));
		}catch(Exception e){
			e.printStackTrace();
		}
		return null;
	}
	
	public static boolean createEvent(String code, String name, java.sql.Date date){
		try(Connection conn = DriverManager.getConnection(Server.GLOBAL_DB)){
			PreparedStatement ps = conn.prepareStatement(CREATE_EVENT_SQL);
			ps.setString(1, code);
			ps.setString(2, name);
			ps.setDate(3, date);
			int affected = ps.executeUpdate();
			return affected == 1;
		}catch(Exception e){
			e.printStackTrace();
			return false;
		}
	}
	
	public static boolean setEventStatus(String code, int status){
		try(Connection conn = DriverManager.getConnection(Server.GLOBAL_DB)){
			PreparedStatement ps = conn.prepareStatement(SET_EVENT_STATUS_SQL);
			ps.setInt(1, status);
			ps.setString(2, code);
			int affected = ps.executeUpdate();
			return affected == 1;
		}catch(Exception e){
			e.printStackTrace();
			return false;
		}
	}
	
	public static boolean addTeamToEvent(int team, String eventCode){
		try(Connection conn = getLocalDB(eventCode)){
			PreparedStatement ps = conn.prepareStatement(ADD_TEAM_SQL);
			ps.setInt(1, team);
			int affected = ps.executeUpdate();
			return affected == 1;
		}catch(Exception e){
			e.printStackTrace();
			return false;
		}
	}
	
	public static boolean createEventDatabase(String code){
		//if(getEvent(code) = null)return false;
		try(Connection global = DriverManager.getConnection(Server.GLOBAL_DB)){
			//global.setAutoCommit(false);
			Statement sql = global.createStatement();//CREATE_EVENT_DB_SQL.replaceAll(":code", Server.DB_PATH+ code));
			sql.addBatch(CREATE_EVENT_DB_SQL[0].replaceAll(":code", Server.DB_PATH+ code));
			for(int i = 1; i < CREATE_EVENT_DB_SQL.length; i++){
				sql.addBatch(CREATE_EVENT_DB_SQL[i]);
			}
			sql.executeBatch();
			//global.setAutoCommit(true);
			return true;
		} catch(Exception e){
			e.printStackTrace();
		}
		return false;
	}
	
	
}

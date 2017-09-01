package nc.ftc.inspection.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nc.ftc.inspection.Server;
import nc.ftc.inspection.model.Event;
import nc.ftc.inspection.model.FormRow;

public class EventDAO {
	public static final SimpleDateFormat EVENT_DATE_FORMAT = new SimpleDateFormat("MM/dd/yyyy");
	static final String CREATE_EVENT_SQL = "INSERT INTO events(code, name, [date], status) VALUES(?,?,?,0)";
	static final String[] CREATE_EVENT_DB_SQL ={ 
											"ATTACH DATABASE ':code.db' AS local;" , 
											"CREATE TABLE local.teams(number INTEGER PRIMARY KEY);",
											"CREATE TABLE local.formRows (formID VARCHAR(2), type INTEGER, row INTEGER, columnCount INTEGER, description VARCHAR, rule VARCHAR(128), PRIMARY KEY (formID, row));",
											"CREATE TABLE local.formItems (itemIndex INTEGER, formID VARCHAR(2), row INTEGER, label VARCHAR, req TINYINT, PRIMARY KEY(itemIndex, formID), FOREIGN KEY(formID, row) references formRows(formID, row));",
											"CREATE TABLE local.formStatus(team INTEGER REFERENCES teams(number), formID VARCHAR(2), cbIndex INTEGER, status BOOLEAN, PRIMARY KEY (team, formID, cbIndex), FOREIGN KEY (formID, cbIndex) REFERENCES formRows(formID, itemIndex));" ,
											"CREATE TABLE local.formComments(team INTEGER REFERENCES teams(number), formID VARCHAR(2), comment VARCHAR, PRIMARY KEY (team, formID));",
											"CREATE TABLE local.preferences (id VARCHAR, value VARCHAR);",
											"CREATE TABLE local.inspection (team INTEGER PRIMARY KEY REFERENCES teams(number), ci BOOLEAN, hw BOOLEAN, sw BOOLEAN, fld BOOLEAN, sc BOOLEAN);",
											"INSERT INTO local.formRows SELECT * FROM formRows;",
											"INSERT INTO local.formItems SELECT * FROM formItems;"										
											};//TODO create each local table and populate it with SELECT INTO
	static final String SET_EVENT_STATUS_SQL = "UPDATE events SET STATUS = ? WHERE code = ?;";
	static final String ADD_TEAM_SQL = "INSERT INTO teams VALUES (?);";
	static final String GET_EVENT_LIST_SQL = "SELECT * FROM events;";
	static final String GET_EVENT_SQL = "SELECT * FROM events WHERE code = ?;";
	static final String GET_FORM_ROWS = "SELECT * FROM formRows WHERE formID = ? ORDER BY row";
	static final String GET_FORM_ITEMS = "SELECT items.row, items.itemIndex, items.label, items.req :teamColumns FROM formItems items";
	static final String TEAM_JOINS = " LEFT JOIN formStatus ? ON ?.cbIndex = items.itemIndex AND team = ? AND items.formID = ?.formID";
	static final String FORM_ITEMS_WHERE = " WHERE formID = ?";
	
	protected static Connection getLocalDB(String code) throws SQLException{
		return DriverManager.getConnection("jdbc:sqlite:"+Server.DB_PATH+code+".db");
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
			Statement sql = global.createStatement();
			sql.addBatch(CREATE_EVENT_DB_SQL[0].replaceAll(":code", Server.DB_PATH+ code));
			for(int i = 1; i < CREATE_EVENT_DB_SQL.length; i++){
				sql.addBatch(CREATE_EVENT_DB_SQL[i]);
			}
			sql.executeBatch();
			return true;
		} catch(Exception e){
			e.printStackTrace();
		}
		return false;
	}
	
	public static List<FormRow> getForm(String eventCode, String formCode, int ... teams){
		try(Connection local = getLocalDB(eventCode)){
			PreparedStatement ps = local.prepareStatement(GET_FORM_ROWS);
			ps.setString(1, formCode);
			ResultSet rs = ps.executeQuery();
			//Rows returned ordered by row, so list will be in order
			List<FormRow> form = new ArrayList<FormRow>();
			Map<Integer, FormRow> map= new HashMap<Integer, FormRow>();
			
			while(rs.next()){
				FormRow f = new FormRow(rs.getString(1), rs.getInt(2), rs.getInt(3), rs.getInt(4) * (teams.length > 0 ? teams.length : 1), rs.getString(5), rs.getString(6));
				form.add(f);
				map.put(f.getRow(), f);
			}
			String teamColumns = "";
			String teamJoins = "";
			char c = 'a';
			for(int i = 0; i < teams.length; i++, c++){
				teamColumns += ", "+c+".status"+teams[i];
				teamJoins += TEAM_JOINS;
			}
			//row, index, label, req, [#.status, #.status, ...]
			ps = local.prepareStatement(GET_FORM_ITEMS.replaceAll(":teamColumns", teamColumns) + teamJoins + FORM_ITEMS_WHERE );
			c = 'a';
			int i = 0;
			for(; i < teams.length; i += 4, c++){
				ps.setString(i + 1, c + "");
				ps.setString(i + 2, c + "");
				ps.setInt(i + 3, teams[i]);
				ps.setString(i + 4, c + "");
			}
			ps.setString(i + 1, formCode);
			rs = ps.executeQuery();
			while(rs.next()){
				int row = rs.getInt(1);
				FormRow fr = map.get(row);
				switch(fr.getType()){
				case FormRow.HEADER:
					System.out.println("HEADER:" + row+" "+rs.getString(3));
					fr.addItemData(rs.getString(3));
					break;
				case FormRow.NON_HEADER:
					int itemId = rs.getInt(2);
					if(teams.length == 0){
						fr.addItemData(itemId, rs.getInt(4), false);
					}
					for(int ti = 0; ti < teams.length; ti++){
						fr.addItemData(itemId + "_" + teams[ti], rs.getInt(4), rs.getBoolean(5 + ti));
					}
					break;
				}				
			}
			return form;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	
	
	
}

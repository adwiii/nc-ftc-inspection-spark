package nc.ftc.inspection.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.List;

import nc.ftc.inspection.Server;
import nc.ftc.inspection.model.Event;

public class EventDAO {
	public static final SimpleDateFormat EVENT_DATE_FORMAT = new SimpleDateFormat("MM/dd/yyyy");
	static final String CREATE_EVENT_SQL = "INSERT INTO events(code, name, [date], status) VALUES(?,?,?,0)";
	static final String CREATE_EVENT_DB_SQL = "ATTACH DATABASE ?.db AS local;" + 
											"SELECT INTO ?.forms FROM forms";//TODO create each local table and populate it with SELECT INTO
	public static List<Event> getEvents(){
		return null;
	}
	
	public static Event getEvent(String code){
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
	
	public static boolean createEventDatabase(String code){
		try(Connection global = DriverManager.getConnection(Server.GLOBAL_DB)){
			PreparedStatement ps = global.prepareStatement(CREATE_EVENT_DB_SQL);
			//fill parameters for each table with eventcode.
			for(int i = 0; i < 2; i++){
				ps.setString(i + 1, code);				
			}
			ps.executeUpdate();
			return true;
		} catch(Exception e){
			e.printStackTrace();
		}
		return false;
	}
	
	
}

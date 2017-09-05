package nc.ftc.inspection.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import nc.ftc.inspection.Server;
import nc.ftc.inspection.model.Team;

public class GlobalDAO {
	
	private static final String MASTER_TEAM_LIST_SQL = "SELECT * FROM teamInfo";
	private static final String NEW_TEAM_SQL = "INSERT INTO teamInfo VALUES (?, ?)";
	private static final String EDIT_TEAM_SQL = "UPDATE teamInfo SET name = ? WHERE number = ?"; 
	
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
			e.printStackTrace();
		}
		return null;
	}
	
	public static boolean addNewTeam(int number, String name){
		try(Connection global = DriverManager.getConnection(Server.GLOBAL_DB)){
			PreparedStatement ps = global.prepareStatement(NEW_TEAM_SQL);
			ps.setInt(1,  number);
			ps.setString(2,  name);
			int affected = ps.executeUpdate();
			return affected == 1;
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return false;
	}
	
	public static boolean editTeamName(int number, String name){
		try(Connection global = DriverManager.getConnection(Server.GLOBAL_DB)){
			PreparedStatement ps = global.prepareStatement(EDIT_TEAM_SQL);
			ps.setString(1,  name);
			ps.setInt(2,  number);
			int affected = ps.executeUpdate();
			return affected == 1;
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return false;
	}

}

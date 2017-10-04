package nc.ftc.inspection.dao;

import java.sql.CallableStatement;
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
import java.util.stream.Collector;
import java.util.stream.Collectors;

import nc.ftc.inspection.Server;
import nc.ftc.inspection.model.Alliance;
import nc.ftc.inspection.model.EventData;
import nc.ftc.inspection.model.FormRow;
import nc.ftc.inspection.model.Match;
import nc.ftc.inspection.model.Team;

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
											"CREATE TABLE local.inspectionStatus (team INTEGER PRIMARY KEY REFERENCES teams(number), ci TINYINT, hw TINYINT, sw TINYINT, fd TINYINT, sc TINYINT);",
											"INSERT INTO local.formRows SELECT * FROM formRows;",
											"INSERT INTO local.formItems SELECT * FROM formItems;",
											
											//TODO where to put cards - qualsData & qualsResults?  matches- red1Card, red2Card, blue1Card, blue2Card?
											"CREATE TABLE local.quals(match INTEGER PRIMARY KEY, red1 INTEGER REFERENCES teams(number), red1S BOOLEAN, red2 INTEGER REFERENCES teams(number), red2S BOOLEAN, blue1 INTEGER REFERENCES teams(number), blue1S BOOLEAN, blue2 INTEGER REFERENCES teams(number), blue2S BOOLEAN);", //non-game specific info
											"CREATE TABLE local.qualsData(match INTEGER REFERENCES quals(match), status INTEGER, randomization INTEGER, PRIMARY KEY (match)); ", //status and game-specific info necessary
											"CREATE TABLE local.qualsResults(match INTEGER REFERENCES quals(match), redScore INTEGER, blueScore INTEGER, redPenalty INTEGER, bluePenalty INTEGER, PRIMARY KEY (match));", //penalties needed to sub out for RP ~non-game specific info
											"CREATE TABLE local.qualsScores(match INTEGER REFERENCES quals(match), alliance TINYINT, autoGlyphs INTEGER, cryptoboxKeys INTEGER, jewels INTEGER, parkedAuto INTEGER, glyps INTEGER, rows INTEGER, columns INTEGER, ciphers INTEGER, relic1Zone INTEGER, relic1Standing BOOLEAN, relic2Zone INTEGER, relic2Standing BOOLEAN, balanced INTEGER, major INTEGER, minor INTEGER, cyprotbox1 INTEGER, cryptobox2 INTEGER, PRIMARY KEY (match, alliance) );" //completely game specific (except penalties)
											
											//sig table			
											//TODO create trigger for adding item to row
											};
	static final String SET_EVENT_STATUS_SQL = "UPDATE events SET STATUS = ? WHERE code = ?;";
	static final String[] POPULATE_TEAMS_SQL = {
											"INSERT INTO inspectionStatus (team) SELECT number FROM teams;",
											"INSERT INTO formStatus SELECT number, form.formID, itemIndex, 0 FROM teams LEFT JOIN formRows form ON type = 2 LEFT JOIN formItems items ON items.formID = form.formID AND items.row = form.row;",
											"INSERT INTO formComments (team, formID) SELECT number, formID FROM teams LEFT JOIN (SELECT DISTINCT formID from formRows);"
											//sig table
	};
	static final String ADD_TEAM_SQL = "INSERT INTO teams VALUES (?);";
	static final String ADD_TEAM_LATE = "";
	static final String GET_EVENT_LIST_SQL = "SELECT * FROM events;";
	static final String GET_EVENT_SQL = "SELECT * FROM events WHERE code = ?;";
	static final String GET_FORM_ROWS = "SELECT * FROM formRows WHERE formID = ? ORDER BY row";
	static final String GET_FORM_ITEMS = "SELECT items.row, items.itemIndex, items.label, items.req :teamColumns FROM formItems items";
	static final String TEAM_JOINS = " LEFT JOIN formStatus :alias ON :alias.cbIndex = items.itemIndex AND :alias.team = ? AND items.formID = :alias.formID";
	static final String FORM_ITEMS_WHERE = " WHERE items.formID = ? ORDER BY items.row, items.itemIndex";
	static final String SET_FORM_STATUS_SQL = "UPDATE formStatus SET status = ? WHERE formID = ? AND team = ? AND cbIndex = ?";
	static final String ATTACH_GLOBAL = "ATTACH DATABASE ':pathglobal.db' AS global;";
	static final String GET_STATUS_SQL = "SELECT stat.team, ti.name, :columns FROM inspectionStatus stat LEFT JOIN global.teamInfo ti ON ti.number = stat.team;";
	static final String GET_SINGLE_STATUS = "SELECT * FROM inspectionStatus WHERE team = ?";
	static final String SET_STATUS_SQL = "UPDATE inspectionStatus SET :column = ? WHERE team = ?";
	
	static final String CREATE_SCHEDULE_SQL = "INSERT INTO quals VALUES (?,?,?,?,?,?,?,?,?);";
	static final String GET_SCHEDULE_SQL = "SELECT * FROM quals";
	
	protected static Connection getLocalDB(String code) throws SQLException{
		return DriverManager.getConnection("jdbc:sqlite:"+Server.DB_PATH+code+".db");
	} 
	public static List<EventData> getEvents(){
		try(Connection conn = DriverManager.getConnection(Server.GLOBAL_DB)){
			PreparedStatement ps = conn.prepareStatement(CREATE_EVENT_SQL);
			ResultSet rs = ps.executeQuery();
			List<EventData> result = new ArrayList<EventData>();
			while(rs.next()){
				EventData e = new EventData(rs.getString(0), rs.getString(1), rs.getInt(3), rs.getDate(2));
				result.add(e);
			}
			return result;
		}catch(Exception e){
			e.printStackTrace();
		}
		return null;
	}
	
	public static EventData getEvent(String code){
		try(Connection conn = DriverManager.getConnection(Server.GLOBAL_DB)){
			PreparedStatement ps = conn.prepareStatement(CREATE_EVENT_SQL);
			ps.setString(1, code);
			ResultSet rs = ps.executeQuery();
			if(!rs.next())return null;
			return new EventData(rs.getString(0), rs.getString(1), rs.getInt(3), rs.getDate(2));
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
		//TODO IF EVENT PAST SETUP, need to do ADD_TEAM_LATE_SQL
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
	
	/**
	 * This method populates the formStatus, formComments, inspectionStatus tables. Any table
	 * that has team related data. This method should be called at the beginning of the inspection stage, after the
	 * setup stage. Once this is called, the inspection forms can only be edited in specific ways:
	 * change checkbox to NA or OPT, add OPT or NA box, edit wording. If a team is added, need to
	 * populate data for all of these tables on their addition. 
	 * @param event
	 * @return
	 */
	public static boolean populateStatusTables(String event){
		try(Connection local = getLocalDB(event)){
			Statement sql = local.createStatement();
			for(String s : POPULATE_TEAMS_SQL){
				sql.addBatch(s);
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
				teamColumns += ", "+c+".status";//+teams[i];
				teamJoins += TEAM_JOINS.replaceAll(":alias", c+"");
			}
			//row, index, label, req, [#.status, #.status, ...]
			ps = local.prepareStatement(GET_FORM_ITEMS.replaceAll(":teamColumns", teamColumns) + teamJoins + FORM_ITEMS_WHERE );
			int i = 0;
			for(; i < teams.length; i++){
				ps.setInt(i + 1, teams[i]);
			}
			ps.setString(i + 1, formCode);
			rs = ps.executeQuery();
			while(rs.next()){
				int row = rs.getInt(1);
				FormRow fr = map.get(row);
				switch(fr.getType()){
				case FormRow.HEADER:
					if(teams.length == 0){
						fr.addHeaderItem(rs.getInt(2), rs.getString(3), -1);
					} else{
						for(int team : teams){
							fr.addHeaderItem(rs.getInt(2), rs.getString(3) + (teams.length > 1 ? "<br/>("+team+")" : ""), team);
						}
					}
					break;
				case FormRow.NON_HEADER:
					int itemId = rs.getInt(2);
					if(teams.length == 0){
						fr.addDataItem(itemId, rs.getInt(4), false, -1);
					} else{
						for(int ti = 0; ti < teams.length; ti++){
							fr.addDataItem(itemId, rs.getInt(4), rs.getBoolean(5 + ti), teams[ti]);
						}
					}
					break;
				}				
			}
			for(FormRow row : form){
				row.postProcess();
			}
			return form;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	
	public static boolean setFormStatus(String event, String form, int team, int itemIndex, boolean status){
		try(Connection local = getLocalDB(event)){
			PreparedStatement ps = local.prepareStatement(SET_FORM_STATUS_SQL);
			//System.out.println(status+","+form+","+team+","+itemIndex);
			ps.setBoolean(1,  status);
			ps.setString(2, form);
			ps.setInt(3,  team);
			ps.setInt(4,  itemIndex);
			int affected = ps.executeUpdate();
			//System.out.println(affected);
			return affected == 1;
		}catch(Exception e){
			e.printStackTrace();
		}
		return false;
	}
	
	
	public static boolean setTeamStatus(String event, String form, int team, int status){
		//TODO handle same status returning false in calling method by checking!
		try(Connection local = getLocalDB(event)){
			PreparedStatement ps = local.prepareStatement(SET_STATUS_SQL.replaceAll(":column", form));
			ps.setInt(1, status);
			ps.setInt(2, team);
			int affected = ps.executeUpdate();
			return affected == 1;
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return false;
	}
	
	public static List<Team> getStatus(String event, String ... columns){
		try(Connection local = getLocalDB(event)){
			Statement stmt = local.createStatement();
			stmt.execute(ATTACH_GLOBAL.replaceAll(":path", Server.DB_PATH));
			if(columns.length == 0){
				columns = new String[]{"hw", "sw", "fd", "sc", "ci"};
			}
			stmt.execute(GET_STATUS_SQL.replaceAll(":columns", String.join(",", columns)));
			ResultSet rs = stmt.getResultSet();
			List<Team> result = new ArrayList<Team>();
			while(rs.next()){			
				Team team = new Team(rs.getInt("team"), rs.getString("name"));
				for(String c : columns){
					team.setStatus(c, rs.getByte(c));
				}
				
//				
//				String s = '{' + "\"number\":"+rs.getInt("team")+",\"name\":\""+rs.getString("name")+"\", ";
//				s += String.join(",", columns.stream().map(o -> {
//					try {
//						return '"'+o+"\":"+rs.getByte(o);
//					} catch (SQLException e) {
//						e.printStackTrace();
//						return "";
//					}
//				}).toArray(String[]::new));
				result.add(team);
			}
			return result;
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	public static boolean createSchedule(String event, List<Match> matches){
		try(Connection local = getLocalDB(event)){
			for(Match m : matches){
				PreparedStatement ps = local.prepareStatement(CREATE_SCHEDULE_SQL);
				ps.setInt(1, m.getNumber());
				Alliance red = m.getRed();
				Alliance blue = m.getBlue();
				ps.setInt(2, red.getTeam1());
				ps.setBoolean(3,  red.is1Surrogate());
				ps.setInt(4, red.getTeam2());
				ps.setBoolean(5,  red.is2Surrogate());
				ps.setInt(6, blue.getTeam1());
				ps.setBoolean(7,  blue.is1Surrogate());
				ps.setInt(8, blue.getTeam2());
				ps.setBoolean(9,  blue.is2Surrogate());
				ps.executeUpdate();
			}			
		} catch(Exception e){
			return false;
		}
		return true;
	}
	
	public static List<Match> getSchedule(String event){
		try(Connection local = getLocalDB(event)){
			PreparedStatement ps = local.prepareStatement(GET_SCHEDULE_SQL);
			ResultSet rs = ps.executeQuery();
			List<Match> matches = new ArrayList<>();
			while(rs.next()){
				Alliance red = new Alliance(rs.getInt(2), rs.getBoolean(3), rs.getInt(4), rs.getBoolean(5));
				Alliance blue = new Alliance(rs.getInt(6), rs.getBoolean(7), rs.getInt(8), rs.getBoolean(9));
				matches.add(new Match(rs.getInt(1), red, blue));
			}
			return matches;
		}catch(Exception e){
			return null;
		}
	}
	
}

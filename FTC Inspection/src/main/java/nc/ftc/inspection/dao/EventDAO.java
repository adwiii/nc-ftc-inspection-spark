/*
 * Copyright (c) 2016-2018 Thomas Barnette and Trey Woodlief
 * All Rights Reserved.
 */

package nc.ftc.inspection.dao;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nc.ftc.inspection.RemoteUpdater;
import nc.ftc.inspection.Server;
import nc.ftc.inspection.Update;
import nc.ftc.inspection.event.Event;
import nc.ftc.inspection.event.StatsCalculator;
import nc.ftc.inspection.event.StatsCalculator.StatsCalculatorJob;
import nc.ftc.inspection.model.Alliance;
import nc.ftc.inspection.model.EventData;
import nc.ftc.inspection.model.FormRow;
import nc.ftc.inspection.model.Match;
import nc.ftc.inspection.model.MatchResult;
import nc.ftc.inspection.model.Selection;
import nc.ftc.inspection.model.Team;

public class EventDAO {
	public static final SimpleDateFormat EVENT_DATE_FORMAT = new SimpleDateFormat("MM/dd/yyyy");
	
	public static final Map<Integer, SQL> queryMap = new HashMap<>(); 
	private static final RemoteUpdater updater = RemoteUpdater.getInstance();
	
	
	//MAX SQL = 35
	//TODO THis needs to be a command - NO, this should not be a thing! Must create an event locally.
	static final SQL CREATE_EVENT_SQL = new SQL(1,"INSERT INTO events(code, name, [date], status) VALUES(?,?,?,0)");
	static final String[] CREATE_EVENT_DB_SQL ={ 
											"ATTACH DATABASE ':code.db' AS local;" , 
											"CREATE TABLE local.teams(number INTEGER PRIMARY KEY);",
											"CREATE TABLE local.formRows (formID VARCHAR(2), type INTEGER, row INTEGER, columnCount INTEGER, description VARCHAR, rule VARCHAR(128), page INTEGER, PRIMARY KEY (formID, row));",
											"CREATE TABLE local.formItems (formID VARCHAR(2), row INTEGER, itemIndex INTEGER, label VARCHAR, req TINYINT, PRIMARY KEY(itemIndex, formID), FOREIGN KEY(formID, row) references formRows(formID, row));",
											"CREATE TABLE local.formStatus(team INTEGER REFERENCES teams(number), formID VARCHAR(2), cbIndex INTEGER, status BOOLEAN, PRIMARY KEY (team, formID, cbIndex), FOREIGN KEY (formID, cbIndex) REFERENCES formRows(formID, itemIndex));" ,
											"CREATE TABLE local.formComments(team INTEGER REFERENCES teams(number), formID VARCHAR(2), comment VARCHAR, PRIMARY KEY (team, formID));",
											"CREATE TABLE local.formSigs(team INTEGER REFERENCES teams(number), formID VARCHAR(2), sigIndex INTEGER, sig VARCHAR, PRIMARY KEY (team, formID, sigIndex));",
											"CREATE TABLE local.preferences (id VARCHAR PRIMARY KEY, value VARCHAR);",
											"CREATE TABLE local.inspectionStatus (team INTEGER PRIMARY KEY REFERENCES teams(number), ci TINYINT, hw TINYINT, sw TINYINT, fd TINYINT, sc TINYINT);",
											"INSERT INTO local.formRows SELECT * FROM formRows;",
											"INSERT INTO local.formItems SELECT * FROM formItems;",											
											
											
											"CREATE TABLE local.quals(match INTEGER PRIMARY KEY, red1 INTEGER REFERENCES teams(number), red1S BOOLEAN, red2 INTEGER REFERENCES teams(number), red2S BOOLEAN, blue1 INTEGER REFERENCES teams(number), blue1S BOOLEAN, blue2 INTEGER REFERENCES teams(number), blue2S BOOLEAN);", //non-game specific info
											"CREATE TABLE local.qualsData(match INTEGER REFERENCES quals(match), status INTEGER, randomization INTEGER, PRIMARY KEY (match)); ", //status and game-specific info necessary
											"CREATE TABLE local.qualsResults(match INTEGER REFERENCES quals(match), redScore INTEGER, blueScore INTEGER, redPenalty INTEGER, bluePenalty INTEGER, PRIMARY KEY (match));", //penalties needed to sub out for RP ~non-game specific info
											"CREATE TABLE local.qualsScores(match INTEGER REFERENCES quals(match), alliance TINYINT, autoGlyphs INTEGER, cryptoboxKeys INTEGER, jewels INTEGER, parkedAuto INTEGER, glyphs INTEGER, rows INTEGER, columns INTEGER, ciphers INTEGER, relic1Zone INTEGER, relic1Standing BOOLEAN, relic2Zone INTEGER, relic2Standing BOOLEAN, balanced INTEGER, major INTEGER, minor INTEGER, cryptobox1 INTEGER, cryptobox2 INTEGER, jewelSet1 TINYINT, jewelSet2 TINYINT, adjust INTEGER, card1 INTEGER, card2 INTEGER, dq1 BOOLEAN, dq2 BOOLEAN, PRIMARY KEY (match, alliance) );", //completely game specific (except penalties)
												
											"CREATE TABLE local.alliances(rank PRIMARY KEY, team1 INTEGER, team2 INTEGER, team3 INTEGER);",
											"CREATE TABLE local.elims(match INTEGER PRIMARY KEY, red INTEGER REFERENCES alliances(rank), blue INTEGER REFERENCES alliances(rank));", //non-game specific info
											"CREATE TABLE local.elimsData(match INTEGER REFERENCES elims(match), status INTEGER, randomization INTEGER, name VARCHAR, PRIMARY KEY (match)); ", //status and game-specific info necessary
											"CREATE TABLE local.elimsResults(match INTEGER REFERENCES elims(match), redScore INTEGER, blueScore INTEGER, redPenalty INTEGER, bluePenalty INTEGER, PRIMARY KEY (match));", //penalties needed to sub out for RP ~non-game specific info
											"CREATE TABLE local.elimsScores(match INTEGER REFERENCES elims(match), alliance TINYINT, autoGlyphs INTEGER, cryptoboxKeys INTEGER, jewels INTEGER, parkedAuto INTEGER, glyphs INTEGER, rows INTEGER, columns INTEGER, ciphers INTEGER, relic1Zone INTEGER, relic1Standing BOOLEAN, relic2Zone INTEGER, relic2Standing BOOLEAN, balanced INTEGER, major INTEGER, minor INTEGER, cryptobox1 INTEGER, cryptobox2 INTEGER, jewelSet1 TINYINT, jewelSet2 TINYINT, adjust INTEGER, card1 INTEGER, card2 INTEGER, dq1 BOOLEAN, dq2 BOOLEAN, PRIMARY KEY (match, alliance) );", //completely game specific (except penalties)
											"CREATE TABLE local.selections(id INTEGER PRIMARY KEY AUTOINCREMENT, op INTEGER, alliance INTEGER, team INTEGER REFERENCES teams(number));"
												
											//TODO create trigger for adding item to row
											};
	//TODO This needs to eb a command. (it accesses global)
	public static final SQL SET_EVENT_STATUS_SQL = new SQL(2,"UPDATE events SET STATUS = ? WHERE code = ?;");
	static final String[] POPULATE_TEAMS_SQL = {
											"INSERT INTO inspectionStatus (team) SELECT number FROM teams;",
											"INSERT INTO formStatus SELECT number, form.formID, itemIndex, 0 FROM teams LEFT JOIN formRows form ON type = 2 LEFT JOIN formItems items ON items.formID = form.formID AND items.row = form.row;",
											"INSERT INTO formComments (team, formID) SELECT number, formID FROM teams LEFT JOIN (SELECT DISTINCT formID from formRows);",
											"INSERT INTO formSigs (team, formID, sigIndex) SELECT number, formID, i from teams LEFT JOIN (SELECT DISTINCT formID from formRows) LEFT JOIN (SELECT 0 AS i UNION SELECT 1 AS i);"
	};
	public static final SQL ADD_TEAM_SQL = new SQL(3, "INSERT INTO teams VALUES (?);");
	public static final SQL REMOVE_TEAM_SQL = new SQL(25, "DELETE FROM teams WHERE number=?;");
	static final String ADD_TEAM_LATE = "";
	static final String GET_EVENT_LIST_SQL = "SELECT * FROM events;";
	static final String GET_EVENT_SQL = "SELECT * FROM events WHERE code = ?;";
	static final String GET_FORM_ROWS = "SELECT * FROM formRows WHERE formID = ? ORDER BY row";
	static final String GET_FORM_ITEMS = "SELECT items.row, items.itemIndex, items.label, items.req :teamColumns FROM formItems items";
	static final String TEAM_JOINS = " LEFT JOIN formStatus :alias ON :alias.cbIndex = items.itemIndex AND :alias.team = ? AND items.formID = :alias.formID";
	static final String FORM_ITEMS_WHERE = " WHERE items.formID = ? ORDER BY items.row, items.itemIndex";
	//This query should probably be optimized at some point, it has 2 nested selects
	static final String GET_FAILED_ROWS_SQL = "SELECT fr.row, fr.description, fr.rule, fr.page FROM formRows fr INNER JOIN (SELECT row, fi.formID from formItems fi INNER JOIN (select cbIndex, formID from formStatus where formID=? AND team=? AND status=0) j ON fi.itemIndex = j.cbIndex AND fi.formID = j.formID WHERE fi.req=1) j2 ON fr.row = j2.row AND fr.formID = j2.formID ORDER BY fr.row;";
	
	static final SQL SET_FORM_STATUS_SQL = new SQL(4,"UPDATE formStatus SET status = ? WHERE formID = ? AND team = ? AND cbIndex = ?");
	public static final String ATTACH_GLOBAL = "ATTACH DATABASE ':pathglobal.db' AS global;";
	static final String GET_STATUS_SQL = "SELECT stat.team, ti.name, :columns FROM inspectionStatus stat LEFT JOIN global.teamInfo ti ON ti.number = stat.team;";
	static final String GET_TEAMSTATUS_SQL = "SELECT * FROM inspectionStatus WHERE team=?;";
	static final String GET_TEAMS_SQL = "SELECT a.number, ti.name, ti.location FROM teams a LEFT JOIN global.teamInfo ti ON ti.number = a.number ORDER BY a.number;";
	static final String GET_SINGLE_STATUS = "SELECT * FROM inspectionStatus WHERE team = ?";
	static final SQL SET_STATUS_SQL = new SQL(14,"UPDATE inspectionStatus SET :column = ? WHERE team = ?");
	static final String GET_COMMENT_SQL = "SELECT team,comment FROM formComments WHERE team IN (:in) AND formID = ?";
	static final String GET_SIG_SQL = "SELECT team,sigIndex,sig FROM formSigs WHERE team IN (:in) AND formID = ?";
	static final SQL SET_COMMENT_SQL = new SQL(5,"UPDATE formComments SET comment = ? WHERE team = ? AND formID = ? ");
	static final SQL SET_SIG_SQL = new SQL(6,"UPDATE formSigs SET sig = ? WHERE team = ? AND formID = ? AND sigIndex = ? ");
	
	//TODO change to >= 2 when Inspection is stored in active events.
	static final String GET_ACTIVE_EVENTS_SQL = "SELECT * FROM events WHERE status >= 3 AND status <= 5;";
	static final SQL CREATE_SCHEDULE_SQL = new SQL(7,"INSERT INTO quals VALUES (?,?,?,?,?,?,?,?,?);");
	static final SQL CREATE_SCHEDULE_DATA_SQL = new SQL(8,"INSERT INTO qualsData VALUES (?,?,?);");
	static final SQL CREATE_SCHEDULE_RESULTS_SQL = new SQL(9,"INSERT INTO qualsResults (match) VALUES (?);");
	static final SQL CREATE_SCHEDULE_SCORES_SQL = new SQL(10,"INSERT INTO qualsScores (match, alliance) VALUES (?,?);");
	
	static final SQL DELETE_SCHEDULE_SQL = new SQL(32,"DELETE FROM quals");
	static final SQL DELETE_SCHEDULE_DATA_SQL = new SQL(33, "DELETE FROM qualsData;");
	static final SQL DELETE_SCHEDULE_RESULTS_SQL = new SQL(34, "DELETE FROM qualsResults;");
	static final SQL DELETE_SCHEDULE_SCORES_SQL = new SQL(35, "DELETE FROM qualsScores;");	
	
	static final SQL DELETE_ELIMS_SQL = new SQL(36,"DELETE FROM elims");
	static final SQL DELETE_ELIMS_DATA_SQL = new SQL(37, "DELETE FROM elimsData;");
	static final SQL DELETE_ELIMS_RESULTS_SQL = new SQL(38, "DELETE FROM elimsResults;");
	static final SQL DELETE_ELIMS_SCORES_SQL = new SQL(39, "DELETE FROM elimsScores;");
	static final SQL DELETE_ALLIANCES_SQL = new SQL(40, "DELETE FROM alliances;");
	
	static final String GET_SCHEDULE_SQL = "SELECT * FROM quals";
	static final String GET_NEXT_MATCH_SQL = "SELECT q.* FROM qualsData qd LEFT JOIN quals q ON qd.match == q.match WHERE qd.status==0 ORDER BY match LIMIT 1;";
	static final String GET_MATCH_SQL = "SELECT q.* FROM quals q WHERE q.match=? ORDER BY match LIMIT 1;";
	
	static final SQL COMMIT_MATCH_DATA = new SQL(11,"UPDATE qualsData SET status = ?, randomization = ? WHERE match = ?;");
	static final SQL COMMIT_MATCH_RESULTS = new SQL(12,"UPDATE qualsResults SET redScore = ?, blueScore = ?, redPenalty = ?, bluePenalty = ? WHERE match = ?;");
	static final SQL COMMIT_MATCH_SCORES = new SQL(13,"UPDATE qualsScores SET autoGlyphs=?, cryptoboxKeys=?, jewels=?, parkedAuto=?, glyphs=?, rows=?, columns=?, ciphers=?, relic1Zone=?, relic1Standing=?, relic2Zone=?, relic2Standing=?, balanced=?, major=?, minor=?, cryptobox1=?, cryptobox2=?, jewelSet1=?, jewelSet2=?, adjust=?, card1=?, card2=?, dq1=?, dq2=? WHERE match=? AND alliance=?");
	
	static final String GET_SCHEDULE_STATUS_QUALS = "SELECT q.match, red1, red2, blue1, blue2, status, redScore, blueScore FROM quals q LEFT JOIN qualsData qd ON q.match = qd.match LEFT JOIN qualsResults qr ON q.match = qr.match";
	static final String GET_RESULTS_QUALS = "SELECT q.match, red1, red1S, red2, red2S, blue1, blue1S, blue2, blue2S, redScore, blueScore, status, redPenalty, bluePenalty FROM quals q LEFT JOIN qualsData qd ON q.match = qd.match LEFT JOIN qualsResults qr ON q.match = qr.match ORDER BY q.match";
	static final String GET_CARDS_DQS_SQL = "SELECT qs.match, alliance, card1, card2, dq1, dq2 from qualsScores qs INNER JOIN qualsData qd ON qs.match=qd.match WHERE qd.status = 1; ";
	static final String GET_MATCH_RESULTS_FULL_SQL = "SELECT * FROM qualsScores s WHERE match=?;";
	static final String GET_MATCH_RESULTS_FOR_STATS_SQL = "SELECT s.*,randomization FROM qualsScores s LEFT JOIN qualsData d ON s.match=d.match ORDER BY s.match;";
	
	
	static final SQL SET_ALLIANCE_SQL = new SQL(15, "INSERT OR REPLACE INTO alliances VALUES (?,?,?,?);");
	
	//used when need to create more matches (alliances uploaded, need 3+ matches, SFs complete.)
	static final SQL ADD_ELIMS_MATCH_SQL = new SQL(16, "INSERT INTO elims VALUES (?,?,?);");
	static final SQL ADD_ELIMS_MATCH_RESULT_SQL = new SQL(17, "INSERT INTO elimsResults (match) VALUES (?);");
	static final SQL ADD_ELIMS_MATCH_DATA_SQL = new SQL(18, "INSERT INTO elimsData (match, status, name) VALUES (?,?, ?);");
	static final SQL ADD_ELIMS_MATCH_SCORES_SQL = new SQL(19, "INSERT INTO elimsScores (match, alliance) VALUES (?,?);");
	
	//use string replaceall instead
//	static final SQL COMMIT_ELIMS_DATA = new SQL(20, "UPDATE elimsData SET status=1, randomization = ? WHERE match=?;");
	static final SQL UNCANCEL_ELIMS_MATCH_SQL = new SQL(21, "UPDATE elimsData SET status=0 WHERE match=?;");
	static final SQL CANCEL_ELIMS_MATCH_SQL = new SQL(22, "UPDATE elimsData SET status=2 WHERE match=?;");
//	static final SQL COMMIT_ELIMS_RESULTS_SQL = new SQL(22, "UPDATE elimsResults SET redScore = ?, blueScore = ?, redPenalty = ?, bluePenalty = ? WHERE match = ?;");
//	static final SQL COMMIT_ELIMS_SCORES_SQL = new SQL(23,"UPDATE elimsScores SET autoGlyphs=?, cryptoboxKeys=?, jewels=?, parkedAuto=?, glyphs=?, rows=?, columns=?, ciphers=?, relic1Zone=?, relic1Standing=?, relic2Zone=?, relic2Standing=?, balanced=?, major=?, minor=?, cryptobox1=?, cryptobox2=?, jewelSet1=?, jewelSet2=?, adjust=?, card1=?, card2=?, dq1=?, dq2=? WHERE match=? AND alliance=?");
	
	static final String GET_SCHEDULE_ELIMS_SQL = "SELECT  q.match, red.rank, red.team1, red.team2, red.team3, blue.rank, blue.team1, blue.team2, blue.team3, name, status=2 FROM elims q LEFT JOIN alliances red ON red.rank=q.red LEFT JOIN alliances blue ON blue.rank=q.blue LEFT JOIN elimsData ed ON ed.match=q.match";
	static final String GET_NEXT_ELIMS_MATCH_SQL = "SELECT q.match, red.rank, red.team1, red.team2, red.team3, blue.rank, blue.team1, blue.team2, blue.team3, name, status=2 FROM elims q LEFT JOIN alliances red ON red.rank=q.red LEFT JOIN alliances blue ON blue.rank=q.blue LEFT JOIN elimsData ed ON ed.match=q.match WHERE ed.status==0 ORDER BY q.match LIMIT 1;";
	static final String GET_ELIMS_MATCH_SQL = "SELECT q.match, red.rank, red.team1, red.team2, red.team3, blue.rank, blue.team1, blue.team2, blue.team3, name, status=2 FROM elims q LEFT JOIN alliances red ON red.rank=q.red LEFT JOIN alliances blue ON blue.rank=q.blue LEFT JOIN elimsData ed ON ed.match=q.match WHERE q.match=? ORDER BY q.match LIMIT 1;";
	static final String GET_ELIMS_MATCH_BASIC = "SELECT red, blue FROM elims WHERE match = ?;";
	
	//This ones def gonna break! (red and blue 3 at end to reuse quals code)
	static final String GET_SCHEDULE_STATUS_ELIMS_SQL = "SELECT q.match, r.team1, r.team2, b.team1, b.team2, status, redScore, blueScore, r.team3, b.team3, name  FROM elims q LEFT JOIN elimsData qd ON q.match = qd.match LEFT JOIN elimsResults qr ON q.match = qr.match LEFT JOIN alliances r ON r.rank=q.red LEFT JOIN alliances b ON b.rank=q.blue;"; 
	static final String GET_RESULTS_ELIMS = "SELECT q.match, q.red, red.team1, red.team2, red.team3, q.blue, blue.team1, blue.team2, blue.team3, redScore, blueScore, status, redPenalty, bluePenalty, qd.name FROM elims q LEFT JOIN elimsData qd ON q.match = qd.match LEFT JOIN elimsResults qr ON q.match = qr.match LEFT JOIN alliances red ON red.rank=q.red LEFT JOIN alliances blue ON blue.rank=q.blue ORDER BY q.match";	
	static final String GET_CARDS_ELIMS_SQL = "SELECT e.match, e.red, e.blue, red.card1, blue.card1 FROM elims e INNER JOIN elimsScores red ON e.match=red.match AND red.alliance=0 INNER JOIN elimsScores blue ON e.match=blue.match AND blue.alliance=1 WHERE red.card1 + blue.card1 > 0;";
	static final String GET_ELIMS_RESULTS_FULL_SQL = "SELECT * FROM elimsScores s WHERE match=?;";
	static final String GET_ELIMS_MATCH_NUMBER_SQL = "SELECT match FROM elimsData WHERE name = ?;";
	//include all statuses, so caller can know if more matches exist already
	static final String GET_ELIMS_SERIES_RESULTS_SQL = "SELECT er.*, ed.status FROM elimsResults er LEFT JOIN elimsData ed ON ed.match = er.match WHERE ed.name LIKE ?";
	//This SQL requires post-processing
	static final String GET_CARDS_FOR_TEAM_SQL =  "SELECT q.match, red1, red2, card1, card2 FROM quals q INNER JOIN qualsScores qs ON qs.match=q.match AND qs.alliance=0 WHERE red1=? OR red2=? UNION "
												+ "SELECT q.match, blue1, blue2, card1, card2 FROM quals q INNER JOIN qualsScores qs ON qs.match=q.match AND qs.alliance=1 WHERE blue1=? OR blue2=? ORDER BY q.match";
	
	static final String GET_RANDOM_SQL = "SELECT randomization FROM qualsData WHERE match = ?;";
	
	static final String GET_SELECTIONS = "SELECT * FROM selections ORDER BY id;";
	static final SQL SELECTION_SQL = new SQL(26, "INSERT INTO selections(op, alliance, team) VALUES (?,?,?);");
	static final SQL UNDO_SELECTION_SQL = new SQL(27, "DELETE FROM selections WHERE id IN (SELECT MAX(id) FROM selections);");
	static final SQL CLEAR_SELECTION_SQL = new SQL(28, "DELETE FROM selections;");
	
	static final SQL SET_PROPERTY = new SQL(30, "INSERT OR REPLACE INTO preferences VALUES (?,?);");
	static final String GET_PROPERTY_SQL = "SELECT value FROM preferences WHERE id = ?;";
	
	static final String REMOVE_EVENT_SQL = "DELETE FROM events WHERE code=?;";
	

	static final Logger log = LoggerFactory.getLogger(EventDAO.class);
	
	static {
		Field[] fields = EventDAO.class.getDeclaredFields();
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
					System.err.println("DUPLICATE SQL MAPPING IN EVENTDAO: "+s.id);
				}
				queryMap.put(s.id, s);
			}
		}
	}
	
	
	protected static Connection getLocalDB(String code) throws SQLException{
		return DriverManager.getConnection("jdbc:sqlite:"+Server.DB_PATH+code+".db");
	} 
	private static List<EventData> createEventList(ResultSet rs) throws SQLException{
		List<EventData> result = new ArrayList<EventData>();
		while(rs.next()){
			EventData e = new EventData(rs.getString(1), rs.getString(2), rs.getInt(4), rs.getDate(3));
			result.add(e);
		}
		return result;
	}
	public static List<EventData> getEvents(){
		try(Connection conn = DriverManager.getConnection(Server.GLOBAL_DB)){
			PreparedStatement ps = conn.prepareStatement(GET_EVENT_LIST_SQL);
			ResultSet rs = ps.executeQuery();
			return createEventList(rs);
		}catch(Exception e){
			log.error("Error getting event list.", e);
		}
		return null;
	}
	public static boolean removeEvent(String code) {
		try(Connection conn = DriverManager.getConnection(Server.GLOBAL_DB)){
			PreparedStatement ps = conn.prepareStatement(REMOVE_EVENT_SQL);
			ps.setString(1, code);
			ps.executeUpdate();
		}catch(Exception e) {
			log.error("Error removing event "+ code, e);
			return false;
		}
		return true;
	}
	public static boolean deleteEvent(String code) {
		removeEvent(code);
		try {
			Files.deleteIfExists(new File(Server.DB_PATH+code+".db").toPath());
		} catch (IOException e) {
			log.error("Error deleting event "+ code, e);
			return false;
		}
		return true;
		
	}
	
	public static EventData getEvent(String code){
		try(Connection conn = DriverManager.getConnection(Server.GLOBAL_DB)){
			PreparedStatement ps = conn.prepareStatement(GET_EVENT_SQL);
			ps.setString(1, code);
			ResultSet rs = ps.executeQuery();
			if(!rs.next()) {
				log.info("No event for code {}", code);
				return null;
			}
			return new EventData(rs.getString(1), rs.getString(2), rs.getInt(4), rs.getDate(3));
		}catch(Exception e){
			log.error("Error getting EventData for"+ code, e);
		}
		return null;
	}
	
	public static boolean createEvent(String code, String name, java.sql.Date date){
		try(Connection conn = DriverManager.getConnection(Server.GLOBAL_DB)){
			PreparedStatement ps = conn.prepareStatement(CREATE_EVENT_SQL.sql);
			ps.setString(1, code);
			ps.setString(2, name);
			ps.setDate(3, date);
			int affected = ps.executeUpdate();
			return affected == 1;
		}catch(Exception e){
			log.error("Error creating event " + code, e);
			return false;
		}
	}
	
	public static boolean setEventStatus(String code, int status){
		try(Connection conn = DriverManager.getConnection(Server.GLOBAL_DB)){
			PreparedStatement ps = conn.prepareStatement(SET_EVENT_STATUS_SQL.sql);
			ps.setInt(1, status);
			ps.setString(2, code);
			int affected = ps.executeUpdate();
			if(status == EventData.QUALS) {
				Server.activeEvents.put(code, new Event(getEvent(code)));
			}
			Event e = Server.activeEvents.get(code);
			if (e != null) {
				e.getData().setStatus(status);
			}
			updater.enqueue(new Update(code, Update.COMMAND, null, Update.SET_EVENT_STATUS_CMD, status));
			return affected == 1;
		}catch(Exception e){
			log.error("Error setting event status "+ status+ "for "+code, e);
			return false;
		}
	}
	
	public static boolean addTeamToEvent(int team, String eventCode){
		//TODO IF EVENT PAST SETUP, need to do ADD_TEAM_LATE_SQL
		try(Connection conn = getLocalDB(eventCode)){
			PreparedStatement ps = conn.prepareStatement(ADD_TEAM_SQL.sql);
			ps.setInt(1, team);
			int affected = ps.executeUpdate();
			updater.enqueue(new Update(eventCode, 1, null,ADD_TEAM_SQL.id, team));
			return affected == 1;
		}catch(Exception e){
			if(e.getMessage().contains("PRIMARY KEY must be unique")) {
				log.info("Team {} already in event {}.", team, eventCode);
				return false; //attempted to add a team already in event.
			}
			log.error("Error adding team "+team+" to event " + eventCode,  e);
			return false;
		}
	}
	
	public static boolean addTeamLate(int team, String code) {
		// TODO Auto-generated method stub
		//TODO add inspection data insertion SQL
		log.warn("Added team {} to event {} late! Inspection data not supported!", team, code);
		return addTeamToEvent(team, code);
	}
	
	public static boolean removeTeamFromEvent(int team, String eventCode){
		try(Connection conn = getLocalDB(eventCode)){
			PreparedStatement ps = conn.prepareStatement(REMOVE_TEAM_SQL.sql);
			ps.setInt(1, team);
			int affected = ps.executeUpdate();
			updater.enqueue(new Update(eventCode, 1, null,REMOVE_TEAM_SQL.id, team));
			return affected == 1;
		}catch(Exception e){
			log.error("Error removing team "+team+" from event "+ eventCode +":", e);
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
			updater.enqueue(new Update(code, Update.COMMAND, null, Update.CREATE_EVENT_DB_CMD));
			return true;
		} catch(Exception e){
			log.error("Error creating event database for "+code, e);
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
			updater.enqueue(new Update(event, Update.COMMAND, null, Update.POPULATE_STATUS_TABLES_CMD));
			return true;
		} catch(Exception e){
			log.error("error populating status tables for "+event, e);
		}
		return false;
	}
	
	public static List<FormRow> getFailedItems(String eventCode, String formCode, int team){
		
		try(Connection local = getLocalDB(eventCode)){
			PreparedStatement ps = local.prepareStatement(GET_FAILED_ROWS_SQL);
			ps.setString(1, formCode);
			ps.setInt(2,  team);
			ResultSet rs = ps.executeQuery();
			//Rows returned ordered by row, so list will be in order
			List<FormRow> form = new ArrayList<FormRow>();			
			while(rs.next()){
				FormRow f = new FormRow(formCode, FormRow.NON_HEADER, 0, rs.getInt(1), rs.getString(2), rs.getString(3), rs.getInt(4));
				form.add(f);
			}		
			return form;
		} catch (Exception e) {
			log.error("Error getting failed items in "+formCode+" for team "+team +" in "+eventCode, e );
		}
		return null;
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
				FormRow f = new FormRow(rs.getString(1), rs.getInt(2), rs.getInt(3), rs.getInt(4) * (teams.length > 0 ? teams.length : 1), rs.getString(5), rs.getString(6), rs.getInt(7));
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
			log.error("Error getting form "+formCode+" for "+Arrays.toString(teams)+" at "+eventCode, e);
		}
		return null;
	}
	
	
	
	public static boolean setFormStatus(String event, String form, int team, int itemIndex, boolean status){
		try(Connection local = getLocalDB(event)){
			PreparedStatement ps = local.prepareStatement(SET_FORM_STATUS_SQL.sql);
			//System.out.println(status+","+form+","+team+","+itemIndex);
			ps.setBoolean(1,  status);
			ps.setString(2, form);
			ps.setInt(3,  team);
			ps.setInt(4,  itemIndex);
			int affected = ps.executeUpdate();
			updater.enqueue(new Update(event, 1, null,SET_FORM_STATUS_SQL.id, status, form, team, itemIndex));
			return affected == 1;
		}catch(Exception e){
			log.error("Error setting form status: "+form +" index "+itemIndex+" to "+status+" for team "+team+" at "+event, e);
		}
		return false;
	}
	
	
	public static boolean setTeamStatus(String event, String form, int team, int status){
		//TODO handle same status returning false in calling method by checking!
		try(Connection local = getLocalDB(event)){
			PreparedStatement ps = local.prepareStatement(SET_STATUS_SQL.sql.replaceAll(":column", form));
			ps.setInt(1, status);
			ps.setInt(2, team);
			int affected = ps.executeUpdate();
			Map<String, String> map = new HashMap<>();
			map.put(":column", form);
			updater.enqueue(new Update(event, 1, map, SET_STATUS_SQL.id, status, team));
			return affected == 1;
		} catch (SQLException e) {
			log.error("Error setting team "+team+" "+form+" status to "+status+" at "+event, e);
		}
		return false;
	}
	
	public static List<Team> getTeams(String event){
		try(Connection local = getLocalDB(event)){
			Statement stmt = local.createStatement();
			stmt.execute(ATTACH_GLOBAL.replaceAll(":path", Server.DB_PATH));
			stmt.execute(GET_TEAMS_SQL);
			ResultSet rs = stmt.getResultSet();
			List<Team> result = new ArrayList<Team>();
			while(rs.next()){			
				Team team = new Team(rs.getInt("number"), rs.getString("name"), rs.getString("location"));
				result.add(team);
			}
			return result;
		} catch (SQLException e) {
			if(e.getMessage().contains("no such table")) {
				System.err.println("OLD DB ("+event+")"+e.getMessage());
				log.error("Old or missing Database ("+event+"). No teams table!", e);
				return null;
			}
			log.error("Error getting teams at "+event, e);
		}
		return null;
	}
	
	public static Team getTeamStatus(String event, int teamNo) {
		try(Connection local = getLocalDB(event)){
			PreparedStatement ps = local.prepareStatement(GET_TEAMSTATUS_SQL);
			ps.setInt(1, teamNo);
			ResultSet rs = ps.executeQuery();
			if(!rs.next()) {
				log.info("No status info for team {} at {}.", teamNo, event);
				return null;
			}
			Team team = new Team(rs.getInt("team"), "NO NAME");
			for(String c : new String[]{"hw", "sw", "fd", "sc", "ci"}){
				team.setStatus(c, rs.getByte(c));
			}
			return team;
			
		} catch(Exception e) {
			log.error("Error getting status for team "+teamNo+" at "+event, e);
		}
		return null;
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
			System.err.println("SQL Error in getStatus()");
		}
		return null;
	}
	
	public static boolean createSchedule(String event, List<Match> matches){
		//TODO if schedule already exists, PK violation -> way to overwrite schedule
		try(Connection local = getLocalDB(event)){
			for(Match m : matches){
				System.out.println(m.getNumber());
				PreparedStatement ps = local.prepareStatement(CREATE_SCHEDULE_SQL.sql);
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
				updater.enqueue(new Update(event, 1, null, CREATE_SCHEDULE_SQL.id, m.getNumber(), red.getTeam1(), red.is1Surrogate(), red.getTeam2(),
						red.is2Surrogate(), blue.getTeam1(), blue.is1Surrogate(), blue.getTeam2(), blue.is2Surrogate()));
				ps = local.prepareStatement(CREATE_SCHEDULE_DATA_SQL.sql);
				ps.setInt(1,  m.getNumber());
				ps.setInt(2, 0);
				ps.setInt(3, 0);
				ps.executeUpdate();
				updater.enqueue(new Update(event, 1, null, CREATE_SCHEDULE_DATA_SQL.id, m.getNumber(), 0, 0));
				ps = local.prepareStatement(CREATE_SCHEDULE_RESULTS_SQL.sql);
				ps.setInt(1, m.getNumber());
				ps.executeUpdate();
				updater.enqueue(new Update(event, 1, null, CREATE_SCHEDULE_RESULTS_SQL.id, m.getNumber()));
				ps = local.prepareStatement(CREATE_SCHEDULE_SCORES_SQL.sql);
				ps.setInt(1, m.getNumber());
				ps.setInt(2, 0);
				ps.executeUpdate();
				updater.enqueue(new Update(event, 1, null, CREATE_SCHEDULE_SCORES_SQL.id, m.getNumber(), 0));
				ps = local.prepareStatement(CREATE_SCHEDULE_SCORES_SQL.sql);
				ps.setInt(1,  m.getNumber());
				ps.setInt(2,  1);
				ps.executeUpdate();
				updater.enqueue(new Update(event, 1, null, CREATE_SCHEDULE_SCORES_SQL.id, m.getNumber(), 1));
			}			
			
			//TODO calc rankings!
			
		} catch(Exception e){
			e.printStackTrace();
			return false;
		}
		Server.activeEvents.get(event).scheduleCache.invalidate();
		return true;
	}
	private static Match parseMatch(ResultSet rs) throws SQLException{
		Alliance red = new Alliance(rs.getInt(2), rs.getBoolean(3), rs.getInt(4), rs.getBoolean(5));
		Alliance blue = new Alliance(rs.getInt(6), rs.getBoolean(7), rs.getInt(8), rs.getBoolean(9));
		return new Match(rs.getInt(1), red, blue);
	}
	private static Match parseElimsMatch(ResultSet rs) throws SQLException{
		Alliance red = new Alliance(rs.getInt(2), rs.getInt(3), rs.getInt(4), rs.getInt(5));
		Alliance blue = new Alliance(rs.getInt(6), rs.getInt(7), rs.getInt(8), rs.getInt(9));
		Match match = new Match(rs.getInt(1), red, blue, rs.getString(10));
		match.setCancelled(rs.getBoolean(11));
		return match;
	}
	public static List<Match> getSchedule(String event){
		try(Connection local = getLocalDB(event)){
			PreparedStatement ps = local.prepareStatement(GET_SCHEDULE_SQL);
			ResultSet rs = ps.executeQuery();
			List<Match> matches = new ArrayList<>();
			while(rs.next()){
				matches.add(parseMatch(rs));
			}
			try {
				ps = local.prepareStatement(GET_SCHEDULE_ELIMS_SQL);
				rs = ps.executeQuery();
				while(rs.next()){
					matches.add(parseElimsMatch(rs));
				}
			}catch(Exception e) {
				System.err.println("NO ELIMS TABLE FOR "+event);
			}
			return matches;
		}catch(Exception e){
			e.printStackTrace();
			return null;
		}
	}
	
	public static Match getNextMatch(String event){
		try(Connection local = getLocalDB(event)){
			boolean elims = getEvent(event).getStatus() == EventData.ELIMS;
			PreparedStatement ps = local.prepareStatement(elims ? GET_NEXT_ELIMS_MATCH_SQL :GET_NEXT_MATCH_SQL);
			ResultSet rs = ps.executeQuery();
			List<Match> matches = new ArrayList<>();
			while(rs.next()){
				matches.add(elims ? parseElimsMatch(rs) : parseMatch(rs));
			}
			System.out.println(matches.get(0));
			return matches.get(0);
		}catch(Exception e){
			return null;
		}
	}
	
	public static void loadActiveEvents(){
		try(Connection global = DriverManager.getConnection(Server.GLOBAL_DB)){
			Statement stmt = global.createStatement();
			ResultSet rs = stmt.executeQuery(GET_ACTIVE_EVENTS_SQL);
			List<EventData> events = createEventList(rs);
			for(EventData ed : events){
				Event e = new Event(ed);
				//TODO eventually remove this step, done via event management page/software
				//e.setCurrentMatch(getNextMatch(ed.getCode()));
				e.loadNextMatch();
				Server.activeEvents.put(ed.getCode(), e);
				System.out.println("Loaded event "+ed.getCode());
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	public static boolean commitScores(String event, Match match){
		if(match.getNumber() == -1)return true; //test match
		boolean elims = match.isElims();//getEvent(event).getStatus() == EventData.ELIMS;
		try(Connection local = getLocalDB(event)){
			Map<String, String> map = null;
			if(elims) {
				map = new HashMap<String, String>();
				map.put("quals", "elims");
			}
			String sql = elims ? COMMIT_MATCH_DATA.sql.replaceAll("quals","elims") : COMMIT_MATCH_DATA.sql;
			PreparedStatement ps = local.prepareStatement(sql);
			ps.setInt(1, 1);
			ps.setInt(2, match.getRandomization());
			ps.setInt(3,  match.getNumber());
			int affected = ps.executeUpdate();
			/*Don't do this, if we replay a match, there is a 1/6 chance this would break it!
			if(affected != 1){
				return false;
			}
			*/
			
			//TODO TEST THE MAP!
			updater.enqueue(new Update(event, 1, map, COMMIT_MATCH_DATA.id, 1, match.getRandomization(), match.getNumber())); 
			
			sql = elims ? COMMIT_MATCH_RESULTS.sql.replaceAll("quals", "elims") : COMMIT_MATCH_RESULTS.sql;
			ps = local.prepareStatement(sql);
			
			if(elims) {
				Server.activeEvents.get(event).fillCardCarry(match);
			}
			match.getScoreBreakdown();//Force score calc			
			
			

			Alliance blue = match.getBlue();
			Alliance red = match.getRed();
			//ignore red pen if blue has red card, vice  versa
			int redPen = elims && blue.isAllianceRedCard() ? 0 : red.getPenaltyPoints();
			int bluePen = elims && red.isAllianceRedCard() ? 0 : blue.getPenaltyPoints();
			ps.setInt(1, red.getLastScore() );
			ps.setInt(2,  blue.getLastScore());
			ps.setInt(3, redPen);
			ps.setInt(4, bluePen );
			ps.setInt(5,  match.getNumber());
			affected = ps.executeUpdate();
			/*
			if(affected != 1){
				return false;
			}
			*/
			updater.enqueue(new Update(event, 1, map, COMMIT_MATCH_RESULTS.id, red.getLastScore(), blue.getLastScore(), red.getPenaltyPoints(), blue.getPenaltyPoints(), match.getNumber()));
			
			commitAllianceScore(event, match.getNumber(), match.getBlue(), Alliance.BLUE, local, elims);
			commitAllianceScore(event, match.getNumber(), match.getRed(), Alliance.RED, local, elims);
			
			if(!elims) {
				updater.enqueue(new Update(event, Update.COMMAND, null, Update.RECALCULATE_RANKINGS));
			} else {
				StatsCalculator.enqueue(new StatsCalculatorJob(Server.activeEvents.get(event), StatsCalculatorJob.ELIMS));
				updater.enqueue(new Update(event, Update.COMMAND, null, Update.RECALCULATE_ELIMS_STATS));
			}
			Server.activeEvents.get(event).resultsCache.invalidate();
			return true;
		} catch (SQLException e) {
			e.printStackTrace();
			return false;
		}
	}
	private static void setParam(PreparedStatement ps, int index, Alliance a, String field, int type) throws SQLException{
		Object o = a.getScore(field);
		if(o == null){
			ps.setNull(index, type);
		} else{
			ps.setObject(index, o);
		}
	}
	//UPDATE qualsScores SET autoGlyphs=?, cryptoboxKeys=?, jewels=?, parkedAuto=?, glyphs=?, rows=?, columns=?, ciphers=?, relic1Zone=?, relic1Standing=?, relic2Zone=?, relic2Standing=?, balanced=?, major=?, minor=?, cryptobox1=?, cryptobox2=? WHERE match=? AND alliance=?
	private static int commitAllianceScore(String event, int match, Alliance a, int aI, Connection local, boolean elims) throws SQLException{
		//TODO make this a loop.
		String sql = elims ? COMMIT_MATCH_SCORES.sql.replaceAll("quals", "elims") : COMMIT_MATCH_SCORES.sql;
		Map<String, String> map = null;
		if(elims) {
			map = new HashMap<String, String>();
			map.put("quals", "elims");
		}
		PreparedStatement ps = local.prepareStatement(sql);
		setParam(ps, 1, a, "autoGlyphs", Types.INTEGER);
		setParam(ps, 2, a, "cryptoboxKeys", Types.INTEGER);
		setParam(ps, 3, a, "jewels", Types.INTEGER);
		setParam(ps, 4, a, "parkedAuto", Types.INTEGER);
		setParam(ps, 5, a, "glyphs", Types.INTEGER);
		setParam(ps, 6, a, "rows", Types.INTEGER);
		setParam(ps, 7, a, "columns", Types.INTEGER);
		setParam(ps, 8, a, "ciphers", Types.INTEGER);
		setParam(ps, 9, a, "relic1Zone", Types.INTEGER);
		setParam(ps, 10, a, "relic1Standing", Types.BOOLEAN);
		setParam(ps, 11, a, "relic2Zone", Types.INTEGER);
		setParam(ps, 12, a, "relic2Standing", Types.BOOLEAN);
		setParam(ps, 13, a, "balanced", Types.INTEGER);
		setParam(ps, 14, a, "major", Types.INTEGER);
		setParam(ps, 15, a, "minor", Types.INTEGER);
		setParam(ps, 16, a, "cryptobox1", Types.INTEGER);
		setParam(ps, 17, a, "cryptobox2", Types.INTEGER);
		setParam(ps, 18, a, "jewelSet1", Types.INTEGER);
		setParam(ps, 19, a, "jewelSet2", Types.INTEGER);
		setParam(ps, 20, a, "adjust", Types.INTEGER);
		setParam(ps, 21, a, "card1", Types.INTEGER);
		setParam(ps, 22, a, "card2", Types.INTEGER);
		setParam(ps, 23, a, "dq1", Types.INTEGER);
		setParam(ps, 24, a, "dq2", Types.INTEGER);
		ps.setInt(25, match);
		ps.setInt(26,  aI);		
		int r = ps.executeUpdate();
		//this has to be same order
		updater.enqueue(new Update(event, 1, map, COMMIT_MATCH_SCORES.id, a.getScore("autoGlyphs"), a.getScore("cryptoboxKeys"),
				a.getScore("jewels"), a.getScore("parkedAuto"), a.getScore("glyphs"), a.getScore("rows"), 
				a.getScore("columns"), a.getScore("ciphers"), a.getScore("relic1Zone"), a.getScore("relic1Standing"), 
				a.getScore("relic2Zone"), a.getScore("relic2Standing"), a.getScore("balanced"), a.getScore("major"),
				a.getScore("minor"), a.getScore("cryptobox1"), a.getScore("cryptobox2"), a.getScore("jewelSet1"),
				a.getScore("jewelSet2"), a.getScore("adjust"), a.getScore("card1"), a.getScore("card2"), a.getScore("dq1"),
				a.getScore("dq2"), match, aI));
		return r;
	}
	
	private static String json(String name, Object value) {
		return "\"" + name + "\":\"" + (value == null ? "" : value.toString()) + "\"";
	}
	
	public static String getScheduleStatusJSON(String event) {
		try (Connection local = getLocalDB(event)){
			int status = EventDAO.getEvent(event).getStatus();
			PreparedStatement ps = local.prepareStatement(status == EventData.QUALS ? GET_SCHEDULE_STATUS_QUALS : GET_SCHEDULE_STATUS_ELIMS_SQL);
			ResultSet rs = ps.executeQuery();
			String result = "[";
			while(rs.next()) {
				result += "{";
				List<String> list = new ArrayList<String>();
				list.add(json("match", rs.getObject(1)));
				list.add(json("red1", rs.getObject(2)));
				list.add(json("red2", rs.getObject(3)));
				list.add(json("blue1", rs.getObject(4)));
				list.add(json("blue2", rs.getObject(5)));
				list.add(json("status", rs.getObject(6)));
				list.add(json("redScore", rs.getObject(7)));
				list.add(json("blueScore", rs.getObject(8)));
				if(status == EventData.ELIMS) {
					list.add(json("red3", rs.getObject(9)));
					list.add(json("blue3", rs.getObject(10)));
					list.add(json("name", rs.getObject(11)));
				}
				result += String.join(",", list);
				result += "},";
			}
			result = result.substring(0, result.length() - 1);
			result = result + "]";
			return result;
		}
		catch(Exception e) {
			e.printStackTrace();
			return "";
		}
	}
	public static Match getMatch(String event, int num, boolean elims) {
		try(Connection local = getLocalDB(event)){
			int status = getEvent(event).getStatus();
			PreparedStatement ps = local.prepareStatement(elims ?  GET_ELIMS_MATCH_SQL : GET_MATCH_SQL);
			ps.setInt(1,  num);
			ResultSet rs = ps.executeQuery();
			Match m = null;
			while(rs.next()){
				m = elims ?  parseElimsMatch(rs) : parseMatch(rs) ;
			}
			if(m == null)return null;
			return m;
		}catch(Exception e){
			e.printStackTrace();
			return null;
		}
	}
	
	
	public static List<MatchResult> getMatchResults(String event){
		try (Connection local = getLocalDB(event)){
			PreparedStatement ps = local.prepareStatement(GET_RESULTS_QUALS);
			ResultSet rs = ps.executeQuery();
			List<MatchResult> result = new ArrayList<MatchResult>();
			while(rs.next()) {
				Alliance red = new Alliance(rs.getInt(2), rs.getBoolean(3), rs.getInt(4), rs.getBoolean(5));
				Alliance blue = new Alliance(rs.getInt(6), rs.getBoolean(7), rs.getInt(8), rs.getBoolean(9));
				MatchResult mr = new MatchResult(rs.getInt(1), red, blue, rs.getInt(10), rs.getInt(11), rs.getInt(12), rs.getInt(13), rs.getInt(14));
				result.add(mr);
			}
			//"SELECT q.match, q.red, red.team1, red.team2, red.team3, q.blue, blue.team1, blue.team2, blue.team3, redScore, blueScore, status, redPenalty, bluePenalty, q.name
			try {
				ps = local.prepareStatement(GET_RESULTS_ELIMS);
				rs = ps.executeQuery();
				while(rs.next()) {
					Alliance red = new Alliance(rs.getInt(2), rs.getInt(3), rs.getInt(4), rs.getInt(5));
					Alliance blue = new Alliance(rs.getInt(6), rs.getInt(7), rs.getInt(8), rs.getInt(9));
					MatchResult mr = new MatchResult(rs.getInt(1), red, blue, rs.getInt(10), rs.getInt(11), rs.getInt(12), rs.getInt(13), rs.getInt(14), rs.getString(15));
					result.add(mr);
				}
			}catch(Exception e) {
				System.err.println("NO ELIMS TABLES IN DB FOR "+event);
			}
			return result;
		}
		catch(Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	//same as above but with cards & dqs
	public static List<MatchResult> getMatchResultsForRankings(String event){
		try (Connection local = getLocalDB(event)){
			PreparedStatement ps = local.prepareStatement(GET_RESULTS_QUALS);
			ResultSet rs = ps.executeQuery();
			List<MatchResult> result = new ArrayList<MatchResult>();
			HashMap<Integer, MatchResult> map = new HashMap<>();
			while(rs.next()) {
				Alliance red = new Alliance(rs.getInt(2), rs.getBoolean(3), rs.getInt(4), rs.getBoolean(5));
				Alliance blue = new Alliance(rs.getInt(6), rs.getBoolean(7), rs.getInt(8), rs.getBoolean(9));
				MatchResult mr = new MatchResult(rs.getInt(1), red, blue, rs.getInt(10), rs.getInt(11), rs.getInt(12), rs.getInt(13), rs.getInt(14));
				map.put(mr.getNumber(), mr);
				result.add(mr);
			}
			ps = local.prepareStatement(GET_CARDS_DQS_SQL);
			rs = ps.executeQuery();
			while(rs.next()) {
				MatchResult mr = map.get(rs.getInt(1));
				Alliance a = rs.getInt(2) == Alliance.RED ? mr.getRed() : mr.getBlue();
				a.initializeScores();
				a.updateScore("card1", rs.getInt(3));
				a.updateScore("card2", rs.getInt(4));
				a.updateScore("dq1",Boolean.parseBoolean(rs.getObject(5).toString()));
				a.updateScore("dq2", Boolean.parseBoolean(rs.getObject(6).toString()));
				//System.out.println(rs.getInt(1)+" "+rs.getInt(2)+":"+rs.getInt(3)+","+rs.getInt(4)+","+rs.getBoolean(5)+","+rs.getBoolean(6));
			}
//			ps = local.prepareStatement("SELECT match, alliance, dq1, dq2 FROM qualsScores;");
//			rs = ps.executeQuery();
//			while(rs.next()) {
//				System.out.println(rs.getInt(1)+" "+rs.getInt(2)+": "+rs.getObject(3).+","+rs.getObject(4));
//			}
			return result;
		}
		catch(Exception e) {
			if(e.getMessage() == null) {
				e.printStackTrace();
				return null;
			}
			if(e.getMessage().contains("no such column")) {
				System.err.println("OLD DB ("+event+")"+e.getMessage());
				return null;
			}
			e.printStackTrace();
			return null;
		}
	}
	
	
	public static Match getMatchResultFull(String event, int num, boolean elims) {	
		try (Connection local = getLocalDB(event)){
			Match match = getMatch(event, num, elims);
			PreparedStatement ps = local.prepareStatement(elims ? GET_MATCH_RESULTS_FULL_SQL.replaceAll("qual", "elim") : GET_MATCH_RESULTS_FULL_SQL);
			ps.setInt(1, num);
			ResultSet rs = ps.executeQuery();
			 
			while(rs.next()) {
				Alliance a = match.getAlliance(rs.getInt(2) == Alliance.RED ? "red" : "blue");
				a.initializeScores();
				Set<String> keys = a.getScoreFields();
				//TODO URGENT FIX THIS
				keys.remove("card3");
				keys.remove("dq3");
				keys.remove("cbKeys");
				keys.remove("cbRows");
				for(String key : keys) {
					a.updateScore(key, rs.getObject(key));
				}
			}
			return match;
		}
		catch(Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	public static List<MatchResult> getMatchResultsForStats(String code, boolean elims) {
		List<MatchResult> results = getMatchResults(code);
		if(elims) {
			results.removeIf(((Predicate<MatchResult>)MatchResult::isElims).negate());
		} else {
			results.removeIf(MatchResult::isElims);
		}
		try (Connection local = getLocalDB(code)){
			PreparedStatement ps = local.prepareStatement(elims ? GET_MATCH_RESULTS_FOR_STATS_SQL.replaceAll("qual", "elim") : GET_MATCH_RESULTS_FOR_STATS_SQL);
			ResultSet rs = ps.executeQuery();
			
			for(int i = 0;rs.next(); i++) {
				Alliance a = results.get(i/2).getAlliance(rs.getInt(2) == Alliance.RED ? "red" : "blue");
				a.initializeScores();
				Set<String> keys = a.getScoreFields();
				keys.remove("card3");
				keys.remove("dq3");
				keys.remove("cbKeys");
				keys.remove("cbRows");
				for(String key : keys) {
					a.updateScore(key, rs.getObject(key));
				}
				a.randomization = rs.getInt("randomization");
//				results.get(rs.getInt(1)-1).rand = rs.getInt("randomization");
				
			}
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		return results;
	}
	
	public static final int INSPECTOR_SIG = 0;
	public static final int TEAM_SIG = 1;
	public static String[] getFormComments(String eventCode, String formID, int... teamList) {
		String[] result = new String[teamList.length];
		
		try (Connection local = getLocalDB(eventCode)){
			String s  =String.join(",",  IntStream.of(teamList).mapToObj(Integer::toString).collect(Collectors.toList()));
//			System.out.println(s);
			PreparedStatement ps = local.prepareStatement(GET_COMMENT_SQL.replace(":in", s));
			List<Integer> list = new ArrayList<>(teamList.length);
			for(int i:teamList) {
				list.add(i);
			}
			ps.setString(1, formID);
			ResultSet rs = ps.executeQuery();
			while(rs.next()) {
//				System.out.println("Result:"+rs.getInt(1)+":"+rs.getString(2));
				result[list.indexOf(rs.getInt(1))] = rs.getString(2);
			}
//			System.out.println("All done!");
			for(int i = 0; i < result.length; i++) {
				result[i] = result[i] == null ? "" : result[i];
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return result;
	}	
	public static boolean setFormComment(String event, String form, int team, String comment) {
		try (Connection local = getLocalDB(event)){
			PreparedStatement ps = local.prepareStatement(SET_COMMENT_SQL.sql);
			ps.setInt(2, team);
			ps.setString(3,  form);
			ps.setString(1,  comment);
			ps.executeUpdate();
			updater.enqueue(new Update(event, 1, null, SET_COMMENT_SQL.id, comment, team, form));
			return true;
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return false;
	}
	public static boolean updateSigs(String event, String form, int team, int sigIndex, String sig) {
		try (Connection local = getLocalDB(event)){
			PreparedStatement ps = local.prepareStatement(SET_SIG_SQL.sql);
			ps.setInt(2, team);
			ps.setString(3,  form);
			ps.setInt(4,  sigIndex);
			ps.setString(1, sig);
			ps.executeUpdate();
			updater.enqueue(new Update(event, 1, null, SET_SIG_SQL.id, sig, team, form, sigIndex));
			return true;
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return false;
	}
	public static String[] getSigs(String eventCode, String formID, int[] teamList) {
		String[] result = new String[teamList.length * 2];
		try (Connection local = getLocalDB(eventCode)){
			String s = String.join(",",  IntStream.of(teamList).mapToObj(Integer::toString).collect(Collectors.toList()));
			PreparedStatement ps = local.prepareStatement(GET_SIG_SQL.replace(":in", s));
			List<Integer> list = new ArrayList<>(teamList.length);
			for(int i:teamList) {
				list.add(i);
			}
			ps.setString(1, formID);
			ResultSet rs = ps.executeQuery();
			while(rs.next()) {
				result[list.indexOf(rs.getInt(1)) * 2 + rs.getInt(2)] = rs.getString(3);
			}
			for(int i = 0; i < result.length; i++) {
				result[i] = result[i] == null ? "" : result[i];
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return result;
	}
	
	public static Map<Integer, List<Integer>> getCardsForTeams(String event, int...teams){
		try (Connection local = getLocalDB(event)){
			Map<Integer, List<Integer>> map = new HashMap<>();
			for(int t:teams) {
				List<Integer> list = new ArrayList<Integer>();
				PreparedStatement ps = local.prepareStatement(GET_CARDS_FOR_TEAM_SQL);
				for(int i = 0; i < 4; i++) {
					ps.setInt(i+1, t);
				}
				ResultSet rs = ps.executeQuery();
				while(rs.next()) {
					if(rs.getInt(2)==t && rs.getInt(4) >0) {
						list.add(rs.getInt(1));
					}
					if(rs.getInt(3)==t && rs.getInt(5)>0) {
						list.add(rs.getInt(1));
					}
				}
				Collections.sort(list);
				map.put(t, list);				
			}
			return map;
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
	public static boolean executeRemoteUpdate(String event, Map<String, String> v, Object[] p) {
		if(p == null)return true;
		if(p.length == 0)return true;
		String sql = queryMap.get(new Double(p[0].toString()).intValue()).sql;
		if(v != null) {
			for(Entry<String, String> entry : v.entrySet()) {
				sql = sql.replaceAll(entry.getKey(), entry.getValue());
			}
		}
		System.out.println("Executing Update ("+event+"): "+sql+" "+Arrays.toString(p));
		try (Connection local = getLocalDB(event)){			
			PreparedStatement ps = local.prepareStatement(sql);
			for(int i = 1; i < p.length; i++) {
				//params 1 -indexed, so this works beautifully!
				ps.setObject(i, p[i]);
			}
			ps.execute();
			Event e = Server.activeEvents.get(event);
			if(e != null) {
				//this is a little excessive
				e.rankingsCache.invalidate();
				e.resultsCache.invalidate();
				e.scheduleCache.invalidate();
			}
			return true;
		} catch (SQLException e) {
			e.printStackTrace();
			System.out.println("ERROR IN REMOTE UPDATE: "+sql);
			return false;
		}
	}
	
	public static boolean createAlliances(String event, Alliance[] data) {
		try (Connection local = getLocalDB(event)){
			PreparedStatement ps = local.prepareStatement(SET_ALLIANCE_SQL.sql);
			for(int i = 0; i < data.length; i++) {
				ps.setInt(1, data[i].getRank());
				ps.setInt(2, data[i].getTeam1());
				ps.setInt(3, data[i].getTeam2());
				ps.setInt(4, data[i].getTeam3());
				ps.executeUpdate();
				updater.enqueue(new Update(event, Update.EVENT_DB_UPDATE, null, SET_ALLIANCE_SQL.id,data[i].getRank(), data[i].getTeam1(), data[i].getTeam2(), data[i].getTeam3() ));
			}
			if(!Server.activeEvents.containsKey(event)) {
				Server.activeEvents.put(event, new Event(EventDAO.getEvent(event)));
			}
			Server.activeEvents.get(event).scheduleCache.invalidate();
			return true;			
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return false;
	}
	
	
	
	public static boolean createElimsMatches(String event, List<Match> matches) {
		try (Connection local = getLocalDB(event)){
			PreparedStatement ps;
			for(Match match : matches) {
				ps = local.prepareStatement(ADD_ELIMS_MATCH_SQL.sql);
				ps.setInt(1, match.getNumber());
				ps.setInt(2, match.getRed().getRank());
				ps.setInt(3,  match.getBlue().getRank());
				ps.executeUpdate();
				updater.enqueue(new Update(event, Update.EVENT_DB_UPDATE, null, ADD_ELIMS_MATCH_SQL.id, match.getNumber(), match.getRed().getRank(), match.getBlue().getRank()));
				
				ps = local.prepareStatement(ADD_ELIMS_MATCH_DATA_SQL.sql);
				ps.setInt(1,match.getNumber());
				ps.setInt(2, match.isCancelled() ? 2 : 0); //when generating tie breaks, calling method needs to cancel extra matches
				ps.setString(3, match.getName());
				ps.executeUpdate();
				updater.enqueue(new Update(event, Update.EVENT_DB_UPDATE, null, ADD_ELIMS_MATCH_DATA_SQL.id, match.getNumber(), match.isCancelled() ? 2 : 0, match.getName()));
				
				ps = local.prepareStatement(ADD_ELIMS_MATCH_RESULT_SQL.sql);
				ps.setInt(1,  match.getNumber());
				ps.executeUpdate();
				updater.enqueue(new Update(event, Update.EVENT_DB_UPDATE, null, ADD_ELIMS_MATCH_RESULT_SQL.id, match.getNumber()));
				
				ps = local.prepareStatement(ADD_ELIMS_MATCH_SCORES_SQL.sql);
				ps.setInt(1, match.getNumber());
				ps.setInt(2, 0);
				ps.executeUpdate();
				updater.enqueue(new Update(event, Update.EVENT_DB_UPDATE, null, ADD_ELIMS_MATCH_SCORES_SQL.id, match.getNumber(), 0));
				
				ps = local.prepareStatement(ADD_ELIMS_MATCH_SCORES_SQL.sql);
				ps.setInt(1, match.getNumber());
				ps.setInt(2, 1);
				ps.executeUpdate();
				updater.enqueue(new Update(event, Update.EVENT_DB_UPDATE, null, ADD_ELIMS_MATCH_SCORES_SQL.id, match.getNumber(), 1));
			}
			if(!Server.activeEvents.containsKey(event)) {
				Server.activeEvents.put(event, new Event(EventDAO.getEvent(event)));
			}
			Server.activeEvents.get(event).scheduleCache.invalidate();
			return true;
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return false;
	}
	
	public static List<MatchResult> getSeriesResults(String event, String series){
		try (Connection local = getLocalDB(event)){
			PreparedStatement ps = local.prepareStatement(GET_ELIMS_SERIES_RESULTS_SQL);
			ps.setString(1, series+"%");
			List<MatchResult> results = new ArrayList<>();
			ResultSet rs = ps.executeQuery();
			while(rs.next()) {
				results.add(new MatchResult(rs.getInt(1), null, null, rs.getInt(2), rs.getInt(3),rs.getInt(6), rs.getInt(4), rs.getInt(5)));
			}
			return results;
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	public static boolean cancelMatch(String event, int m) {
		try (Connection local = getLocalDB(event)){
			PreparedStatement ps = local.prepareStatement(CANCEL_ELIMS_MATCH_SQL.sql);
			ps.setInt(1, m);
			int affected = ps.executeUpdate();
			updater.enqueue(new Update(event, Update.EVENT_DB_UPDATE, null, CANCEL_ELIMS_MATCH_SQL.id, m));
			return affected == 1;
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return false;
	}
	
	public static boolean uncancelMatch(String event, int m) {
		try (Connection local = getLocalDB(event)){
			PreparedStatement ps = local.prepareStatement(UNCANCEL_ELIMS_MATCH_SQL.sql);
			ps.setInt(1, m);
			int affected = ps.executeUpdate();
			updater.enqueue(new Update(event, Update.EVENT_DB_UPDATE, null, UNCANCEL_ELIMS_MATCH_SQL.id, m));
			return affected == 1;
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return false;
	}
	
	public static Map<Integer, List<Integer>> getCardsElims(String event){
		try (Connection local = getLocalDB(event)){
			PreparedStatement ps = local.prepareStatement(GET_CARDS_ELIMS_SQL);
			Map<Integer, List<Integer>> map = new HashMap<>();
			for(int i = 1; i < 5; i++) {
				map.put(i, new ArrayList<Integer>(2));
			}
			ResultSet rs = ps.executeQuery();
			while(rs.next()) {
				if(rs.getInt(4) > 0) {
					map.get(rs.getInt(2)).add(rs.getInt(1));
				}
				if(rs.getInt(5) > 0) {
					map.get(rs.getInt(3)).add(rs.getInt(1));
				}
			}
			return map;
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	public static int getElimsMatchNumber(String event,String name) {
		try (Connection local = getLocalDB(event)){
			PreparedStatement ps = local.prepareStatement(GET_ELIMS_MATCH_NUMBER_SQL);
			ps.setString(1, name.toUpperCase());
			ResultSet rs = ps.executeQuery();
			if(!rs.next())return 0;
			return rs.getInt(1);
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return 0;
	}
	
	public static int[] getElimsMatchBasic(String event, int match) {
		try (Connection local = getLocalDB(event)){
			PreparedStatement ps = local.prepareStatement(GET_ELIMS_MATCH_BASIC);
			ps.setInt(1, match);
			ResultSet rs = ps.executeQuery();
			if(!rs.next())return null;
			return new int[] {rs.getInt(1), rs.getInt(2)};
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	/*
	 * static final String GET_SELECTIONS = "SELECT * FROM selections ORDER BY id;";
	 
	static final SQL SELECTION_SQL = new SQL(26, "INSERT INTO selections(op, alliance, team) VALUES (?,?,?);");
	static final SQL UNDO_SELECTION_SQL = new SQL(27, "DELETE FROM selections WHERE id IN (SELECT MAX(id) FROM selections);");
	static final SQL CLEAR_SELECTION_SQL = new SQL(28, "DELETE FROM selections;");
	*/
	
	public static List<Selection> getSelections(String event){
		try (Connection local = getLocalDB(event)){
			List<Selection> list = new ArrayList<>();
			PreparedStatement ps = local.prepareStatement(GET_SELECTIONS);
			ResultSet rs = ps.executeQuery();
			while(rs.next()) {
				list.add(new Selection(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4)));
			}
			return list;
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	public static boolean saveSelection(String event, int op, int alliance, int team) {
		try (Connection local = getLocalDB(event)){
			PreparedStatement ps = local.prepareStatement(SELECTION_SQL.sql);
			ps.setInt(1, op);
			ps.setInt(2, alliance);
			ps.setInt(3,  team);
			ps.executeUpdate();
			
			//TODO change these to updates that have the params so server can construct Selection object to execute in parallel with local?
			updater.enqueue(new Update(event, SELECTION_SQL.id, null, op, alliance, team));
			updater.sendNow();
			return true;
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return false;
	}
	
	public static boolean undoSelection(String event) {
		try (Connection local = getLocalDB(event)){
			PreparedStatement ps = local.prepareStatement(UNDO_SELECTION_SQL.sql);
			ps.executeUpdate();
			updater.enqueue(new Update(event, UNDO_SELECTION_SQL.id, null));
			updater.sendNow();
			return true;
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return false;
	}
	
	public static boolean clearSelections(String event) {
		try (Connection local = getLocalDB(event)){
			PreparedStatement ps = local.prepareStatement(CLEAR_SELECTION_SQL.sql);
			ps.executeUpdate();
			updater.enqueue(new Update(event, CLEAR_SELECTION_SQL.id, null));
			updater.sendNow();
			return true;
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return false;
	}
	public static void fillRandomizationData(String event, Match match) {
		try(Connection local = getLocalDB(event)){
			boolean elims = match.isElims();
			PreparedStatement ps = local.prepareStatement(elims ? GET_RANDOM_SQL.replaceAll("qual", "elim") : GET_RANDOM_SQL);
			ps.setInt(1, match.getNumber());
			ResultSet rs = ps.executeQuery();
			if(rs.next()) {
				match.randomize(rs.getInt(1));
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	public static String getProperty(String event, String key) {
		try(Connection local = getLocalDB(event)){
			PreparedStatement ps = local.prepareStatement(GET_PROPERTY_SQL);
			ps.setString(1, key);
			ResultSet rs = ps.executeQuery();
			if(rs.next()) {
				return rs.getString(1);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return null;
	}
	public static void setProperty(String event, String key, String value) {
		try(Connection local = getLocalDB(event)){
			PreparedStatement ps = local.prepareStatement(SET_PROPERTY.sql);
			ps.setString(1, key);
			ps.setString(2, value);
			ps.executeUpdate();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	public static int importTeamList(String event, List<Team> teams) {
		try(Connection local = getLocalDB(event)){
			PreparedStatement ps;
			int added = 0;
			for(Team t : teams) {
				try {
					ps = local.prepareStatement(ADD_TEAM_SQL.sql);
					ps.setInt(1, t.getNumber());
					added += ps.executeUpdate();
					updater.enqueue(new Update(event, 1, null,ADD_TEAM_SQL.id, t.getNumber()));
				}catch(Exception e) {
					//team already there
				}
			}
			return added;
		} catch (SQLException e) {
			e.printStackTrace();
			return -1;
		}
	}
	
	
	public static void deleteQuals(String event) {
		try(Connection local = getLocalDB(event)){
			PreparedStatement ps = local.prepareStatement(DELETE_SCHEDULE_RESULTS_SQL.sql);
			ps.executeUpdate();
			ps = local.prepareStatement(DELETE_SCHEDULE_DATA_SQL.sql);
			ps.executeUpdate();
			ps = local.prepareStatement(DELETE_SCHEDULE_SCORES_SQL.sql);
			ps.executeUpdate();
			ps = local.prepareStatement(DELETE_SCHEDULE_SQL.sql);
			ps.executeUpdate();
		}catch(SQLException e) {
			e.printStackTrace();
		}
	}
	
	public static void deleteElims(String event) {
		try(Connection local = getLocalDB(event)){
			PreparedStatement ps = local.prepareStatement(DELETE_ELIMS_RESULTS_SQL.sql);
			ps.executeUpdate();
			ps = local.prepareStatement(DELETE_ELIMS_DATA_SQL.sql);
			ps.executeUpdate();
			ps = local.prepareStatement(DELETE_ELIMS_SCORES_SQL.sql);
			ps.executeUpdate();
			ps = local.prepareStatement(DELETE_ELIMS_SQL.sql);
			ps.executeUpdate();
			ps = local.prepareStatement(DELETE_ALLIANCES_SQL.sql);
			ps.executeUpdate();
			EventDAO.clearSelections(event);
		}catch(SQLException e) {
			e.printStackTrace();
		}
	}
	
	
	
}

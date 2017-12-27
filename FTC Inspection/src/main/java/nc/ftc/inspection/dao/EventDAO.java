package nc.ftc.inspection.dao;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLType;
import java.sql.Statement;
import java.sql.Types;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import nc.ftc.inspection.Server;
import nc.ftc.inspection.event.Event;
import nc.ftc.inspection.model.Alliance;
import nc.ftc.inspection.model.EventData;
import nc.ftc.inspection.model.FormRow;
import nc.ftc.inspection.model.Match;
import nc.ftc.inspection.model.MatchResult;
import nc.ftc.inspection.model.Team;

public class EventDAO {
	public static final SimpleDateFormat EVENT_DATE_FORMAT = new SimpleDateFormat("MM/dd/yyyy");
	static final String CREATE_EVENT_SQL = "INSERT INTO events(code, name, [date], status) VALUES(?,?,?,0)";
	static final String[] CREATE_EVENT_DB_SQL ={ 
											"ATTACH DATABASE ':code.db' AS local;" , 
											"CREATE TABLE local.teams(number INTEGER PRIMARY KEY);",
											"CREATE TABLE local.formRows (formID VARCHAR(2), type INTEGER, row INTEGER, columnCount INTEGER, description VARCHAR, rule VARCHAR(128), page INTEGER, PRIMARY KEY (formID, row));",
											"CREATE TABLE local.formItems (formID VARCHAR(2), row INTEGER, itemIndex INTEGER, label VARCHAR, req TINYINT, PRIMARY KEY(itemIndex, formID), FOREIGN KEY(formID, row) references formRows(formID, row));",
											"CREATE TABLE local.formStatus(team INTEGER REFERENCES teams(number), formID VARCHAR(2), cbIndex INTEGER, status BOOLEAN, PRIMARY KEY (team, formID, cbIndex), FOREIGN KEY (formID, cbIndex) REFERENCES formRows(formID, itemIndex));" ,
											"CREATE TABLE local.formComments(team INTEGER REFERENCES teams(number), formID VARCHAR(2), comment VARCHAR, PRIMARY KEY (team, formID));",
											"CREATE TABLE local.formSigs(team INTEGER REFERENCES teams(number), formID VARCHAR(2), sigIndex INTEGER, sig VARCHAR, PRIMARY KEY (team, formID, sigIndex));",
											"CREATE TABLE local.preferences (id VARCHAR, value VARCHAR);",
											"CREATE TABLE local.inspectionStatus (team INTEGER PRIMARY KEY REFERENCES teams(number), ci TINYINT, hw TINYINT, sw TINYINT, fd TINYINT, sc TINYINT);",
											"INSERT INTO local.formRows SELECT * FROM formRows;",
											"INSERT INTO local.formItems SELECT * FROM formItems;",											
											
											//TODO where to put cards - qualsData & qualsResults?  matches- red1Card, red2Card, blue1Card, blue2Card?
											"CREATE TABLE local.quals(match INTEGER PRIMARY KEY, red1 INTEGER REFERENCES teams(number), red1S BOOLEAN, red2 INTEGER REFERENCES teams(number), red2S BOOLEAN, blue1 INTEGER REFERENCES teams(number), blue1S BOOLEAN, blue2 INTEGER REFERENCES teams(number), blue2S BOOLEAN);", //non-game specific info
											"CREATE TABLE local.qualsData(match INTEGER REFERENCES quals(match), status INTEGER, randomization INTEGER, PRIMARY KEY (match)); ", //status and game-specific info necessary
											"CREATE TABLE local.qualsResults(match INTEGER REFERENCES quals(match), redScore INTEGER, blueScore INTEGER, redPenalty INTEGER, bluePenalty INTEGER, PRIMARY KEY (match));", //penalties needed to sub out for RP ~non-game specific info
											"CREATE TABLE local.qualsScores(match INTEGER REFERENCES quals(match), alliance TINYINT, autoGlyphs INTEGER, cryptoboxKeys INTEGER, jewels INTEGER, parkedAuto INTEGER, glyphs INTEGER, rows INTEGER, columns INTEGER, ciphers INTEGER, relic1Zone INTEGER, relic1Standing BOOLEAN, relic2Zone INTEGER, relic2Standing BOOLEAN, balanced INTEGER, major INTEGER, minor INTEGER, cryptobox1 INTEGER, cryptobox2 INTEGER, jewelSet1 TINYINT, jewelSet2 TINYINT, adjust INTEGER, card1 INTEGER, card2 INTEGER, dq1 BOOLEAN, dq2 BOOLEAN, PRIMARY KEY (match, alliance) );" //completely game specific (except penalties)
													
											//TODO create trigger for adding item to row
											};
	static final String SET_EVENT_STATUS_SQL = "UPDATE events SET STATUS = ? WHERE code = ?;";
	static final String[] POPULATE_TEAMS_SQL = {
											"INSERT INTO inspectionStatus (team) SELECT number FROM teams;",
											"INSERT INTO formStatus SELECT number, form.formID, itemIndex, 0 FROM teams LEFT JOIN formRows form ON type = 2 LEFT JOIN formItems items ON items.formID = form.formID AND items.row = form.row;",
											"INSERT INTO formComments (team, formID) SELECT number, formID FROM teams LEFT JOIN (SELECT DISTINCT formID from formRows);",
											"INSERT INTO formSigs (team, formID, sigIndex) SELECT number, formID, i from teams LEFT JOIN (SELECT DISTINCT formID from formRows) LEFT JOIN (SELECT 0 AS i UNION SELECT 1 AS i);"
	};
	static final String ADD_TEAM_SQL = "INSERT INTO teams VALUES (?);";
	static final String ADD_TEAM_LATE = "";
	static final String GET_EVENT_LIST_SQL = "SELECT * FROM events;";
	static final String GET_EVENT_SQL = "SELECT * FROM events WHERE code = ?;";
	static final String GET_FORM_ROWS = "SELECT * FROM formRows WHERE formID = ? ORDER BY row";
	static final String GET_FORM_ITEMS = "SELECT items.row, items.itemIndex, items.label, items.req :teamColumns FROM formItems items";
	static final String TEAM_JOINS = " LEFT JOIN formStatus :alias ON :alias.cbIndex = items.itemIndex AND :alias.team = ? AND items.formID = :alias.formID";
	static final String FORM_ITEMS_WHERE = " WHERE items.formID = ? ORDER BY items.row, items.itemIndex";
	//This query should probably be optimized at some point, it has 2 nested selects
	static final String GET_FAILED_ROWS_SQL = "SELECT fr.row, fr.description, fr.rule, fr.page FROM formRows fr INNER JOIN (SELECT row, fi.formID from formItems fi INNER JOIN (select cbIndex, formID from formStatus where formID=? AND team=? AND status=0) j ON fi.itemIndex = j.cbIndex AND fi.formID = j.formID WHERE fi.req=1) j2 ON fr.row = j2.row AND fr.formID = j2.formID ORDER BY fr.row;";
	
	static final String SET_FORM_STATUS_SQL = "UPDATE formStatus SET status = ? WHERE formID = ? AND team = ? AND cbIndex = ?";
	static final String ATTACH_GLOBAL = "ATTACH DATABASE ':pathglobal.db' AS global;";
	static final String GET_STATUS_SQL = "SELECT stat.team, ti.name, :columns FROM inspectionStatus stat LEFT JOIN global.teamInfo ti ON ti.number = stat.team;";
	static final String GET_TEAMSTATUS_SQL = "SELECT * FROM inspectionStatus WHERE team=?;";
	static final String GET_TEAMS_SQL = "SELECT a.number, ti.name FROM teams a LEFT JOIN global.teamInfo ti ON ti.number = a.number ORDER BY a.number;";
	static final String GET_SINGLE_STATUS = "SELECT * FROM inspectionStatus WHERE team = ?";
	static final String SET_STATUS_SQL = "UPDATE inspectionStatus SET :column = ? WHERE team = ?";
	static final String GET_COMMENT_SQL = "SELECT team,comment FROM formComments WHERE team IN (:in) AND formID = ?";
	static final String GET_SIG_SQL = "SELECT team,sigIndex,sig FROM formSigs WHERE team IN (:in) AND formID = ?";
	static final String SET_COMMENT_SQL = "UPDATE formComments SET comment = ? WHERE team = ? AND formID = ? ";
	static final String SET_SIG_SQL = "UPDATE formSigs SET sig = ? WHERE team = ? AND formID = ? AND sigIndex = ? ";
	
	//TODO change to >= 2 when Inspection is stored in active events.
	static final String GET_ACTIVE_EVENTS_SQL = "SELECT * FROM events WHERE status >= 3 AND status <= 5;";
	static final String CREATE_SCHEDULE_SQL = "INSERT INTO quals VALUES (?,?,?,?,?,?,?,?,?);";
	static final String CREATE_SCHEDULE_DATA_SQL = "INSERT INTO qualsData VALUES (?,?,?);";
	static final String CREATE_SCHEDULE_RESULTS_SQL = "INSERT INTO qualsResults (match) VALUES (?);";
	static final String CREATE_SCHEDULE_SCORES_SQL = "INSERT INTO qualsScores (match, alliance) VALUES (?,?);";
	static final String GET_SCHEDULE_SQL = "SELECT * FROM quals";
	static final String GET_NEXT_MATCH_SQL = "SELECT q.* FROM qualsData qd LEFT JOIN quals q ON qd.match == q.match WHERE qd.status==0 ORDER BY match LIMIT 1;";
	static final String GET_MATCH_SQL = "SELECT q.* FROM quals q WHERE q.match=? ORDER BY match LIMIT 1;";
	
	static final String COMMIT_MATCH_DATA = "UPDATE qualsData SET status = ?, randomization = ? WHERE match = ?;";
	static final String COMMIT_MATCH_RESULTS = "UPDATE qualsResults SET redScore = ?, blueScore = ?, redPenalty = ?, bluePenalty = ? WHERE match = ?;";
	static final String COMMIT_MATCH_SCORES = "UPDATE qualsScores SET autoGlyphs=?, cryptoboxKeys=?, jewels=?, parkedAuto=?, glyphs=?, rows=?, columns=?, ciphers=?, relic1Zone=?, relic1Standing=?, relic2Zone=?, relic2Standing=?, balanced=?, major=?, minor=?, cryptobox1=?, cryptobox2=?, jewelSet1=?, jewelSet2=?, adjust=?, card1=?, card2=?, dq1=?, dq2=? WHERE match=? AND alliance=?";
	
	static final String GET_SCHEDULE_STATUS_QUALS = "SELECT q.match, red1, red2, blue1, blue2, status, redScore, blueScore FROM quals q LEFT JOIN qualsData qd ON q.match = qd.match LEFT JOIN qualsResults qr ON q.match = qr.match";
	static final String GET_RESULTS_QUALS = "SELECT q.match, red1, red1S, red2, red2S, blue1, blue1S, blue2, blue2S, redScore, blueScore, status, redPenalty, bluePenalty FROM quals q LEFT JOIN qualsData qd ON q.match = qd.match LEFT JOIN qualsResults qr ON q.match = qr.match";
	static final String GET_MATCH_RESULTS_FULL_SQL = "SELECT * FROM qualsScores s WHERE match=?;";
	
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
			PreparedStatement ps = conn.prepareStatement(CREATE_EVENT_SQL);
			ResultSet rs = ps.executeQuery();
			return createEventList(rs);
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
			e.printStackTrace();
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
	
	public static List<Team> getTeams(String event){
		try(Connection local = getLocalDB(event)){
			Statement stmt = local.createStatement();
			stmt.execute(ATTACH_GLOBAL.replaceAll(":path", Server.DB_PATH));
			stmt.execute(GET_TEAMS_SQL);
			ResultSet rs = stmt.getResultSet();
			List<Team> result = new ArrayList<Team>();
			while(rs.next()){			
				Team team = new Team(rs.getInt("number"), rs.getString("name"));
				result.add(team);
			}
			return result;
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	public static Team getTeamStatus(String event, int teamNo) {
		try(Connection local = getLocalDB(event)){
			PreparedStatement ps = local.prepareStatement(GET_TEAMSTATUS_SQL);
			ps.setInt(1, teamNo);
			ResultSet rs = ps.executeQuery();
			if(!rs.next()) {
				return null;
			}
			Team team = new Team(rs.getInt("team"), "NO NAME");
			for(String c : new String[]{"hw", "sw", "fd", "sc", "ci"}){
				team.setStatus(c, rs.getByte(c));
			}
			return team;
			
		} catch(Exception e) {
			e.printStackTrace();
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
			e.printStackTrace();
		}
		return null;
	}
	
	public static boolean createSchedule(String event, List<Match> matches){
		try(Connection local = getLocalDB(event)){
			for(Match m : matches){
				System.out.println(m.getNumber());
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
				ps = local.prepareStatement(CREATE_SCHEDULE_DATA_SQL);
				ps.setInt(1,  m.getNumber());
				ps.setInt(2, 0);
				ps.setInt(3, 0);
				ps.executeUpdate();
				ps = local.prepareStatement(CREATE_SCHEDULE_RESULTS_SQL);
				ps.setInt(1, m.getNumber());
				ps.executeUpdate();
				ps = local.prepareStatement(CREATE_SCHEDULE_SCORES_SQL);
				ps.setInt(1, m.getNumber());
				ps.setInt(2, 0);
				ps.executeUpdate();
				ps = local.prepareStatement(CREATE_SCHEDULE_SCORES_SQL);
				ps.setInt(1,  m.getNumber());
				ps.setInt(2,  1);
				ps.executeUpdate();
			}			
		} catch(Exception e){
			e.printStackTrace();
			return false;
		}
		return true;
	}
	private static Match parseMatch(ResultSet rs) throws SQLException{
		Alliance red = new Alliance(rs.getInt(2), rs.getBoolean(3), rs.getInt(4), rs.getBoolean(5));
		Alliance blue = new Alliance(rs.getInt(6), rs.getBoolean(7), rs.getInt(8), rs.getBoolean(9));
		return new Match(rs.getInt(1), red, blue);
	}
	public static List<Match> getSchedule(String event){
		try(Connection local = getLocalDB(event)){
			PreparedStatement ps = local.prepareStatement(GET_SCHEDULE_SQL);
			ResultSet rs = ps.executeQuery();
			List<Match> matches = new ArrayList<>();
			while(rs.next()){
				matches.add(parseMatch(rs));
			}
			return matches;
		}catch(Exception e){
			return null;
		}
	}
	public static Match getNextMatch(String event){
		try(Connection local = getLocalDB(event)){
			PreparedStatement ps = local.prepareStatement(GET_NEXT_MATCH_SQL);
			ResultSet rs = ps.executeQuery();
			List<Match> matches = new ArrayList<>();
			while(rs.next()){
				matches.add(parseMatch(rs));
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
				System.out.println(ed.getCode());
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	public static boolean commitScores(String event, Match match){
		try(Connection local = getLocalDB(event)){
			PreparedStatement ps = local.prepareStatement(COMMIT_MATCH_DATA);
			ps.setInt(1, 1);
			ps.setInt(2, match.getRandomization());
			ps.setInt(3,  match.getNumber());
			int affected = ps.executeUpdate();
			if(affected != 1){
				return false;
			}
			//TODO FIX THIS!!!! the calculate scores method is not ok!
			//THE ONLY CORRECT ONE IS IN FULLBREAKDOWN!
			//(if theres a way to edit scores, testing the fix could be done by using that and not changing anything)
			ps = local.prepareStatement(COMMIT_MATCH_RESULTS);
			match.getScoreBreakdown();//Force score calc
			Alliance blue = match.getBlue();
			Alliance red = match.getRed();
			ps.setInt(1, red.getLastScore() );
			ps.setInt(2,  blue.getLastScore());
			ps.setInt(3, red.getPenaltyPoints());
			ps.setInt(4,  blue.getPenaltyPoints());
			ps.setInt(5,  match.getNumber());
			affected = ps.executeUpdate();
			if(affected != 1){
				return false;
			}
			
			commitAllianceScore(match.getNumber(), match.getBlue(), Alliance.BLUE, local);
			commitAllianceScore(match.getNumber(), match.getRed(), Alliance.RED, local);
			
			return affected == 1;
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
	private static int commitAllianceScore(int match, Alliance a, int aI, Connection local) throws SQLException{
		//TODO make this a loop.
		PreparedStatement ps = local.prepareStatement(COMMIT_MATCH_SCORES);
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
		return ps.executeUpdate();
	}
	
	private static String json(String name, Object value) {
		return "\"" + name + "\":\"" + (value == null ? "" : value.toString()) + "\"";
	}
	
	public static String getScheduleStatusJSON(String event) {
		try (Connection local = getLocalDB(event)){
			PreparedStatement ps = local.prepareStatement(GET_SCHEDULE_STATUS_QUALS);
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
	public static Match getMatch(String event, int num) {
		try(Connection local = getLocalDB(event)){
			PreparedStatement ps = local.prepareStatement(GET_MATCH_SQL);
			ps.setInt(1,  num);
			ResultSet rs = ps.executeQuery();
			Match m = null;
			while(rs.next()){
				m = parseMatch(rs);
			}
			if(m == null)return null;
			return m;
		}catch(Exception e){
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
			return result;
		}
		catch(Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	
	public static Match getMatchResultFull(String event, int num) {	
		try (Connection local = getLocalDB(event)){
			Match match = getMatch(event, num);
			PreparedStatement ps = local.prepareStatement(GET_MATCH_RESULTS_FULL_SQL);
			ps.setInt(1, num);
			ResultSet rs = ps.executeQuery();
			 
			while(rs.next()) {
				Alliance a = match.getAlliance(rs.getInt(2) == Alliance.RED ? "red" : "blue");
				a.initializeScores();
				Set<String> keys = a.getScoreFields();
				//TODO URGENT FIX THIS
				keys.remove("card3");
				keys.remove("dq3");
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
	
	public static final int INSPECTOR_SIG = 0;
	public static final int TEAM_SIG = 1;
	public static String[] getFormComments(String eventCode, String formID, int... teamList) {
		String[] result = new String[teamList.length];
		
		try (Connection local = getLocalDB(eventCode)){
			String s  =String.join(",",  IntStream.of(teamList).mapToObj(Integer::toString).collect(Collectors.toList()));
			System.out.println(s);
			PreparedStatement ps = local.prepareStatement(GET_COMMENT_SQL.replace(":in", s));
			List<Integer> list = new ArrayList<>(teamList.length);
			for(int i:teamList) {
				list.add(i);
			}
			ps.setString(1, formID);
			ResultSet rs = ps.executeQuery();
			while(rs.next()) {
				System.out.println("Result:"+rs.getInt(1)+":"+rs.getString(2));
				result[list.indexOf(rs.getInt(1))] = rs.getString(2);
			}
			System.out.println("All done!");
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
			PreparedStatement ps = local.prepareStatement(SET_COMMENT_SQL);
			ps.setInt(2, team);
			ps.setString(3,  form);
			ps.setString(1,  comment);
			System.out.println(team+","+form+","+comment);
			System.out.println(ps.executeUpdate());
			return true;
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return false;
	}
	public static boolean updateSigs(String event, String form, int team, int sigIndex, String sig) {
		try (Connection local = getLocalDB(event)){
			PreparedStatement ps = local.prepareStatement(SET_SIG_SQL);
			ps.setInt(2, team);
			ps.setString(3,  form);
			ps.setInt(4,  sigIndex);
			ps.setString(1, sig);
			System.out.println(team+","+form+","+sigIndex+","+sig);
			ps.executeUpdate();
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
	
}

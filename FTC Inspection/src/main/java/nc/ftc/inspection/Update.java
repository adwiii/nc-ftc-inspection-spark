package nc.ftc.inspection;

import java.util.Map;

import com.google.gson.Gson;

import nc.ftc.inspection.dao.ConfigDAO;
import nc.ftc.inspection.dao.EventDAO;
import nc.ftc.inspection.dao.GlobalDAO;
import nc.ftc.inspection.dao.UsersDAO;


public class Update {
	//variable names intentionally vague
	static int idcount = 1;
	long id;
	public String e;
	public int t;
	Map<String,String> v;
	Object[] p;
	
	//NEVER CHANGE THESE CONSTANTS! They have not been consistently used in external classes!
	public static final transient int EVENT_DB_UPDATE = 1; //contains sql info to run
	public static final transient int GLOBAL_DB_UPDATE = 2; //contains sql info to run
	public static final transient int USER_DB_UPDATE = 3; //contains sql info to run
	public static final transient int COMMAND = 4;  //command to run a certan thing (liek create eventdb or recalc ranking)
	
	public static final transient int SET_EVENT_STATUS_CMD = 1;
	public static final transient int CREATE_EVENT_DB_CMD = 2;
	public static final transient int POPULATE_STATUS_TABLES_CMD = 3;
	//TODO IMPLEMENT
	public static final transient int ACTIVATE_EVENT = 4; //add event to active events
	public static final transient int RECALCULATE_RANKINGS = 5;
	
	//TODO IF A SERVER EVER HAS TO POST ABOUT 2 EVENTS, expand REMOTES TO HAVE A key[] 
	//not two remotes so things dont get duplicated.
	
	
	public Update(String event, int type, Map<String, String> manualSQLVariables, Object...params) {
		this.e = event;
		this.t = type;
		this.v = manualSQLVariables;
		this.p = params;
		id = idcount++;
	}
	
	public boolean execute(String key) {
		//check if event and check out.
		if(!ConfigDAO.checkKey(e, key))return false;
		switch(t) {
		case EVENT_DB_UPDATE:
			EventDAO.executeRemoteUpdate(e, v, p);
			break;
		case GLOBAL_DB_UPDATE:
			GlobalDAO.executeRemoteUpdate(v, p);
			break;
		case USER_DB_UPDATE:
			UsersDAO.executeRemoteUpdate(v, p);
			break;
		case COMMAND:
			executeCommand();
			break;			
		}
		
		return true;
	}
	
	private int getInt(int index) {
		return new Double(p[index].toString()).intValue();
	}
	
	private boolean executeCommand() {
		switch(new Double(p[0].toString()).intValue()) {
		case SET_EVENT_STATUS_CMD:       return EventDAO.setEventStatus(e, getInt(1));
		case CREATE_EVENT_DB_CMD:        return EventDAO.createEventDatabase(e);
		case POPULATE_STATUS_TABLES_CMD: return EventDAO.populateStatusTables(e);
		case RECALCULATE_RANKINGS:       Server.activeEvents.get(e).calculateRankings();
										 return true;
		}
		return false;
	}
	
	public String toString() {
		return new Gson().toJson(this);
	}
}

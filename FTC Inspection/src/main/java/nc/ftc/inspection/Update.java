package nc.ftc.inspection;

import java.util.Map;

import com.google.gson.Gson;

import nc.ftc.inspection.dao.EventDAO;


public class Update {
	//variable names intentionally vague
	static int idcount = 1;
	long id;
	String e;
	int t;
	Map<String,String> v;
	Object[] p;
	
	public static final transient int EVENT_DB_UPDATE = 1; //contains sql info to run
	public static final transient int GLOBAL_DB_UPDATE = 2; //contains sql info to run
	public static final transient int COMMAND = 4;  //command to run a certan thing (liek create eventdb or recalc ranking)
	
	public Update(String event, int type, Map<String, String> manualSQLVariables, Object...params) {
		this.e = event;
		this.t = type;
		this.v = manualSQLVariables;
		this.p = params;
		id = idcount++;
	}
	
	public boolean execute() {
		switch(t) {
		case EVENT_DB_UPDATE:
			//check if key & event check out
			EventDAO.executeRemoteUpdate(e, v, p);
			break;
		}
		return true;
	}
	
	public String toString() {
		return new Gson().toJson(this);
	}
}

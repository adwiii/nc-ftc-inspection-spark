package nc.ftc.inspection;

import java.util.Map;

import com.google.gson.Gson;


public class Update {
	//variable names intentionally vague
	String e;
	int t;
	Map<String,String> v;
	Object[] p;
	
	public static final transient int DB_UPDATE = 1;
	public static final transient int COMMAND = 2; 
	
	public Update(String event, int type, Map<String, String> manualSQLVariables, Object...params) {
		this.e = event;
		this.t = type;
		this.v = manualSQLVariables;
		this.p = params;
	}
	
	public String toString() {
		return new Gson().toJson(this);
	}
}

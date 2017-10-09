package nc.ftc.inspection.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class Alliance {
	//TODO where to put (static) mapping of values to points? Here?
	int team1, team2;
	boolean surrogate1 = false;
	boolean surrogate2 = false;
	//Keep scores in hash map - easy to change each year, easy to isolate for PUT requests.
	Map<String, Object> scores;
	transient boolean scoreSubmitted = false;
	//how they are stored in the db in the matchScores tables
	public static transient final int RED = 0;
	public static transient final int BLUE = 1;
	static Map<String, Number> scoreMap = new HashMap<>();
	static{
		//TODO put all the possible fields in an array/db, make this support multiple years.
		//table with name, points, type
		scoreMap.put("autoGlyphs", 15);
		scoreMap.put("cryptoboxKeys", 30);
		scoreMap.put("jewels", 30);
		scoreMap.put("parkedAuto", 10);
		scoreMap.put("glyphs", 2);
		scoreMap.put("rows", 10);
		scoreMap.put("columns", 20);
		scoreMap.put("ciphers", 30);
		scoreMap.put("relic1Standing", 15);
		scoreMap.put("relic2Standing", 15);
		scoreMap.put("balanced", 20);
	}
	
	public Alliance(int t1, int t2){
		team1 = t1;
		team2 = t2;
	}
	public Alliance(int t1, boolean s1, int t2, boolean s2){
		this(t1,t2);
		surrogate1 = s1;
		surrogate2 = s2;
	}
	public int getTeam1(){
		return team1;
	}
	public int getTeam2(){
		return team2;
	}
	public boolean is1Surrogate(){
		return surrogate1;
	}
	public boolean is2Surrogate(){
		return surrogate2;
	}
	
	public void initializeScores(){
		scores = new HashMap<>();
		scores.put("major", 0);
		scores.put("minor", 0);
		scores.put("autoGlyphs", 0);
		scores.put("cryptoboxKeys", 0);
		scores.put("jewels", 0);
		scores.put("parkedAuto", 0);
		scores.put("glyphs", 0);
		scores.put("rows", 0);
		scores.put("columns", 0);
		scores.put("ciphers", 0);
		scores.put("relic1Zone", 0);
		scores.put("relic1Standing", false);
		scores.put("relic2Zone", 0);
		scores.put("relic2Standing", false);
		scores.put("balanced", 0);
		scores.put("cryptobox1", 0);
		scores.put("cryptobox2", 0);
	}
	
	public void updateScore(String field, Object value){
		
		Object old = scores.get(field);
		if(old == null){
			throw new IllegalArgumentException("Invalid field "+field);
		}
		if(!old.equals(value)){
			scores.put(field, value);
			//TODO notify anyone who needs update on scores. Different than state change cuz need to know what field changed
		}
	}
	
	public Object getScore(String field){
		return scores.get(field);
	}
	
	public List<String> getScores(){
		List<String> list = new ArrayList<String>();
		for(Entry<String, Object> entry : scores.entrySet()){
			list.add("\"" + entry.getKey() +"\":\"" + entry.getValue()+"\"");
		}
		return list;
	}
	
	public int calculateScore(){
		int score = 0;
		for(Entry<String, Number> entry : scoreMap.entrySet()){
			Object item = scores.get(entry.getKey());
			if(item instanceof Number){
				score += entry.getValue().intValue() * ((Number)item).intValue();
			} else if(item instanceof Boolean){
				if(((Boolean) item).booleanValue()){
					score += entry.getValue().intValue() ;
				}
			}
		}
		return score;
	}
	
	public int getPenaltyPoints(){
		return 10 * ((Number)scores.get("minor")).intValue() + 40 * ((Number)scores.get("major")).intValue();
	}
	
	public void setSubmitted(boolean sub){
		this.scoreSubmitted = sub;
	}
	public boolean scoreSubmitted(){
		return scoreSubmitted;
	}
}

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
	transient boolean autoSubmitted = false;
	transient boolean inReview = false;
	transient int randomization = 0;//set by match & stored in match.
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
		scores.put("adjust", 0); //for double red cards
		//Field-status 
		scores.put("cryptobox1", 0);
		scores.put("cryptobox2", 0);
		scores.put("jewelSet1", 0b11);
		scores.put("jewelSet2", 0b11);
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
	
	public Set<String> getScoreFields(){
		return scores.keySet();
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
		return 10 * Integer.parseInt(scores.get("minor").toString())+ 40 * Integer.parseInt(scores.get("major").toString());
	}
	
	public void setSubmitted(boolean sub){
		this.scoreSubmitted = sub;
	}
	public boolean scoreSubmitted(){
		return scoreSubmitted;
	}
	
	public void setAutoSubmitted(boolean sub){
		this.autoSubmitted = sub;
	}
	public boolean autoSubmitted(){
		return autoSubmitted;
	}
	public void setInReview(boolean ir) {
		this.inReview = ir;
	}
	public boolean isInReview() {
		return inReview;
	}
	
	//1,2,3 is red left, 4, 5, 6, is blue left
	public int getRedJewels() {
		int count = 0;
		if(randomization < 4) {
			//left
			if(Integer.parseInt(scores.get("jewelSet1").toString()) == 0b10)count++;
			if(Integer.parseInt(scores.get("jewelSet2").toString()) == 0b10)count++;
		} else {
			//right
			if(Integer.parseInt(scores.get("jewelSet1").toString()) == 0b01)count++;
			if(Integer.parseInt(scores.get("jewelSet2").toString()) == 0b01)count++;
		}
		return count;
	}
	public int getBlueJewels() {
		int count = 0;
		if(randomization > 3) {
			//left
			if(Integer.parseInt(scores.get("jewelSet1").toString()) == 0b10)count++;
			if(Integer.parseInt(scores.get("jewelSet2").toString()) == 0b10)count++;
		} else {
			//right
			if(Integer.parseInt(scores.get("jewelSet1").toString()) == 0b01)count++;
			if(Integer.parseInt(scores.get("jewelSet2").toString()) == 0b01)count++;
		}
		return count;
	}
	public int zonePoints(int zone) {
		if(zone == 1)return 10;
		if(zone == 2)return 20;
		if(zone == 3)return 40;
		return 0;
	}
	public int getRelicPoints() {
		int points = zonePoints(Integer.parseInt(scores.get("relic1Zone").toString()));
		points += zonePoints(Integer.parseInt(scores.get("relic2Zone").toString()));
		points += Boolean.parseBoolean(scores.get("relic1Standing").toString()) ? 15 : 0;
		points += Boolean.parseBoolean(scores.get("relic2Standing").toString()) ? 15 : 0;
		return points;
	}
	/**
	 * DO NOT CALL FROM REVIEW PAGE
	 */
	public void calculateGlyphs() {
		//calculate number of glyphs, number of rows, number of columns, number of ciphers.
		int ciphers = 0;
		int rows = 0;
		int columns = 0;
		int glyphs = 0;
		for(int i = 1 ; i < 3; i++) {
			int cb = Integer.parseInt(scores.get("cryptobox"+i).toString());
			int cbInv = ((~cb) & 0xFFFFFF);
			if(cb == 6710886 || cbInv == 6710886 || cb == 6908265 || cbInv == 6908265 || cb == 10065510 || cbInv == 10065510) {
				ciphers++;
			}
			
			for(int r = 0; r < 4; r++) {
				int t = (cb >> (r * 6)) & 0x3F; //t is the value for the row.
				int thisRow = 0;
				while(t > 0) {
					thisRow++;
					t = t & (t-1);
				}
				glyphs += thisRow;
				if(thisRow == 3) {
					rows++;
				}
			}
			
			final int COLUMN_MASK = 0b11000011000011000011;
			for(int c = 0; c < 3; c++) {
				int t = cb & (COLUMN_MASK << (2*c));
				int thisColumn = 0;
				while(t > 0) {
					thisColumn++;
					t = t & (t-1);
				}
				glyphs += thisColumn;
				if(thisColumn == 4) {
					columns++;
				}
			}			
		}
		 // each glyph counted twice.
		glyphs /= 2;
		scores.put("glyphs", glyphs);
		scores.put("rows", rows);
		scores.put("columns", columns);
		scores.put("ciphers", ciphers);
	}
	
}

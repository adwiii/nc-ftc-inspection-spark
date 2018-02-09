package nc.ftc.inspection.model;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import nc.ftc.inspection.dao.EventDAO;

public class Match {
	int number;
	String name;
	Alliance red;
	Alliance blue;
	public transient Map<String, String> redScoreBreakdown;
	public transient Map<String, String> blueScoreBreakdown;
	public transient MatchStatus status; //pre-match (pre-randomize), auto, auto-review, teleop, teleop-review, pre-commit, post-commit
	transient int randomization = 0; 
	long lastChange = 0;
	Object scoreLock = new Object();
	public boolean refLockout = false;
	boolean cancelled = false;
	
	public static transient final Match TEST_MATCH;
	
	static {
		Alliance red = new Alliance(-1, -2);
		Alliance blue = new Alliance(-3,-4);
		TEST_MATCH = new Match(-1, red, blue, "Test Match");
	}
	
	public Match(int num, Alliance red, Alliance blue){
		this.red = red;
		this.blue = blue;
		this.number = num;
	}
	public Match(int num, Alliance red, Alliance blue, String name) {
		this(num,red,blue);
		this.name = name;
	}
	public boolean isCancelled() {
		return cancelled;
	}
	public void setCancelled(boolean c) {
		cancelled = c;
	}
	public String getName() {
		if(name == null) {
			return "Qualification Match "+number;
		}
		return name;
	}
	public int getNumber(){
		return number;
	}
	public Alliance getRed(){
		return red;
	}
	public Alliance getBlue(){
		return blue;
	}
	public int randomize(){
		Random r = new Random();
		randomization = r.nextInt(6) + 1;
		red.randomization = this.randomization;
		blue.randomization = this.randomization;
		return randomization;
	}
	public int randomize(int v) {
		if(v == 0) {
			return randomize();
		}
		randomization = v;
		red.randomization = this.randomization;
		blue.randomization = this.randomization;
		return randomization;		
	}
	public boolean isRandomized(){
		return randomization != 0;
	}
	public void clearRandom() {
		randomization = 0;
	}
	public int getRandomization(){
		return randomization;
	}
	public void setStatus(MatchStatus stat){
		status = stat;
		//TODO Fire events to any observers 
		//Pre-random->auto= scorekeeper, AD? (show result of random), non-HR tablets
		//MOVED EVENT FIRING to calling methods.
		
		if(status == MatchStatus.PRE_RANDOM){
			red.initializeScores();
			blue.initializeScores();
		}
	}
	public MatchStatus getStatus(){
		return status;
	}
	public Alliance getAlliance(String a){
		return a.equals("red") ? red : (a.equals("blue") ? blue : null);
	}
	public boolean autoSubmitted(){
		return red.autoSubmitted() && blue.autoSubmitted();
	}
	
	public boolean scoreSubmitted(){
		return red.scoreSubmitted() && blue.scoreSubmitted();
	}
	public void clearSubmitted(){
		red.setSubmitted(false);
		blue.setSubmitted(false);
		red.setAutoSubmitted(false);
		blue.setAutoSubmitted(false);
	}
	public boolean isInReview() {
		return red.isInReview() && blue.isInReview();
	}
	
	
	public void updateJewels() {
		red.updateScore("jewels", red.getRedJewels() + blue.getRedJewels());
		blue.updateScore("jewels", blue.getBlueJewels() + red.getBlueJewels());
	}
	public void calculateEndAuto() {
		updateJewels();
	}
	
	private String json(String name, Object value) {
		return "\"" + name + "\":\"" + value.toString() + "\"";
	}
	
	/**
	 *
	 * @param a
	 * @return
	 */
	private List<String> getScoreBreakdownNoPenalty(Alliance a) {
		List<String> list = new ArrayList<String>();
		int jewelPoints = 0;
		if(a == red) {
			jewelPoints = red.getRedJewels() + blue.getRedJewels();
		}
		else {
			jewelPoints = red.getBlueJewels() + blue.getBlueJewels();
		}
		jewelPoints = Integer.parseInt(a.scores.get("jewels").toString());
		jewelPoints *= 30;
		int glyphAutoPoints = 15 * Integer.parseInt(a.scores.get("autoGlyphs").toString());
		int keyBonus = 30 * Integer.parseInt(a.scores.get("cryptoboxKeys").toString());
		int parkingPoints = 10 * Integer.parseInt( a.scores.get("parkedAuto").toString());
		int autoPoints = jewelPoints + glyphAutoPoints + keyBonus + parkingPoints;
		list.add(json("jewelPoints", jewelPoints));
		list.add(json("glyphAutoPoints", glyphAutoPoints));
		list.add(json("keyPoints", keyBonus));
		list.add(json("parkingPoints", parkingPoints));
		list.add(json("autoPoints", autoPoints));
		
		int glyphPoints = 2 * Integer.parseInt(a.scores.get("glyphs").toString());
		int rowPoints = 10 * Integer.parseInt(a.scores.get("rows").toString());
		int columnPoints = 20 * Integer.parseInt(a.scores.get("columns").toString());
		int cipherPoints = 30 * Integer.parseInt(a.scores.get("ciphers").toString());
		int relicPoints = a.getRelicPoints();
		int balancePoints = 20 * Integer.parseInt(a.scores.get("balanced").toString());
		int teleopPoints = glyphPoints + rowPoints + columnPoints + cipherPoints + relicPoints + balancePoints; 
		
		
		
		int adjust = Integer.parseInt(a.scores.get("adjust").toString());
		
		//store this in ram (call this method to calculate score, that way this code is only written once)
		a.lastCalculatedScoreNoPenalties = autoPoints + teleopPoints + adjust;
		
		list.add(json("noPenaltyScore", autoPoints + teleopPoints + adjust));
		list.add(json("glyphPoints", glyphPoints));
		list.add(json("rowPoints", rowPoints));
		list.add(json("columnPoints", columnPoints));
		list.add(json("cipherPoints", cipherPoints));
		list.add(json("relicPoints", relicPoints));
		list.add(json("balancePoints", balancePoints));
		list.add(json("teleopPoints", teleopPoints));
		list.add(json("endPoints", relicPoints + balancePoints));
		
		list.add(json("adjust", adjust));
		
		
		list.add(json("cryptobox1", a.scores.get("cryptobox1")));
		list.add(json("cryptobox2", a.scores.get("cryptobox2")));
		list.add(json("cbKeys", a.scores.get("cbKeys") == null ? "0" : a.scores.get("cbKeys")));
		Object o = a.scores.get("relic1Zone");
		if(o != null)list.add(json("relic1Zone", o));
		o = a.scores.get("relic2Zone");
		if(o != null)list.add(json("relic2Zone", o));
		o = a.scores.get("relic1Standing");
		if(o != null)list.add(json("relic1Standing", o));
		o = a.scores.get("relic2Standing");
		if(o != null)list.add(json("relic2Standing", o));
		o = a.scores.get("cbRows");
		if(o !=null)list.add(json("cbRows", o));
		list.add(json("jewelSet1", a.scores.get("jewelSet1")));
		list.add(json("jewelSet2", a.scores.get("jewelSet2")));
		return list;
	}
	
	/**
	 * Ensure that the carryCard flag in the Alliance objects has been set before calling for Elims calculations
	 * @param a
	 * @return
	 */
	public String getScoreBreakdown(Alliance a) {
		String res =  String.join(",", getScoreBreakdownNoPenalty(a))+", \"foulPoints\":\"";
		Alliance opp = (a == red ? blue : red);
		res += opp.getPenaltyPoints() + "\"";
		int pen = 0;
		res += ",\"minorPoints\":\"" + opp.getMinorPenaltyPoints() + "\"";
		res += ",\"majorPoints\":\"" + opp.getMajorPenaltyPoints() + "\"";
		int score = (a.lastCalculatedScoreNoPenalties + opp.getPenaltyPoints());
		if(this.isElims()) {
			int card = Integer.parseInt(a.getScore("card1").toString());
			if(card > 1 || (card == 1) && a.carriesCard()) {
				score = Integer.parseInt(a.getScore("adjust").toString());
				a.lastCalculatedScoreNoPenalties = score;
			} 
		}
		res += ",\"score\":\"" + score + "\"";
		return "{" + res + "}";
	}
	
	public String getScoreBreakdown(){
		Gson gson = new Gson();
		//taken from https://stackoverflow.com/questions/2779251/how-can-i-convert-json-to-a-hashmap-using-gson
		Type type = new TypeToken<Map<String, String>>(){}.getType();
		String redBreakdown = getScoreBreakdown(red);
		redScoreBreakdown = gson.fromJson(redBreakdown, type);
		String blueBreakdown = getScoreBreakdown(blue);
		blueScoreBreakdown = gson.fromJson(blueBreakdown, type);
		return "{\"red\":"+redBreakdown+",\"blue\":"+blueBreakdown+", \"ts\":"+lastChange+"}";
	}
	
	
	public String getFullScores() {
		return "{\"red\":{"+String.join(",", red.getScores())+"},\"blue\":{"+String.join(",",blue.getScores())+"}, \"ts\":"+lastChange+"}";
	}
	
	public String getFieldDisplayScores() {
		return "";
	}
	
	public long getLastUpdate() {
		return lastChange;
	}
	
	public Object getUpdateLock() {
		return scoreLock;
	}
	
	public void updateNotify() {
		lastChange = System.currentTimeMillis();
		synchronized(scoreLock) {
			scoreLock.notifyAll();
		}
	}
	
	public boolean isElims() {
		return name != null && name.indexOf('F') >= 0;
	}
	public void setName(String name) {
		this.name = name;
	}
	public void setNumber(int number) {
		this.number = number;
		
	}
	
	
}

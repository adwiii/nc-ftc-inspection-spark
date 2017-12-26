package nc.ftc.inspection.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Match {
	int number;
	Alliance red;
	Alliance blue;
	public transient MatchStatus status; //pre-match (pre-randomize), auto, auto-review, teleop, teleop-review, pre-commit, post-commit
	transient int randomization = 0; 
	long lastChange = 0;
	Object scoreLock = new Object();
	public boolean refLockout = false;
	public Match(int num, Alliance red, Alliance blue){
		this.red = red;
		this.blue = blue;
		this.number = num;
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
	public boolean isRandomized(){
		return randomization != 0;
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
		
		list.add(json("glyphPoints", glyphPoints));
		list.add(json("rowPoints", rowPoints));
		list.add(json("columnPoints", columnPoints));
		list.add(json("cipherPoints", cipherPoints));
		list.add(json("relicPoints", relicPoints));
		list.add(json("balancePoints", balancePoints));
		list.add(json("teleopPoints", teleopPoints));
		
		list.add(json("adjust", Integer.parseInt(a.scores.get("adjust").toString())));
		
		
		list.add(json("cryptobox1", a.scores.get("cryptobox1")));
		list.add(json("cryptobox2", a.scores.get("cryptobox2")));
		list.add(json("jewelSet1", a.scores.get("jewelSet1")));
		list.add(json("jewelSet2", a.scores.get("jewelSet2")));
		return list;
	}
	
	private String getScoreBreakdown(Alliance a) {
		String res =  String.join(",", getScoreBreakdownNoPenalty(a))+", \"foulPoints\":\"";
		res += (a == red ? blue : red).getPenaltyPoints() + "\"";
		return res;
	}
	
	public String getScoreBreakdown(){
		return "{\"red\":{"+getScoreBreakdown(red)+"},\"blue\":{"+getScoreBreakdown(blue)+"}, \"ts\":"+lastChange+"}";
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
}

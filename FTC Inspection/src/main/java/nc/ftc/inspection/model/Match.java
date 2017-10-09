package nc.ftc.inspection.model;

import java.util.Random;

public class Match {
	int number;
	Alliance red;
	Alliance blue;
	public transient MatchStatus status; //pre-match (pre-randomize), auto, auto-review, teleop, teleop-review, pre-commit, post-commit
	transient int randomization = 0; 
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
		//
		synchronized(stat){
			stat.notifyAll();
		}
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
	public boolean scoreSubmitted(){
		return red.scoreSubmitted() && blue.scoreSubmitted();
	}
	public void clearSubmitted(){
		red.setSubmitted(false);
		blue.setSubmitted(false);
	}
}

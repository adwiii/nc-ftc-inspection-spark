package nc.ftc.inspection.model;

public class Alliance {
	//TODO where to put (static) mapping of values to points? Here?
	int team1, team2;
	boolean surrogate1 = false;
	boolean surrogate2 = false;
	int majorPenalty;
	int minorPenalty;
	
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
}

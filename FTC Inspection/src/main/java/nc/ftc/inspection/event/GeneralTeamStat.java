package nc.ftc.inspection.event;

import java.text.DecimalFormat;
import java.util.HashMap;

import nc.ftc.inspection.model.Team;

//General Team Stats
public class GeneralTeamStat {
	public Team team;
	public double OPR;
	public int rank;
	public double teleopOPR;
	public double autoOPR;
	public double defenseOPR;
	public double avgScore;
	public double avgMargin;
	public double largestMargin;
	public double highestScore;
	HashMap<String, Number> stats = new HashMap<String, Number>();
	public GeneralTeamStat(Team t) {
		team = t;
	}
	public Team getTeam() {
		return team;
	}
	public int getRank() {
		return rank;
	}
	public double getOPR() {
		return OPR;
	}
	public String getOprString() {
		DecimalFormat df = new DecimalFormat("#.00"); 
		return df.format(OPR);
	}
	
	public void setStat(String s, Number n) {
		stats.put(s, n);
	}
	public Number getStat(String key) {
		Number n = stats.get(key);
		if(n == null) {
			return Double.NaN;
		}
		if(Double.isInfinite(n.doubleValue()))return Double.NaN;
		return n;
	}
	public String getDoubleString(String key) {
		DecimalFormat df = new DecimalFormat("#.00"); 
		return df.format(getStat(key));
	}
	public int getInt(String key) {
		return getStat(key).intValue();
	}
	
}

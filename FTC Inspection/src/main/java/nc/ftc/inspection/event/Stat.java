package nc.ftc.inspection.event;

import java.text.DecimalFormat;

import nc.ftc.inspection.model.Team;

public class Stat {
	public Team team;
	public double OPR;
	public int rank;
	public Stat(Team t) {
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
}

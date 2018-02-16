/*
 * Copyright (c) 2016-2018 Thomas Barnette and Trey Woodlief
 * All Rights Reserved.
 */

package nc.ftc.inspection.event;

import java.util.ArrayList;
import java.util.List;

import nc.ftc.inspection.model.Team;

public class Rank implements Comparable<Rank>{
	Team team;
	public int QP;
	public int RP;
	int plays;
	boolean card;
	int highest;
	List<Integer> scores = new ArrayList<>();
	int rank;
	public Rank(Team t) {
		this.team = t;
	}
	@Override
	public int compareTo(Rank r) {
		if(QP != r.QP) {
			return r.QP-QP;
		}
		if(RP != r.RP) {
			return r.RP - RP;
		}
		int i = 0;
		//highest scores
		for(;i < Math.min(scores.size(), r.scores.size());i++) {
			int t = r.scores.get(i) - scores.get(i);
			if(t != 0) return t;
		}
		return r.plays - plays; // if one has more plays than the other, it has a higher score last score (which is the tie breaker at this point)		
	}
	
	public Team getTeam() {
		return team;
	}
	public int getQP() {
		return QP;
	}
	public int getRP() {
		return RP;
	}
	public int getHighest() {
		return highest;
	}
	public int getPlays() {
		return plays;
	}
	public void setRank(int r) {
		this.rank = r;
	}
	public int getRank() {
		return rank;
	}
}

/*
 * Copyright (c) 2016-2018 Thomas Barnette and Trey Woodlief
 * All Rights Reserved.
 */

package nc.ftc.inspection.model;

public class Selection {
	private int id;
	private int op;
	private int alliance;
	private int team;
	
	//team become permanent captain
	public static final transient int CAPTAIN = 1;
	//team accepts
	public static final transient int ACCEPT = 2;
	//team declines
	public static final transient int DECLINE = 3;
	
	public Selection(int id, int op, int alliance, int team) {
		this.id = id;
		this.op = op;
		this.alliance = alliance;
		this.team = team;
	}
	
	public int getOp() {
		return op;
	}
	
	public int getAlliance() {
		return alliance;
	}
	public int getTeam() {
		return team;
	}
	
	
}

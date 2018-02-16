/*
 * Copyright (c) 2016-2018 Thomas Barnette and Trey Woodlief
 * All Rights Reserved.
 */

package nc.ftc.inspection.model;

public class MatchResult {
//	public int rand;
	Alliance red;
	Alliance blue;
	int redScore;
	int blueScore;
	int redPenalty;
	int bluePenalty;
	int redTotal;
	int blueTotal;
	char winChar;
	String winColor;
	int number;
	
	String RED = "#FF4444";
	String BLUE = "#44AAFF";
	int status;
	String name;
	
	//TODO for 2 team elims, make that show up properly in the results page.
	//Blue score included auto, teleop, and adjust
	//Blue penalty is # of points resulting from BLUE penalties. They count toward RED!
	public MatchResult(int n, Alliance r, Alliance b, int rs, int bs, int status, int redP, int blueP, String name) {
		this(n,r,b,rs,bs,status,redP,blueP);
		this.name = name;
	}
	public MatchResult(int n, Alliance r, Alliance b, int rs, int bs, int status, int redP, int blueP) {
		red = r;
		blue = b;
		number = n;
		redScore = rs;
		blueScore = bs;
		redPenalty = redP;
		bluePenalty = blueP;
		redTotal = redScore + bluePenalty;
		blueTotal = blueScore + redPenalty;
		if(redTotal > blueTotal) {
			winColor = RED;
			winChar = 'R';
		}
		else if(blueTotal > redTotal) {
			winColor = BLUE;
			winChar = 'B';
		} else {
			winChar = 'T';
			//the official software has #CCC for all ties even though it alternates if the match hasn't played yet
			winColor = "#CCCCCC";
		}
		this.status = status;
	}
	
	public Alliance getRed() {
		return red;
	}
	public Alliance getBlue() {
		return blue;
	}
	public int getNumber() {
		return number;
	}
	public int getRedScore() {
		return redScore;
	}
	public int getBlueScore() {
		return blueScore;
	}
	public int getRedPenalty() {
		return redPenalty;
	}
	public int getBluePenalty() {
		return bluePenalty;
	}
	public int getRedTotal() {
		return redTotal;
	}
	public int getBlueTotal() {
		return blueTotal;
	}
	public char getWinChar() {
		return winChar;
	}
	public String getWinColor() {
		return winColor;
	}
	public int getStatus() {
		return status;
	}
	public String getName() {
		return name == null ? "Qualification Match "+getNumber() : name;
	}
	public String getShortName() {
		return name == null ? "Match "+getNumber() : name;
	}
	public boolean isElims() {
		return getName().indexOf('-') >= 0;
	}
	public Alliance getAlliance(String a) {
		return a.equals("red") ? red : (a.equals("blue") ? blue : null);
	}
	public double getMargin() {
		return Math.abs(getRedTotal()-getBlueTotal());
	}
}

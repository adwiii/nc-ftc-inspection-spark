/*
 * Copyright (c) 2016-2018 Thomas Barnette and Trey Woodlief
 * All Rights Reserved.
 */

package nc.ftc.inspection.model;

public class Feedback {
	String comment;
	long time;
	public Feedback(String c, long t) {
		comment = c;
		time = t;
	}
	public String getComment() {
		return comment;
	}
	public long getTime() {
		return time;
	}
}

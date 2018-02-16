/*
 * Copyright (c) 2016-2018 Thomas Barnette and Trey Woodlief
 * All Rights Reserved.
 */

package nc.ftc.inspection.event;

public enum TimerCommand {
	/**
	 * This enum also covers non-timing commands for the FieldDisplay, but most are timing.
	 */
	START, PAUSE, RESUME, RESET, LOAD_MATCH, FIELD_TO, TEAM_TO, END_TO
}

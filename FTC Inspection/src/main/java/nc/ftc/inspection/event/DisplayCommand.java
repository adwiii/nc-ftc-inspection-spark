package nc.ftc.inspection.event;

//Each display long-polls for next command,
//different displays can ignore certain ones, for example,
//field display should ignore show preview/results unless it is being
//used as the primary display
public enum DisplayCommand {
	SHOW_PREVIEW, SHOW_RANDOM, SHOW_MATCH, SHOW_RESULT, STOP_SCORE_UPDATES
}

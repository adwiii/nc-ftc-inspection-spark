package nc.ftc.inspection.model;

public enum MatchStatus {
	PRE_RANDOM, AUTO, AUTO_REVIEW, TELEOP, REVIEW, PRE_COMMIT, POST_COMMIT;
	public MatchStatus next(){
		switch(this){
		case PRE_RANDOM: return AUTO;
		case AUTO:return AUTO_REVIEW;
		case AUTO_REVIEW:return TELEOP;
		case TELEOP:return REVIEW;
		case REVIEW:return PRE_COMMIT;
		case PRE_COMMIT:return POST_COMMIT;
		case POST_COMMIT:return PRE_RANDOM;		
		default:
			return PRE_RANDOM;		
		}
	}
	
	public boolean canAcceptScores(){
		return this == AUTO || this == TELEOP || this == AUTO_REVIEW || this == REVIEW; 
	}
	
	public boolean isReview(){
		return this == AUTO_REVIEW || this == REVIEW;
	}
}

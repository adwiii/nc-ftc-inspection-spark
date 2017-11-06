package nc.ftc.inspection.event;

import nc.ftc.inspection.dao.EventDAO;
import nc.ftc.inspection.model.EventData;
import nc.ftc.inspection.model.Match;
import nc.ftc.inspection.model.MatchStatus;

public class Event {
	EventData data;
	Match currentMatch;
	Match previousMatch;	
	public Event(EventData ed){
		this.data = ed;
	}
	public Match getCurrentMatch(){
		return currentMatch;
	}
//	public void setCurrentMatch(Match nextMatch) {
//		currentMatch = nextMatch;
//	}
	public void loadNextMatch(){
		previousMatch = currentMatch;
		currentMatch = EventDAO.getNextMatch(data.getCode());
		if(currentMatch == null){
			System.err.println("Unable to load matches for event "+data.getCode());
			return;
		}
		if(previousMatch != null){
			previousMatch.setStatus(MatchStatus.POST_COMMIT);
		}
		currentMatch.setStatus(MatchStatus.PRE_RANDOM);
		System.out.println("Loaded match #"+currentMatch.getNumber());
	}
	
	public void loadMatch(int num) {
		Match temp = currentMatch;
		currentMatch = EventDAO.getMatch(data.getCode(), num);
		if(currentMatch == null) {
			currentMatch = temp;
		} else {
			previousMatch = currentMatch;
			currentMatch.setStatus(MatchStatus.PRE_RANDOM);
			System.out.println("Loaded match #"+currentMatch.getNumber());
		}
		
	}
	
	
}

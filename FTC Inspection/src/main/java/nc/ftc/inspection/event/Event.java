package nc.ftc.inspection.event;

import java.util.ArrayList;
import java.util.List;

import nc.ftc.inspection.dao.EventDAO;
import nc.ftc.inspection.model.EventData;
import nc.ftc.inspection.model.Match;
import nc.ftc.inspection.model.MatchStatus;

public class Event {
	//TODO keep list of match result objects here.
	List<Integer> rankings = new ArrayList<Integer>(); 
	EventData data;
	Match currentMatch;
	Match previousMatch;	
	
	//Monitors for messaging and long polls
	public Object waitForRefLock = new Object();
	public Object waitForPreviewLock = new Object();
	public Object waitForRandomLock = new Object();
	
	
	
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
	
	public EventData getData() {
		return data;
	}
	
	
}

/*
 * Copyright (c) 2016-2018 Thomas Barnette and Trey Woodlief
 * All Rights Reserved.
 */

package nc.ftc.inspection.event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.linear.CholeskyDecomposition;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.SingularValueDecomposition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nc.ftc.inspection.Cache;
import nc.ftc.inspection.Server;
import nc.ftc.inspection.dao.EventDAO;
import nc.ftc.inspection.event.StatsCalculator.StatsCalculatorJob;
import nc.ftc.inspection.model.Alliance;
import nc.ftc.inspection.model.EventData;
import nc.ftc.inspection.model.Match;
import nc.ftc.inspection.model.MatchResult;
import nc.ftc.inspection.model.MatchStatus;
import nc.ftc.inspection.model.Team;

public class Event {
	//TODO keep list of match result objects here.
	EventData data;
	Match currentMatch;
	Match previousMatch;	
	
	
	public Cache<List<MatchResult>> resultsCache = new Cache<>();
	public Cache<List<Rank>> rankingsCache = new Cache<>();
	public Cache<List<Match>> scheduleCache = new Cache<>(60000 * 60);//hour
	//TODO combine these in the future.
	public Cache<Map<Integer, Team>> teamNameCache = new Cache<>(60000 * 10);
	public Cache<Map<Integer,Team>> teamStatusCache = new Cache<>(Long.MAX_VALUE);
	
	//Monitors for messaging and long polls
	public Object waitForRefLock = new Object();
	public Object waitForPreviewLock = new Object();
	public Object waitForRandomLock = new Object();
	
	//keep null until first inspection write.
	//this way old / noninspecting events dont create extra resources
	BulkTransactionManager inspectionManager;
	
	static Logger log;
	static{
		if(!Server.redirected) {
			Server.redirectError();
		}
		log = LoggerFactory.getLogger(Event.class);
	}
	
	Timer timer = new Timer(this);
	Display display = new Display();
	SelectionManager selection = new SelectionManager(this);
	
	List<Rank> rankings = new ArrayList<Rank>();
	List<GeneralTeamStat> teamStats = new ArrayList<>();//TODO kill
	Map<String,EventStat> qualsStats = new HashMap<>();
	Map<String, EventStat> elimsStats = new HashMap<>();
//	List<Stat> teamStats = new ArrayList<Stat>(); //Only Quals
//	List<Stat> qualsEventStats = new ArrayList<Stat>();
//	List<Stat> elimsEventStats = new ArrayList<Stat>();
	
	
	
	public Event(EventData ed){
		this.data = ed;
	}
	public Match getCurrentMatch(){
		return currentMatch;
	}
//	public void setCurrentMatch(Match nextMatch) {
//		currentMatch = nextMatch;
//	}
	
	private void loadTestMatch() {
		currentMatch = Match.TEST_MATCH;
		currentMatch.refLockout = false;
		currentMatch.setStatus( MatchStatus.PRE_RANDOM);
		currentMatch.clearRandom();
		currentMatch.clearSubmitted();
		currentMatch.getRed().initializeScores();
		currentMatch.getBlue().initializeScores();
		log.info("Loaded Test Match");
	}
	public void loadNextMatch(){
		timer.started = false;
		if(currentMatch != null && currentMatch == Match.TEST_MATCH) {
			loadTestMatch();
			return;
		}
		previousMatch = currentMatch;
		currentMatch = EventDAO.getNextMatch(data.getCode());
		if(currentMatch == null){
			log.warn("Unable to load matches for event "+data.getCode());
			return;
		}
		if(previousMatch != null){
			previousMatch.setStatus(MatchStatus.POST_COMMIT);
		}
		if(currentMatch.isElims()) { //for breakdown calc
			fillCardCarry(currentMatch);
		}
		currentMatch.setStatus(MatchStatus.PRE_RANDOM);
		log.info("Loaded match #"+currentMatch.getNumber());
	}
	
	public void loadMatch(int num) {
		timer.started = false;
		Match temp = currentMatch;
		currentMatch = num == -1 ? Match.TEST_MATCH : EventDAO.getMatch(data.getCode(), num, data.getStatus() >= EventData.ELIMS);
		if(currentMatch == null) {
			currentMatch = temp;
		} else {
			if(currentMatch == Match.TEST_MATCH) {
				loadTestMatch();
			}				
			previousMatch = currentMatch;
			currentMatch.setStatus(MatchStatus.PRE_RANDOM);
			log.info("Loaded "+currentMatch.getName());
		}
		
	}
	
	
	
	public EventData getData() {
		return data;
	}
	
	public Timer getTimer() {
		return timer;
	}
	
	public Display getDisplay() {
		return display;
	}
	
	private boolean isDQ(int teamIndex, Alliance a, Rank r) {
		if(Boolean.parseBoolean(a.getScore("dq"+teamIndex).toString()))return true;
		int card = Integer.parseInt(a.getScore("card"+teamIndex).toString());
		if(card == 2)return true;
		if(card == 1 && r.card) return true; //elevate yellow
		return false;
	}
	
	public void calculateRankings() {
		rankings.clear();
		rankingsCache.invalidate();
		List<Team> teams = EventDAO.getTeams(data.getCode());
		HashMap<Integer, Rank> map = new HashMap<Integer, Rank>();
		for(Team t : teams) {
			Rank r = new Rank(t);
			rankings.add(r);
			map.put(t.getNumber(), r);
		}
		List<MatchResult> results = EventDAO.getMatchResultsForRankings(data.getCode());
		
		//See manual Part 1 Section 5.8.
		for(MatchResult mr : results) {
			if(mr.getStatus() == 1) { //match has been played
				Alliance red = mr.getRed();
				Alliance blue = mr.getBlue();
			//	System.out.println(mr.getNumber()+":"+red.getScore("dq1")+","+red.getScore("dq2")+","+blue.getScore("dq1")+","+blue.getScore("dq2"));
				Rank red1 = map.get(red.getTeam1());
				Rank red2 = map.get(red.getTeam2()); 
				Rank blue1 = map.get(blue.getTeam1());
				Rank blue2 = map.get(blue.getTeam2()); 
				boolean dqRed1 = isDQ(1, red, red1 );
				boolean dqRed2 = isDQ(2, red, red2);
				boolean dqBlue1 = isDQ(1, blue, blue1);
				boolean dqBlue2 = isDQ(2, blue, blue2);
				//QP
				switch(mr.getWinChar()) {
				case 'R':
					if(!dqRed1 && !red.is1Surrogate()) red1.QP += 2;
					if(!dqRed2 && !red.is2Surrogate()) red2.QP += 2;
					break;
				case 'B':
					if(!dqBlue1 && !blue.is1Surrogate()) blue1.QP += 2;
					if(!dqBlue2 && !blue.is2Surrogate()) blue2.QP += 2;
					break;
				case 'T':
					if(!dqRed1 && !red.is1Surrogate()) red1.QP ++;
					if(!dqRed2 && !red.is2Surrogate()) red2.QP ++;
					if(!dqBlue1 && !blue.is1Surrogate()) blue1.QP ++;
					if(!dqBlue2 && !blue.is2Surrogate()) blue2.QP ++;
					break;
				}
				
				//RP
				//If tie, all non-DQd teams get lowest pre-penalty
				if(mr.getWinChar() == 'T') {
					int RP = Math.min(mr.getRedScore(), mr.getBlueScore());
					if(!dqRed1&& !red.is1Surrogate()) red1.RP += RP;
					if(!dqRed2&& !red.is2Surrogate()) red2.RP += RP;
					if(!dqBlue1 && !blue.is1Surrogate()) blue1.RP += RP;
					if(!dqBlue2 && !blue.is2Surrogate()) blue2.RP += RP;
				} else {
					//if both teams on losing alliance DQ'd, winning team gets their SCORE (inc penalties) as RP
					//ignoring that case for now!
					//get other teams pre-penalty score
					int RP = 0;
					if(mr.getWinChar() == 'R')RP = mr.getBlueScore();
					if(mr.getWinChar() == 'B')RP = mr.getRedScore();
					if(!dqRed1&& !red.is1Surrogate()) red1.RP += RP;
					if(!dqRed2&& !red.is2Surrogate()) red2.RP += RP;
					if(!dqBlue1 && !blue.is1Surrogate()) blue1.RP += RP;
					if(!dqBlue2 && !blue.is2Surrogate()) blue2.RP += RP;
				
				}//TODO call this method after every commit and on recalc command
				
				
				
				//add matches to list of scores for non-DQ/surrogate teams
				if(!dqRed1 && !red.is1Surrogate()) red1.scores.add(mr.getRedTotal());
				if(!dqRed2 && !red.is2Surrogate()) red2.scores.add(mr.getRedTotal());
				if(!dqBlue1 && !blue.is1Surrogate()) blue1.scores.add(mr.getBlueTotal());
				if(!dqBlue2 && !blue.is2Surrogate()) blue2.scores.add(mr.getBlueTotal());
				
				//count matches played (do not count surrogates)
				if(!red.is1Surrogate())red1.plays++;
				if(!red.is2Surrogate())red2.plays++;
				if(!blue.is1Surrogate())blue1.plays++;
				if(!blue.is2Surrogate())blue2.plays++;
				
				//carry cards (surrogates?)
				if(Integer.parseInt(red.getScore("card1").toString()) > 0)red1.card = true;
				if(Integer.parseInt(red.getScore("card2").toString()) > 0)red2.card = true;
				if(Integer.parseInt(blue.getScore("card1").toString()) > 0)blue1.card = true;
				if(Integer.parseInt(blue.getScore("card2").toString()) > 0)blue2.card = true;
			}
			
		}
		
		for(Rank r : rankings) {
			Collections.sort(r.scores);
			Collections.reverse(r.scores);
			r.highest = r.scores.size() > 0 ? r.scores.get(0) : 0;
		}
		Collections.sort(rankings);
		for(int i = 0; i < rankings.size(); i++) {
			rankings.get(i).setRank(i + 1);
		}
		try {
			StatsCalculator.enqueue(new StatsCalculatorJob(this, StatsCalculatorJob.QUALS));
			
		}catch(Exception e) {
			log.warn("Err calculating OPR for "+data.getCode());
		}
	}
	
	public List<Rank> getRankings(){
		return rankings;
	}
	
	public int getRank(int team) {
		for(int i = 0; i < rankings.size(); i++) {
			if(rankings.get(i).team.getNumber() == team) {
				return i + 1;
			}
		}
		return -1;
	}
	/**
	 * Sets the cardCary flag in the two alliances in the given match, using card data from this event.
	 * @param m
	 */
	public void fillCardCarry(Match m) {
		if(!m.isElims())throw new IllegalStateException("DONT SET CARD CARRY FOR NON_ELIMINATION MATCHES!");
		String isFD = EventDAO.getProperty(this.getData().getCode(), "isFinalsDivision");
		if(isFD != null && isFD.equals("true")) {
			if(EventDAO.getProperty(this.getData().getCode(), "redCard").equals("true")) {
				m.getRed().setCardCarry(true);
			}
			if(EventDAO.getProperty(this.getData().getCode(), "blueCard").equals("true")) {
				m.getBlue().setCardCarry(true);
			}
			
		}
		Map<Integer, List<Integer>> cardMap = EventDAO.getCardsElims(data.getCode());
		List<Integer> cardList = cardMap.get(m.getRed().getRank());
		if(cardList.size()>0 && cardList.get(0) < m.getNumber()) {
			m.getRed().setCardCarry(true);
		}
		cardList = cardMap.get(m.getBlue().getRank());
		if(cardList.size()>0 && cardList.get(0) < m.getNumber()) {
			m.getBlue().setCardCarry(true);
		}
	}
	
	public SelectionManager getSelectionManager() {
		return selection;
	}
	public List<GeneralTeamStat> getTeamStats() {
		return teamStats;
	}
	public Map<String, EventStat> getQualsStats() {
		return qualsStats;
	}
	public  Map<String, EventStat> getElimsStats() {
		return elimsStats;
	}
	
	public boolean setFormStatus(String form, int team, int itemIndex, boolean status) {
		if(inspectionManager == null) {
			inspectionManager = new BulkTransactionManager(this);
		}
		//TODO check that team exists in inspection table
		//EventDAO.setTeamStatus(getData().getCode(), form, team, 1); //In progress = 1
		if(teamStatusCache.get() == null) {
			//this will populate the status cache
			EventDAO.getStatus(getData().getCode());
		} 
		Team t = teamStatusCache.get().get(team);
		if(t == null) {
			//team not in system.
			return false;
		}
		if(t.getStatus(form) != 1) {
			EventDAO.setTeamStatus(getData().getCode(), form, team, 1);
		}
		EventDAO.setFormStatus(getData().getCode(), inspectionManager, form,team, itemIndex, status);
		return true;
	}
	
}

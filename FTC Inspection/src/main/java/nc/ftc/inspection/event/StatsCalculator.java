/*
 * Copyright (c) 2016-2018 Thomas Barnette and Trey Woodlief
 * All Rights Reserved.
 */

package nc.ftc.inspection.event;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.SingularValueDecomposition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nc.ftc.inspection.Server;
import nc.ftc.inspection.Update;
import nc.ftc.inspection.dao.EventDAO;
import nc.ftc.inspection.model.Alliance;
import nc.ftc.inspection.model.MatchResult;
import nc.ftc.inspection.model.Team;

public class StatsCalculator extends Thread{
	static Logger log;
	static{
		if(!Server.redirected) {
			Server.redirectError();
		}
		log = LoggerFactory.getLogger(StatsCalculator.class);
	}
	public static class StatsCalculatorJob {
		Event event;
		int phase;
		public static final int ELIMS = 2;
		public static final int QUALS = 1;
		public StatsCalculatorJob(Event e, int phase) {
			this.event = e;
			this.phase = phase;
		}
	}
	private Queue<StatsCalculatorJob> queue = new LinkedList<>();
	private StatsCalculator() {
		start();
	}
	private static StatsCalculator calculator = new StatsCalculator();
	private volatile boolean shutdown = false;
	
	public void shutdown() {
		shutdown = true;
		synchronized(this) {
			this.notify();
		}
	}
	
	public static void enqueue(StatsCalculatorJob update) {
		synchronized(calculator) {
			calculator.queue.offer(update);
			calculator.notifyAll();
		}
	}
	
	public void run() {
		while(!shutdown) {
			if(queue.isEmpty()) {
				synchronized(this) {					
					try {
						this.wait();
					} catch(InterruptedException e) {
						//we've probably been killed or something
					}
				}		
			} else {
				StatsCalculatorJob job;
				synchronized(this) {
					job = queue.poll(); 
				}
				try {
					calculateStats(job);
				}catch(Exception e) {
					System.err.println("Error calculating stats: "+job.event.getData().getCode()+", "+job.phase);
					e.printStackTrace();
				}
			}				
		}
	}
	
	public void calculateStats(StatsCalculatorJob job) {
		List<MatchResult> results = EventDAO.getMatchResultsForStats(job.event.getData().getCode(), job.phase == job.ELIMS);
		if(results == null) {
			return;
		}
		if(results.size() == 0) {
			log.warn("Results size == 0, cannot calculate stats for {}",job.event.getData().getCode());
			
			return;
		}
		List<Rank> rankings = new ArrayList<>(job.event.getRankings()); //clone of rankings
		double[][][] matrices = createMatrices(results,rankings);
		double[][] A = matrices[0];
		double[][] B = matrices[1];
		double[][] plays = matrices[2];
		//TODO fully populated results
		if(job.phase == job.QUALS) {
			//general team stats (needs only MatchResult objects)
			//team GS stats (needs fully populated alliance objects)
			//Event GS stats Quals (needs full)
			//Event general stats Quals (needs only results)
			job.event.teamStats = calculateGeneralTeamStats(A, B, results, rankings, plays);
			job.event.qualsStats = calculateEventStats(results);
		} else {
			job.event.elimsStats = calculateEventStats(results);
		}
	}
	
	private double[][][] createMatrices( List<MatchResult> results, List<Rank> rankings) {
		if(results == null) {
			return null;
		}
		double[][][] res = new double[3][][];//[results.size() * 2][rankings.size()];
		res[0] = new double[results.size() * 2][rankings.size()];
		double[][] A = res[0];
		res[1] = new double[results.size()*2][1];
		res[2] = new double[8][rankings.size()]; //0 is plays, 1 is red plays, 2 is blue plays, 3 is for jewel RL, 4 is for jewel LR, 5 is for left key, 6 is for center key, 7 is for right key
			
		HashMap<Integer, Integer> ind = new HashMap<Integer, Integer>();
		for(int i = 0; i < rankings.size(); i++) {
			ind.put(rankings.get(i).getTeam().getNumber(), i);
			res[2][0][i] = rankings.get(i).plays;
		}
		for(int r = 0; r < A.length; r+=2) {
			MatchResult mr = results.get(r / 2);
			
			if(mr.getStatus() != 1)continue;
			
			int red1 = ind.get(mr.getRed().getTeam1());
			int red2 = ind.get(mr.getRed().getTeam2());
			int blue1 = ind.get(mr.getBlue().getTeam1());
			int blue2 = ind.get(mr.getBlue().getTeam2());
			
			//index of red 
			A[r][red1] = 1;
			A[r][red2] = 1;
			//index of blue
			A[r+1][blue1] = 1;
			A[r+1][blue2] = 1;
			
			//red plays
			res[2][1][red1]++;
			res[2][1][red2]++;
			//blue plays
			res[2][2][blue1]++;
			res[2][2][blue2]++;
			if(mr.getRed().randomization > 0) { //leave 0 for no randomization data.
				//jewel plays
				int i = mr.getRed().isRedLeft() ? 3 : 4;
				res[2][i][red1]++;
				res[2][i][red2]++;
				res[2][i][blue1]++;
				res[2][i][blue2]++;
				//key plays
				i = mr.getRed().isKeyLeft() ? 5 : (mr.getRed().isKeyCenter() ? 6 : 7);				
				res[2][i][red1]++;
				res[2][i][red2]++;
				res[2][i][blue1]++;
				res[2][i][blue2]++;
			}
		}		
		return res;
	}	
	
	private RealMatrix calculatePR(double[][] A, double[][] B) {
		RealMatrix matchData = MatrixUtils.createRealMatrix(A);
        SingularValueDecomposition svd = new SingularValueDecomposition(matchData);
        return svd.getSolver().solve(MatrixUtils.createRealMatrix(B));
	}
	
	private RealMatrix calculatePRAdjusted(double[][] A, double[][] B, double[] totalPlays, double[] countedPlays) {
		RealMatrix res = calculatePR(A,B);
		for(int i = 0; i < totalPlays.length; i++) {
			 res.multiplyEntry(i, 0, totalPlays[i]/countedPlays[i]);
		 }
		 return res;
	}
	
	private RealMatrix calculateAverage(double[][] A, double[][] B, double[] plays) {
		 RealMatrix res = MatrixUtils.createRealMatrix(A).transpose().multiply(MatrixUtils.createRealMatrix(B));
		 for(int i = 0; i < plays.length; i++) {
			 res.multiplyEntry(i, 0, 1/plays[i]);
		 }
		 return res;
	}
	
	private void populateBMatrix(double[][] B, List<MatchResult> results, ToDoubleFunction<MatchResult> red, ToDoubleFunction<MatchResult> blue) {
		for(int r = 0; r < B.length; r+=2) {
			MatchResult mr = results.get(r / 2);
			if(mr == null) {
				B[r][0] = 0;
				B[r+1][0] = 0;
			} else {
				try {
				B[r][0] = red.applyAsDouble(mr);
				B[r+1][0] = blue.applyAsDouble(mr);
				}catch(Exception e) {
					B[r][0] = 0;
					B[r+1][0] = 0;
				}
			}
		}
	}
	
	private List<GeneralTeamStat> calculateGeneralTeamStats(double[][] A, double[][] B, List<MatchResult> results, List<Rank> rankings, double[][] playArrays) {
		
//		System.out.println("A:"+Arrays.toString(A));
		double[] plays = playArrays[0];
		double[] redPlays = playArrays[1];
		double[] bluePlays = playArrays[2];
		double[] jewelRLPlays = playArrays[3];
		double[] jewelRRPlays = playArrays[4];
		double[] leftPlays = playArrays[5];
		double[] centerPlays = playArrays[6];
		double[] rightPlays = playArrays[7];
		
		List<GeneralTeamStat> stats = new ArrayList<>();
		for(Rank r : rankings) {
			GeneralTeamStat s = new GeneralTeamStat(r.getTeam());
			s.rank = r.getRank();
			stats.add(s);
		}
		populateBMatrix(B, results, MatchResult::getRedScore, MatchResult::getBlueScore);
		fillGeneralTeamStats(stats, "OPRNP", calculatePR(A,B));
		fillGeneralTeamStats(stats, "avgScoreNP", calculateAverage(A,B, plays));
		
		populateBMatrix(B, results, MatchResult::getRedTotal, MatchResult::getBlueTotal);
		fillGeneralTeamStats(stats, "OPR", calculatePR(A,B));
		fillGeneralTeamStats(stats, "avgScore", calculateAverage(A,B, plays));
		
		populateBMatrix(B, results, mr->mr.getRed().getAutoScore(), mr->mr.getBlue().getAutoScore());
		fillGeneralTeamStats(stats, "autoOPR", calculatePR(A,B));
		
		populateBMatrix(B, results, mr->mr.getRed().getTeleopScore(), mr->mr.getBlue().getTeleopScore());
		fillGeneralTeamStats(stats, "teleopOPR", calculatePR(A,B));
		
		populateBMatrix(B, results, mr->mr.getRedTotal() - mr.getBlueTotal(), mr-> mr.getBlueTotal() - mr.getRedTotal());
		fillGeneralTeamStats(stats, "avgMargin", calculateAverage(A,B,plays));
		
		//Game specific, both OPR and avg for each
		//avg jewels scored by this alliance, ignores other alliance mess ups
		//TODO make getJewelStats method, if field info is known, do this, and add negative for wrong jewel, if field info not know, use jewels score entry.
		populateBMatrix(B, results, mr->getScore(mr.getRed(), "jewels"), mr->getScore(mr.getBlue(),"jewels"));
		fillGeneralTeamStats(stats, "jewelAvg", calculateAverage(A,B,plays));
		fillGeneralTeamStats(stats, "jewelPR", calculatePR(A,B));
		
		populateBMatrix(B, results, mr->getScore(mr.getRed(), "jewels"), mr->0);
		fillGeneralTeamStats(stats, "jewelRedAvg", calculateAverage(A,B,redPlays));
		fillGeneralTeamStats(stats, "jewelRedPR", calculatePRAdjusted(A,B, plays, redPlays));
		
		populateBMatrix(B, results, mr->0, mr->getScore(mr.getBlue(),"jewels"));
		fillGeneralTeamStats(stats, "jewelBlueAvg", calculateAverage(A,B,bluePlays));
		fillGeneralTeamStats(stats, "jewelBluePR", calculatePRAdjusted(A,B, plays, bluePlays));		
		
		populateBMatrix(B, results, mr->{
			Alliance red = mr.getRed();
			return red.isRedLeft() ? red.getRedJewels() : 0;
		}, mr->{
			Alliance blue = mr.getBlue();
			return blue.isRedLeft() ? blue.getBlueJewels() : 0;
		});
		fillGeneralTeamStats(stats, "jewelLeftAvg", calculateAverage(A,B,jewelRLPlays));
		fillGeneralTeamStats(stats, "jewelLeftPR", calculatePRAdjusted(A,B, plays, jewelRLPlays));	
		
		populateBMatrix(B, results, mr->{
			Alliance red = mr.getRed();
			return red.isRedLeft() ? 0 : red.getRedJewels();
		}, mr->{
			Alliance blue = mr.getBlue();
			return blue.isRedLeft() ?  0 : blue.getBlueJewels();
		});
		fillGeneralTeamStats(stats, "jewelRightAvg", calculateAverage(A,B,jewelRRPlays));
		fillGeneralTeamStats(stats, "jewelRightPR", calculatePRAdjusted(A,B, plays, jewelRRPlays));
		
		populateBMatrix(B, results, mr->Double.parseDouble(mr.getRed().getScore("cryptoboxKeys").toString()), mr->Double.parseDouble(mr.getBlue().getScore("cryptoboxKeys").toString()));
		fillGeneralTeamStats(stats, "keyAvg", calculateAverage(A,B,plays));
		fillGeneralTeamStats(stats, "keyPR", calculatePR(A,B));
		
		populateBMatrix(B, results, mr->mr.getRed().isKeyLeft() ? Double.parseDouble(mr.getRed().getScore("cryptoboxKeys").toString()) : 0, mr->mr.getBlue().isKeyLeft() ? Double.parseDouble(mr.getBlue().getScore("cryptoboxKeys").toString()) : 0);
		fillGeneralTeamStats(stats, "keyLeftAvg", calculateAverage(A,B,leftPlays));
		fillGeneralTeamStats(stats, "keyLeftPR", calculatePRAdjusted(A,B, plays, leftPlays));
		
		populateBMatrix(B, results, mr->mr.getRed().isKeyCenter() ? Double.parseDouble(mr.getRed().getScore("cryptoboxKeys").toString()) : 0, mr->mr.getBlue().isKeyCenter() ? Double.parseDouble(mr.getBlue().getScore("cryptoboxKeys").toString()) : 0);
		fillGeneralTeamStats(stats, "keyCenterAvg", calculateAverage(A,B,centerPlays));
		fillGeneralTeamStats(stats, "keyCenterPR", calculatePRAdjusted(A,B, plays, centerPlays));
		
		populateBMatrix(B, results, mr->mr.getRed().isKeyRight() ? Double.parseDouble(mr.getRed().getScore("cryptoboxKeys").toString()) : 0, mr->mr.getBlue().isKeyRight() ? Double.parseDouble(mr.getBlue().getScore("cryptoboxKeys").toString()) : 0);
		fillGeneralTeamStats(stats, "keyRightAvg", calculateAverage(A,B,rightPlays));
		fillGeneralTeamStats(stats, "keyRightPR", calculatePRAdjusted(A,B, plays, rightPlays));
		
		populateBMatrix(B, results, mr->Double.parseDouble(mr.getRed().getScore("parkedAuto").toString()),mr->Double.parseDouble(mr.getBlue().getScore("parkedAuto").toString()));
		fillGeneralTeamStats(stats, "parkingAvg", calculateAverage(A,B,plays));
		fillGeneralTeamStats(stats, "parkingPR", calculatePR(A,B));
		
		populateBMatrix(B, results, mr->Double.parseDouble(mr.getRed().getScore("balanced").toString()),mr->Double.parseDouble(mr.getBlue().getScore("balanced").toString()));
		fillGeneralTeamStats(stats, "balancedAvg", calculateAverage(A,B,plays));
		fillGeneralTeamStats(stats, "balancedPR", calculatePR(A,B));
		
		populateBMatrix(B, results, mr->Double.parseDouble(mr.getRed().getScore("glyphs").toString()),mr->Double.parseDouble(mr.getBlue().getScore("glyphs").toString()));
		fillGeneralTeamStats(stats, "glyphAvg", calculateAverage(A,B,plays));
		fillGeneralTeamStats(stats, "glyphPR", calculatePR(A,B));
		
		populateBMatrix(B, results, mr->Double.parseDouble(mr.getRed().getScore("rows").toString()),mr->Double.parseDouble(mr.getBlue().getScore("rows").toString()));
		fillGeneralTeamStats(stats, "rowAvg", calculateAverage(A,B,plays));
		fillGeneralTeamStats(stats, "rowPR", calculatePR(A,B));
		
		populateBMatrix(B, results, mr->Double.parseDouble(mr.getRed().getScore("columns").toString()),mr->Double.parseDouble(mr.getBlue().getScore("columns").toString()));
		fillGeneralTeamStats(stats, "columnAvg", calculateAverage(A,B,plays));
		fillGeneralTeamStats(stats, "columnPR", calculatePR(A,B));
		
		populateBMatrix(B, results, mr->Double.parseDouble(mr.getRed().getScore("ciphers").toString()),mr->Double.parseDouble(mr.getBlue().getScore("ciphers").toString()));
		fillGeneralTeamStats(stats, "cipherAvg", calculateAverage(A,B,plays));
		fillGeneralTeamStats(stats, "cipherPR", calculatePR(A,B));
		
		populateBMatrix(B, results, mr->mr.getRed().getRelicPoints(), mr->mr.getBlue().getRelicPoints());
		fillGeneralTeamStats(stats, "relicAvg", calculateAverage(A,B,plays));
		fillGeneralTeamStats(stats, "relicPR", calculatePR(A,B));		
		return stats;
		
	}
	
	private double getScore(Alliance a, String f) {
		return Double.parseDouble(a.getScore(f).toString());
	}
	
	//This method can be used for both Quals & Elims
	private Map<String, EventStat> calculateEventStats(List<MatchResult> results) {
		Map<String, EventStat> stats = new HashMap<>();
		stats.put("score", new EventStat());
		stats.put("scoreNP", new EventStat());
		stats.put("margin", new EventStat());
		stats.put("combined", new EventStat());
		stats.put("combinedNP", new EventStat());
		stats.put("winningScore", new EventStat());
		stats.put("parked", new EventStat());
		stats.put("jewels", new EventStat());
		stats.put("keys", new EventStat());
		stats.put("glyphs", new EventStat());
		stats.put("rows", new EventStat());
		stats.put("columns", new EventStat());
		stats.put("ciphers", new EventStat());
		stats.put("frogs", new EventStat());
		stats.put("snakes", new EventStat());
		stats.put("birds", new EventStat());
		stats.put("relics", new EventStat());
		stats.put("standing", new EventStat());
		stats.put("balanced", new EventStat());
		
		for(MatchResult mr : results) {
			if(mr.getStatus() != 1)continue;
			
			stats.get("score").sample(mr.getRedTotal()).sample(mr.getBlueTotal());
			stats.get("scoreNP").sample(mr.getRedScore()).sample(mr.getBlueScore());
			stats.get("margin").sample(mr.getMargin());
			stats.get("combined").sample(mr.getRedTotal()+mr.getBlueTotal());
			stats.get("combinedNP").sample(mr.getRedScore()+mr.getBlueScore());
			stats.get("winningScore").sample(Math.max(mr.getBlueTotal(), mr.getRedTotal()));
		
			stats.get("parked").sample(getScore(mr.getRed(), "parkedAuto")+getScore(mr.getBlue(), "parkedAuto"), 4);
			stats.get("jewels").sample(getScore(mr.getRed(), "jewels")+getScore(mr.getBlue(), "jewels"), 4);
			stats.get("keys").sample(getScore(mr.getRed(), "cryptoboxKeys")+getScore(mr.getBlue(), "cryptoboxKeys"), 4);
			stats.get("glyphs").sample(getScore(mr.getRed(), "glyphs")+getScore(mr.getBlue(), "glyphs"), 48);
			stats.get("rows").sample(getScore(mr.getRed(), "rows")+getScore(mr.getBlue(), "rows"), 16);
			stats.get("columns").sample(getScore(mr.getRed(), "columns")+getScore(mr.getBlue(), "columns"), 12);
			stats.get("ciphers").sample(getScore(mr.getRed(), "ciphers")+getScore(mr.getBlue(), "ciphers"), 4);
			if(getScore(mr.getRed(), "ciphers") > 0) {
				int[] count = mr.getRed().getCipherCount();
				if(count[0] > 0) {
					stats.get("birds").sample(count[2], count[0]);
					stats.get("snakes").sample(count[3], count[0]);
					stats.get("frogs").sample(count[1], count[0]);					
				}				
			}
			if(getScore(mr.getBlue(), "ciphers") > 0) {
				int[] count = mr.getBlue().getCipherCount();
				if(count[0] > 0) {
					stats.get("birds").sample(count[2], count[0]);
					stats.get("snakes").sample(count[3], count[0]);
					stats.get("frogs").sample(count[1], count[0]);					
				}				
			}
			
		}
		
		return stats;
	}
	
	
	private void fillGeneralTeamStats(List<GeneralTeamStat> stats ,String key, RealMatrix PR) {
		for(int i = 0; i < stats.size(); i++) {
			stats.get(i).setStat(key, PR.getEntry(i, 0));
		}
	}


}

package nc.ftc.inspection.event;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.SingularValueDecomposition;

import nc.ftc.inspection.Update;
import nc.ftc.inspection.dao.EventDAO;
import nc.ftc.inspection.model.Alliance;
import nc.ftc.inspection.model.MatchResult;
import nc.ftc.inspection.model.Team;

public class StatsCalculator extends Thread{
	static class StatsCalculatorJob {
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
		}
	}
	
	private double[][][] createMatrices( List<MatchResult> results, List<Rank> rankings) {
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
		populateBMatrix(B, results, mr->mr.getRed().getRedJewels(), mr->mr.getBlue().getBlueJewels());
		fillGeneralTeamStats(stats, "jewelAvg", calculateAverage(A,B,plays));
		fillGeneralTeamStats(stats, "jewelPR", calculatePR(A,B));
		
		populateBMatrix(B, results, mr->mr.getRed().getRedJewels(), mr->0);
		fillGeneralTeamStats(stats, "jewelRedAvg", calculateAverage(A,B,redPlays));
		fillGeneralTeamStats(stats, "jewelRedPR", calculatePR(A,B));
		
		populateBMatrix(B, results, mr->0, mr->mr.getBlue().getBlueJewels());
		fillGeneralTeamStats(stats, "jewelBlueAvg", calculateAverage(A,B,bluePlays));
		fillGeneralTeamStats(stats, "jewelBluePR", calculatePR(A,B));		
		
		populateBMatrix(B, results, mr->{
			Alliance red = mr.getRed();
			return red.isRedLeft() ? red.getRedJewels() : 0;
		}, mr->{
			Alliance blue = mr.getBlue();
			return blue.isRedLeft() ? blue.getBlueJewels() : 0;
		});
		fillGeneralTeamStats(stats, "jewelLeftAvg", calculateAverage(A,B,jewelRLPlays));
		fillGeneralTeamStats(stats, "jewelLeftPR", calculatePR(A,B));	
		
		populateBMatrix(B, results, mr->{
			Alliance red = mr.getRed();
			return red.isRedLeft() ? 0 : red.getRedJewels();
		}, mr->{
			Alliance blue = mr.getBlue();
			return blue.isRedLeft() ?  0 : blue.getBlueJewels();
		});
		fillGeneralTeamStats(stats, "jewelRightAvg", calculateAverage(A,B,jewelRRPlays));
		fillGeneralTeamStats(stats, "jewelRightPR", calculatePR(A,B));
		
		populateBMatrix(B, results, mr->Double.parseDouble(mr.getRed().getScore("cryptoboxKeys").toString()), mr->Double.parseDouble(mr.getBlue().getScore("cryptoboxKeys").toString()));
		fillGeneralTeamStats(stats, "keyAvg", calculateAverage(A,B,plays));
		fillGeneralTeamStats(stats, "keyPR", calculatePR(A,B));
		
		populateBMatrix(B, results, mr->mr.getRed().isKeyLeft() ? Double.parseDouble(mr.getRed().getScore("cryptoboxKeys").toString()) : 0, mr->mr.getBlue().isKeyLeft() ? Double.parseDouble(mr.getBlue().getScore("cryptoboxKeys").toString()) : 0);
		fillGeneralTeamStats(stats, "keyLeftAvg", calculateAverage(A,B,leftPlays));
		fillGeneralTeamStats(stats, "keyLeftPR", calculatePR(A,B));
		
		populateBMatrix(B, results, mr->mr.getRed().isKeyCenter() ? Double.parseDouble(mr.getRed().getScore("cryptoboxKeys").toString()) : 0, mr->mr.getBlue().isKeyCenter() ? Double.parseDouble(mr.getBlue().getScore("cryptoboxKeys").toString()) : 0);
		fillGeneralTeamStats(stats, "keyCenterAvg", calculateAverage(A,B,centerPlays));
		fillGeneralTeamStats(stats, "keyCenterPR", calculatePR(A,B));
		
		populateBMatrix(B, results, mr->mr.getRed().isKeyRight() ? Double.parseDouble(mr.getRed().getScore("cryptoboxKeys").toString()) : 0, mr->mr.getBlue().isKeyRight() ? Double.parseDouble(mr.getBlue().getScore("cryptoboxKeys").toString()) : 0);
		fillGeneralTeamStats(stats, "keyRightAvg", calculateAverage(A,B,rightPlays));
		fillGeneralTeamStats(stats, "keyRightPR", calculatePR(A,B));
		
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
	
	
	private void fillGeneralTeamStats(List<GeneralTeamStat> stats ,String key, RealMatrix PR) {
		for(int i = 0; i < stats.size(); i++) {
			stats.get(i).setStat(key, PR.getEntry(i, 0));
		}
	}


}

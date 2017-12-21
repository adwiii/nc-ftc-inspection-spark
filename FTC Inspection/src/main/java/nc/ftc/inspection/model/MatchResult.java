package nc.ftc.inspection.model;

public class MatchResult {
	Alliance red;
	Alliance blue;
	int redScore;
	int blueScore;
	int redPenalty;
	int bluePenalty;
	char winChar;
	String winColor;
	int number;
	
	String RED = "#FF4444";
	String BLUE = "#44AAFF";
	int status;
	
	public MatchResult(int n, Alliance r, Alliance b, int rs, int bs, int status, int redP, int blueP) {
		red = r;
		blue = b;
		number = n;
		redScore = rs;
		blueScore = bs;
		if(redScore > blueScore) {
			winColor = RED;
			winChar = 'R';
		}
		else if(blueScore > redScore) {
			winColor = BLUE;
			winChar = 'R';
		} else {
			winChar = 'T';
			winColor = number % 2 == 1 ? "#FFFFFF" : "#CCCCCC";
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
	public char getWinChar() {
		return winChar;
	}
	public String getWinColor() {
		return winColor;
	}
	public int getStatus() {
		return status;
	}
}

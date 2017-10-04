package nc.ftc.inspection.model;

public class Match {
	int number;
	Alliance red;
	Alliance blue;
	public Match(int num, Alliance red, Alliance blue){
		this.red = red;
		this.blue = blue;
		this.number = num;
	}
	public int getNumber(){
		return number;
	}
	public Alliance getRed(){
		return red;
	}
	public Alliance getBlue(){
		return blue;
	}

}

package nc.ftc.inspection.model;

public class Team {
	private int number;
	private String name;	
	//TODO if need to keep status in RAM, add a byte[] and final ints HW, SW, etc for index
	
	public Team(int number, String name){
		this.number = number;
		this.name = name;
	}
	
	public String getName(){
		return name;
	}
	public int getNumber(){
		return number;
	}
	public String toString(){
		return "{\"number\":"+number+", \"name\":\""+name+"\"}";
	}

}

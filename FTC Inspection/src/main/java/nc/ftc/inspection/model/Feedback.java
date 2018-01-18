package nc.ftc.inspection.model;

public class Feedback {
	String comment;
	long time;
	public Feedback(String c, long t) {
		comment = c;
		time = t;
	}
	public String getComment() {
		return comment;
	}
	public long getTime() {
		return time;
	}
}

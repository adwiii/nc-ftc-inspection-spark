package nc.ftc.inspection;

public class Key {
	String event;
	boolean verified;
	boolean created;
	public Key(String e, boolean v, boolean c) {
		event = e;
		verified = v;
		created = c;
	}
	public String getEvent() {
		return event;
	}
	public boolean isVerified() {
		return verified;
	}
	public boolean isCreated() {
		return created;
	}
	
}

package nc.ftc.inspection.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

public class User {
	
	public static int SYSADMIN = 1<<31;
	public static int ADMIN = 1<<30;
	public static int KEY_VOLUNTEER = 1<<29;
	public static int HEAD_REF = 1<<28;
	public static int REF = 1<<27;
	public static int LI = 1<<26;
	public static int INSPECTOR = 1<<25;
	public static int VOLUNTEER = 1<<2;
	public static int TEAM = 1<<1;
	public static int GENERAL = 1<<0;//we can change this to be something more useful
	
	public static HashMap<Integer, String> nameMap = new HashMap<>();
	public static HashMap<String, Integer> valMap = new HashMap<>();
	static {
		nameMap.put(SYSADMIN, "System Admin");
		nameMap.put(ADMIN, "Admin");
		nameMap.put(KEY_VOLUNTEER, "Key Volunteer");
		nameMap.put(HEAD_REF, "Head Referee");
		nameMap.put(REF, "Referee");
		nameMap.put(LI, "Lead Inspector");
		nameMap.put(INSPECTOR, "Inspector");
		nameMap.put(VOLUNTEER, "Volunteer");
		nameMap.put(TEAM, "Team Member");
		nameMap.put(GENERAL, "General User");
		Iterator<Entry<Integer, String>> it = nameMap.entrySet().iterator();
		while(it.hasNext()) {
			Entry<Integer, String> entry = it.next();
			valMap.put(entry.getValue(), entry.getKey());
		}
	}
	
	public static int NONE = 0; //this is for if you are not logged in
	
	
	private String username;
	private String realName;
	private String hashedPw;
	private String salt;
	private int type;
	private boolean changedPw;
	
	public boolean hasChangedPw() {
		return changedPw;
	}

	public void setChangedPw(boolean changedPw) {
		this.changedPw = changedPw;
	}

	public User(String username, String hashedPw, String salt, int type, String rn, boolean changed){
		this.username = username;
		this.hashedPw = hashedPw;
		this.salt = salt;
		this.type = type;
		this.realName = rn;
		this.changedPw = changed;
	}
	
	public String getUsername() {
		return username;
	}
	public void setUsername(String username) {
		this.username = username;
	}
	public String getHashedPw() {
		return hashedPw;
	}
	public void setHashedPw(String hashedPw) {
		this.hashedPw = hashedPw;
	}
	public String getSalt() {
		return salt;
	}
	public void setSalt(String salt) {
		this.salt = salt;
	}
	public int getType() {
		return type;
	}
	public void setType(int type) {
		this.type = type;
	}
	public String getRealName() {
		return realName;
	}
	public void setRealName(String realName) {
		this.realName = realName;
	}
	
	public List<String> getPermissionsList() {
		List<String> list = new ArrayList<String>();
		for (int i = 0; i < 32; i++) {
			if (this.is(1<<i) && nameMap.containsKey(1<<i)) {
				list.add(nameMap.get(1<<i));
			}
		}
		return list;
	}
	/**
	 * This checks to see if this users type is at least the type given.
	 * @param type The type to check against
	 * @return if this user is at least the type given
	 */
	public boolean is(int type) {
		//the check for this.type == type is to allow for 0 == 0 for general
		return (this.type & type) != 0 || this.type == type;
	}

	public int getPermissions() {
		return type;
	}
	

}

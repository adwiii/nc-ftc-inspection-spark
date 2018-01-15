package nc.ftc.inspection;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import nc.ftc.inspection.model.User;
import spark.Request;

public class AuthenticationManager {
	static long DEFAULT_SESSION_LENGTH = 100 * 60 * 60 * 24; //24hr default session length
	static Map<String, Session> sessions = new HashMap<>();
	static Map<String, Session> sessionsUserMap = new HashMap<>();

	public static boolean isLoggedIn(String sessionToken) {
		if (sessionToken != null) {
			Session session = sessions.get(sessionToken);
			if (System.currentTimeMillis() < session.getExpirationTime() || session.getExpirationTime() == -1) {
				return true;
			} else {
				sessions.remove(sessionToken);
			}
		}
		return false;
	}

	public static String getNewSession(User user, long expirationTime) {
		while (true) {
			//in theory this is universally unique, so we shouldn't even need to check if it contains
			String token = UUID.randomUUID().toString(); 
			if (!sessions.containsKey(token)) {
				Session session = new Session(user, expirationTime);
				sessions.put(token, session);
				sessionsUserMap.put(user.getUsername(), session);
				return token;
			}
		}
	}
	
	public static String getNewSession(User user) {
		return getNewSession(user, System.currentTimeMillis() + DEFAULT_SESSION_LENGTH);
	}

	public static class Session {
		private User user;
		private long expirationTime;

		public Session(User user, long expirationTime) {
			this.user = user;
			this.expirationTime = expirationTime;
		}

		
		public User getUser() {
			return user;
		}
		
		public long getExpirationTime() {
			return expirationTime;
		}
	}

	public static int getUserType(String sessionToken) {
		Session session = sessions.get(sessionToken);
		if (session != null) {
			return session.getUser().getType();
		}
		return User.NONE;
	}
	
	public static void addUserPermission(String userString, int permission) {
		Session session = sessionsUserMap.get(userString);
		if(session == null) {
			return;
		}
		User user = session.getUser();
		user.addPermission(permission);
	}
	
	public static void removeUserPermission(String userString, int permission) {
		Session session = sessionsUserMap.get(userString);
		if(session == null) {
			return;
		}
		User user = session.getUser();
		user.removePermission(permission);
	}

	public static int getCurrentType(Request request) {
		return getUserType(request.queryParams("sessionToken"));
	}

	public static User getCurrentUser(Request request) {
		Session session = sessions.get(request.queryParams("sessionToken"));
		if (session != null) {
			return session.getUser();
		}
		return null;
	}
}

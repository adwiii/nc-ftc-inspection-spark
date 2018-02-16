/*
 * Copyright (c) 2016-2018 Thomas Barnette and Trey Woodlief
 * All Rights Reserved.
 */

package nc.ftc.inspection;

public class Cache <T>{
	private T value = null;
	private long ts;
	private long lifetime;
	
	public Cache(long lifetime){
		this.lifetime = lifetime;
	}
	public Cache() {
		this(20 * 60000);
	}
	/**
	 * Returns the cached object, 
	 * @return
	 */
	public synchronized T get() {
		if(System.currentTimeMillis() - ts > lifetime) {
			value = null;
		}
		return value;
	}
	public synchronized void set(T t) {
		value = t;
		ts = System.currentTimeMillis();
	}
	public synchronized void invalidate() {
		set(null);
	}
}

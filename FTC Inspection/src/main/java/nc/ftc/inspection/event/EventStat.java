/*
 * Copyright (c) 2016-2018 Thomas Barnette and Trey Woodlief
 * All Rights Reserved.
 */

package nc.ftc.inspection.event;

import java.text.DecimalFormat;

public class EventStat {
	double min = Double.MAX_VALUE;
	double max = Double.MIN_VALUE;
	double sum = 0;
	double n;
	double opportunities;
	static DecimalFormat df = new DecimalFormat("0.00"); 
	public EventStat sample(double sample) {
		min = Math.min(min, sample);
		max = Math.max(max,  sample);
		sum += sample;
		n++;
		return this;
	}
	public EventStat sample(double sample, double opps) {
		sample(sample);
		opportunities+=opps;
		return this;
	}
	private double getAverage() {
		return sum / n;
	}
	public String getPct() {
		return df.format(sum / opportunities * 100);
	}
	public String getMax() {
		return df.format(max);
	}
	public String getMin() {
		return df.format(min);
	}
	public String getAvg() {
		return df.format(getAverage());
	}
	public String getOpp() {
		return df.format(opportunities);
	}
	public String getSum() {
		return df.format(sum);
	}
}

package ee.ioc.cs.vsle.util;

/*-
 * #%L
 * CoCoViLa
 * %%
 * Copyright (C) 2003 - 2017 Institute of Cybernetics at Tallinn University of Technology
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import java.lang.management.*;
import java.util.*;

import javax.management.*;

import ee.ioc.cs.vsle.editor.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MemoryWarningSystem {

	private static final Logger logger = LoggerFactory.getLogger(MemoryWarningSystem.class);

	private final static MemoryWarningSystem s_instance = new MemoryWarningSystem();
	
	public static MemoryWarningSystem getInstance() {
		return s_instance;
	}
	
	private final Collection<Listener> listeners =
		new ArrayList<Listener>();

	private MemoryWarningSystem() {
//	    if ( RuntimeProperties.isLogInfoEnabled() ) 
//	        db.p( "Starting Memory Warning System" );
		MemoryMXBean mbean = ManagementFactory.getMemoryMXBean();
		NotificationEmitter emitter = (NotificationEmitter) mbean;
		emitter.addNotificationListener(new NotificationListener() {
			public void handleNotification(Notification n, Object hb) {
				if (n.getType().equals(
						MemoryNotificationInfo.MEMORY_THRESHOLD_EXCEEDED)) {
					long maxMemory = tenuredGenPool.getUsage().getMax();
					long usedMemory = tenuredGenPool.getUsage().getUsed();
					logger.info( "usedMemory: " + usedMemory + " maxMemory: " + maxMemory );
					for (Listener listener : listeners) {
						listener.memoryUsageLow(usedMemory, maxMemory);
					}
				}
			}
		}, null, null);
	}

	public boolean addListener(Listener listener) {
		return listeners.add(listener);
	}

	public boolean removeListener(Listener listener) {
		return listeners.remove(listener);
	}

	private static final MemoryPoolMXBean tenuredGenPool =
		findTenuredGenPool();

	public static void setPercentageUsageThreshold(double percentage) {
		if (percentage <= 0.0 || percentage > 1.0) {
			throw new IllegalArgumentException("Percentage not in range");
		}
		long maxMemory = tenuredGenPool.getUsage().getMax();
		long warningThreshold = (long) (maxMemory * percentage);
//		if ( RuntimeProperties.isLogInfoEnabled() ) 
//            db.p( "percentage: " + percentage + " warningThreshold: " + warningThreshold + " maxMemory: " + maxMemory );
		tenuredGenPool.setUsageThreshold(warningThreshold);
	}

	/**
	 * Tenured Space Pool can be determined by it being of type
	 * HEAP and by it being possible to set the usage threshold.
	 */
	private static MemoryPoolMXBean findTenuredGenPool() {
		for (MemoryPoolMXBean pool :
			ManagementFactory.getMemoryPoolMXBeans()) {
			// I don't know whether this approach is better, or whether
			// we should rather check for the pool name "Tenured Gen"?
			if (pool.getType() == MemoryType.HEAP &&
					pool.isUsageThresholdSupported()) {
				return pool;
			}
		}
		throw new AssertionError("Could not find tenured space");
	}

	public interface Listener {
		public void memoryUsageLow(long usedMemory, long maxMemory);
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
		MemoryWarningSystem.setPercentageUsageThreshold(0.6);

		MemoryWarningSystem mws = new MemoryWarningSystem();
		mws.addListener(new MemoryWarningSystem.Listener() {
			double per = 0.6;
			public void memoryUsageLow(long usedMemory, long maxMemory) {
				System.out.println("Memory usage low!!!");
				double percentageUsed = ((double) usedMemory) / maxMemory;
				System.out.println("percentageUsed = " + percentageUsed);
				per += 0.1;
				MemoryWarningSystem.setPercentageUsageThreshold(per);
			}
		});

		Collection<Double> numbers = new LinkedList<Double>();
		while (true) {
			numbers.add(Math.random());
		}
	}

}

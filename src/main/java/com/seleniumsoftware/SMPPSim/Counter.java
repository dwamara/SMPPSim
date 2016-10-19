package com.seleniumsoftware.SMPPSim;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.TimerTask;

/**
 * @author hzhang
 */
class Counter extends TimerTask {
	public static int counter = 0;
	private Logger logger = LoggerFactory.getLogger(this.getClass().getSimpleName());

	@Override
	public void run() {
		logger.info("COUNTER: {}", counter);
	}
}


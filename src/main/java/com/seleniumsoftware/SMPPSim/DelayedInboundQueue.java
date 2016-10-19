/****************************************************************************
 * DelayedInboundQueue.java
 *
 * Copyright (C) Martin Woolley 2001
 *
 * This file is part of SMPPSim.
 *
 * SMPPSim is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * SMPPSim is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMPPSim; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 * @author martin@seleniumsoftware.com
 * http://www.woolleynet.com
 * http://www.seleniumsoftware.com
 * $Header: /var/cvsroot/SMPPSim2/distribution/2.6.9/SMPPSim/src/java/com/seleniumsoftware/SMPPSim/DelayedInboundQueue.java,v 1.1 2012/07/24 14:48:59 martin Exp $
 ****************************************************************************
 */
package com.seleniumsoftware.SMPPSim;

import com.seleniumsoftware.SMPPSim.exceptions.InboundQueueFullException;
import com.seleniumsoftware.SMPPSim.pdu.Pdu;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

public class DelayedInboundQueue implements Runnable {

	private static DelayedInboundQueue diqueue;

//	private static Logger logger = Logger
//			.getLogger("com.seleniumsoftware.smppsim");

	private static Logger logger = LoggerFactory.getLogger(DelayedInboundQueue.class);
	ArrayList<Pdu> delayed_queue_pdus;
	ArrayList<Integer> delayed_queue_attempts;
	private Smsc smsc = Smsc.getInstance();
	private InboundQueue iqueue = InboundQueue.getInstance();
	private int period;

	private int max_attempts;

	private DelayedInboundQueue() {
		period = SMPPSim.getDelayed_iqueue_period();
		max_attempts = SMPPSim.getDelayed_inbound_queue_max_attempts();
		delayed_queue_attempts = new ArrayList<Integer>();
		delayed_queue_pdus = new ArrayList<Pdu>();
	}

	public static DelayedInboundQueue getInstance() {
		if (diqueue == null) {
			diqueue = new DelayedInboundQueue();
		}
		return diqueue;
	}

	public void retryLater(Pdu pdu) {
		synchronized (delayed_queue_pdus) {
			synchronized (delayed_queue_attempts) {
				if (!delayed_queue_pdus.contains(pdu)) {
					logger.debug("DelayedInboundQueue: adding object to queue<"
							+ pdu.toString() + ">");
					delayed_queue_pdus.add(pdu);
					delayed_queue_attempts.add(new Integer(0));
				} else {
					int i = delayed_queue_pdus.indexOf(pdu);
					if (i > -1) {
						int a = delayed_queue_attempts.get(i).intValue();
						a++;
						delayed_queue_attempts.set(i, a);
						logger.debug("DelayedInboundQueue: incremented retry count to " + a + " for " + "<"
								+ pdu.toString() + ">");
					}
				}
				logger.info("DelayedInboundQueue: now contains "
						+ delayed_queue_pdus.size() + " object(s)");
			}
		}
	}

	public void deliveredOK(Pdu pdu) {
		int seqno = pdu.getSeq_no();
		synchronized (delayed_queue_pdus) {
			synchronized (delayed_queue_attempts) {
				logger.debug("DelayedInboundQueue: removing object from queue<"
						+ pdu.toString() + ">");
				int i = delayed_queue_pdus.indexOf(pdu);
				if (i > -1) {
					Pdu mo = delayed_queue_pdus.get(i);
					if (mo.getSeq_no() == seqno) {
						delayed_queue_pdus.remove(i);
						delayed_queue_attempts.remove(i);
						logger.debug("Removed delayed message because it was delivered OK or with permanent error. seqno=" + seqno);
					}
				}
				logger.info("DelayedInboundQueue: now contains "
						+ delayed_queue_pdus.size() + " object(s)");
			}
		}

	}

	public void run() {
		// this code periodically processes the contents of the delayed inbound queue, moving
		// messages that are old enough to the active inbound queue for attempted delivery.

		logger.info("Starting DelayedInboundQueue service....");


		while (true) {
			try {
				Thread.sleep(period);
			} catch (InterruptedException e) {
			}
			int dcount = delayed_queue_pdus.size();
			//logger.info("Processing " + dcount +  " messages in the delayed inbound queue");

			synchronized (delayed_queue_pdus) {
				synchronized (delayed_queue_attempts) {
					for (int i = 0; i < delayed_queue_pdus.size(); i++) {
						Pdu mo = delayed_queue_pdus.get(i);
						try {
							if (delayed_queue_attempts.get(i).intValue() < max_attempts) {
								iqueue.addMessage(mo);
								int attempts = delayed_queue_attempts.get(i)
										.intValue() + 1;
								delayed_queue_attempts.set(i, new Integer(
										attempts));
								logger.debug("Requesting retry delivery of message " + mo.getSeq_no());
							} else {
								logger.info("MO message not delivered after max (" + max_attempts + ") allowed attempts so deleting : " + delayed_queue_pdus.get(i).getSeq_no());
								delayed_queue_pdus.remove(i);
							}
						} catch (InboundQueueFullException e) {
							// try again next time around
						}
					}
				}
			}
		}
	}

}

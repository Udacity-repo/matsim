package playground.wrashid.parallelEventsHandler;

import java.util.LinkedList;

import org.matsim.events.Events;
import org.matsim.events.LinkLeaveEvent;
import org.matsim.events.handler.LinkLeaveEventHandler;
import org.matsim.testcases.MatsimTestCase;

import playground.wrashid.tryouts.performance.EventProcessing;

public class ParallelEventsTest extends MatsimTestCase {

	// tests, if the right number of events were received
	// for different number of threads and handlers and number of iterations
	// and different constructors
	public void testEventCount() {
		processEvents(new ParallelEvents(1), 100, 1, 1);
		processEvents(new ParallelEvents(2), 100, 10, 2);
		processEvents(new ParallelEvents(4), 100, 1, 10);
		processEvents(new ParallelEvents(2), 150000, 2, 1);
		processEvents(new ParallelEvents(2), 300000, 3, 1);
		processEvents(new ParallelEvents(1,100), 100, 1, 1);
		processEvents(new ParallelEvents(1,100), 1000, 1, 1);
		processEvents(new ParallelEvents(1,1000), 100, 1, 1);
		processEvents(new ParallelEvents(2,100), 100, 1, 1);
		processEvents(new ParallelEvents(2,100), 1000, 2, 1);
		processEvents(new ParallelEvents(2,1000), 1000, 2, 1);
		processEvents(new ParallelEvents(2,5000), 100, 3, 1);
	}
	
	// test, if adding and removing a handler works
	public void testAddAndRemoveHandler(){
		Events events=new ParallelEvents(2);
		
		Handler1 handler = new Handler1();
		events.addHandler(handler);
		events.removeHandler(handler);
		
		LinkLeaveEvent linkLeaveEvent = new LinkLeaveEvent(0, "", "", 0);
		
		for (int i = 0; i < 100; i++) {
			events.processEvent(linkLeaveEvent);
		}
		
		if (events instanceof ParallelEvents) {
			((ParallelEvents) events).awaitHandlerThreads();
		}
		
		assertEquals(0, handler.getNumberOfProcessedMessages());		
	}
	

	public void processEvents(Events events, int eventCount,
			int numberOfHandlers, int numberOfIterations ) {

		Handler1[] handlers = new Handler1[numberOfHandlers];

		for (int i = 0; i < numberOfHandlers; i++) {
			handlers[i] = new Handler1();
			events.addHandler(handlers[i]);
		}

		LinkLeaveEvent linkLeaveEvent = new LinkLeaveEvent(0, "", "", 0);

		for (int j = 0; j < numberOfIterations; j++) {

			for (int i = 0; i < eventCount; i++) {
				events.processEvent(linkLeaveEvent);
			}

			// wait on all event handler threads
			// very important for the functionality of parallelEvents class
			if (events instanceof ParallelEvents) {
				((ParallelEvents) events).awaitHandlerThreads();
			}

			for (int i = 0; i < numberOfHandlers; i++) {
				assertEquals(eventCount, handlers[i]
						.getNumberOfProcessedMessages());
				handlers[i].resetNumberOfProcessedMessages();
			}

		}

	}

	private class Handler1 implements LinkLeaveEventHandler {

		private int numberOfProcessedMessages = 0;

		public int getNumberOfProcessedMessages() {
			return numberOfProcessedMessages;
		}

		public void resetNumberOfProcessedMessages() {
			numberOfProcessedMessages = 0;
		}

		public void handleEvent(LinkLeaveEvent event) {
			numberOfProcessedMessages++;
		}

		public void reset(int iteration) {
			// TODO Auto-generated method stub

		}

		public Handler1() {

		}

	}

}

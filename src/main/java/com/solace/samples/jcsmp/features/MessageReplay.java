package com.solace.samples.jcsmp.features;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;

import com.solace.samples.jcsmp.features.common.ArgParser;
import com.solace.samples.jcsmp.features.common.SampleApp;
import com.solace.samples.jcsmp.features.common.SampleUtils;
import com.solace.samples.jcsmp.features.common.SessionConfiguration;
import com.solacesystems.jcsmp.Browser;
import com.solacesystems.jcsmp.BrowserProperties;
import com.solacesystems.jcsmp.BytesXMLMessage;
import com.solacesystems.jcsmp.CapabilityType;
import com.solacesystems.jcsmp.ConsumerFlowProperties;
import com.solacesystems.jcsmp.FlowEvent;
import com.solacesystems.jcsmp.FlowEventArgs;
import com.solacesystems.jcsmp.FlowEventHandler;
import com.solacesystems.jcsmp.FlowReceiver;
import com.solacesystems.jcsmp.JCSMPErrorResponseException;
import com.solacesystems.jcsmp.JCSMPErrorResponseSubcodeEx;
import com.solacesystems.jcsmp.JCSMPException;
import com.solacesystems.jcsmp.JCSMPFactory;
import com.solacesystems.jcsmp.Queue;
import com.solacesystems.jcsmp.ReplayStartLocation;
import com.solacesystems.jcsmp.XMLMessageListener;

public class MessageReplay extends SampleApp implements XMLMessageListener {
	class ReplayFlowEventHandler implements FlowEventHandler {
		String flowName = null;
		ReplayFlowEventHandler(String name) {
			flowName = name;
		}
		@Override
		public void handleEvent(Object source, FlowEventArgs event) {
			System.out.println("Flow " + flowName + " (" + source + ") received flow event: " + event);	
			if (event.getEvent() == FlowEvent.FLOW_DOWN) {
				if (event.getException() instanceof JCSMPErrorResponseException) {
					JCSMPErrorResponseException ex = (JCSMPErrorResponseException) event.getException();
					switch (ex.getSubcodeEx()) {
						case JCSMPErrorResponseSubcodeEx.REPLAY_STARTED:
						case JCSMPErrorResponseSubcodeEx.REPLAY_FAILED:
						case JCSMPErrorResponseSubcodeEx.REPLAY_CANCELLED:
						case JCSMPErrorResponseSubcodeEx.REPLAY_LOG_MODIFIED:
						case JCSMPErrorResponseSubcodeEx.REPLAY_START_TIME_NOT_AVAILABLE:
						case JCSMPErrorResponseSubcodeEx.REPLAY_MESSAGE_UNAVAILABLE:
						case JCSMPErrorResponseSubcodeEx.REPLAYED_MESSAGE_REJECTED:
						case JCSMPErrorResponseSubcodeEx.REPLAY_START_MESSAGE_UNAVAILABLE:
							break;
						default:
							break;
					}
				}
			}
		}
	}

	SessionConfiguration conf = null;
	private int msgCount = 0;
	private ReplayFlowEventHandler consumerEventHandler = null;
	private ReplayFlowEventHandler browserEventHandler = null;
	
	public MessageReplay() {
		consumerEventHandler = new ReplayFlowEventHandler("consumer");
		browserEventHandler = new ReplayFlowEventHandler("browser");
	}
	
	void createSession(String[] args) {
		ArgParser parser = new ArgParser();

		// Parse command-line arguments
		if (parser.parse(args) == 0)
			conf = parser.getConfig();
		else
			printUsage(parser.isSecure());

		session = SampleUtils.newSession(conf, new PrintingSessionEventHandler(),null);
	}
	
	void printUsage(boolean secure) {
		String strusage = ArgParser.getCommonUsage(secure);
		strusage += "This sample:\n";
		strusage += "\t[-q queue]\t queue or topic endpoint. \n";
		strusage += "\t[-d date]\t date string in \"yyyy-MM-dd'T'HH:mm:ss\" format (e.g. \"2018-06-15T01:37:56\"). It specifies replay start date and time in UTC time zone. The default is to start fron the beginning.\n";
		System.out.println(strusage);
		finish(1);
	}

	public static void main(String[] args) {
		MessageReplay qsample = new MessageReplay();
		qsample.run(args);
	}

	void checkCapability(final CapabilityType cap) {
		System.out.printf("Checking for capability %s...", cap);
		if (session.isCapable(cap)) {
			System.out.println("OK");
		} else {
			System.out.println("FAILED");
			finish(1);
		}
	}

	void run(String[] args) {
		createSession(args);
		String queueName = "q";
		ConsumerFlowProperties consumerProps = new ConsumerFlowProperties();
		ReplayStartLocation loc = null;
		FlowReceiver consumer = null;
		String dateStr = null;
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
		Map<String, String> map = conf.getArgBag();
		if (map !=null) {
			dateStr = map.get("-d");
			if (map.containsKey("-q"))
			queueName = map.get("-q");
		}
		Queue queue = JCSMPFactory.onlyInstance().createQueue(queueName);
		consumerProps.setEndpoint(queue);
		simpleDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));   // This line converts the given date into UTC time zone
		BrowserProperties brosweProps = new BrowserProperties();
		brosweProps.setEndpoint(queue);
		brosweProps.setTransportWindowSize(1);
		brosweProps.setWaitTimeout(1000);
		Browser browser = null;
		
		try {
			// Connects the Session.
			session.connect();

			// Check REPLAY capability
			checkCapability(CapabilityType.MESSAGE_REPLAY);
			
			/*
			 * Create a browser
			 */			
			browser = session.createBrowser(brosweProps, browserEventHandler);
			System.out.println("Browser created.");	

			if (dateStr != null) {
				Date date = simpleDateFormat.parse(dateStr);
				loc = JCSMPFactory.onlyInstance().createReplayStartLocationDate(date);
			}
			else {
				loc = JCSMPFactory.onlyInstance().createReplayStartLocationBeginning();
			}
	        consumerProps.setReplayStartLocation(loc);
	        consumerProps.setActiveFlowIndication(false);
			/*
			 * Create and start a consumer flow
			 */
			consumer = session.createFlow(
					this, 
					consumerProps,
					null,
					consumerEventHandler);
			consumer.start();
			System.out.println("Flow (" + consumer + ") created");
			
			BytesXMLMessage msg = null;
			int count = 0;
			do {
				msg = browser.getNext(5000);
				if (msg != null) {
					count++;
					System.out.println("Got message (" + count + "): "+ msg.toString());
				}
				else {
					break;
				}
			} while (true);
			
			// Close the flow and browser
			System.out.println("Close flow and browser");
			consumer.close();
			browser.close();
			System.out.println("OK");

			finish(0);
		} catch (JCSMPException ex) {
			System.err.println("Encountered a JCSMPException, closing session... " + ex.getMessage());
			if (consumer != null) {
				consumer.close();
			}
			if (browser != null) {
				browser.close();
			}
			finish(1);
		} catch (Exception ex) {
			System.err.println("Encountered an Exception... " + ex.getMessage());
			finish(1);
		}

	}

	@Override
	public void onReceive(BytesXMLMessage message) {
		msgCount++;
		System.out.println("Received Message (" +msgCount +"): "  + message.toString());
	}

	@Override
	public void onException(JCSMPException exception) {
		 exception.printStackTrace();
	}
}

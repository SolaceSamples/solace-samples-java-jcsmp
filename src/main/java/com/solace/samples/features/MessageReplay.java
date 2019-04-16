package com.solace.samples.features;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;

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
import com.solacesystems.jcsmp.JCSMPFlowTransportUnsolicitedUnbindException;
import com.solacesystems.jcsmp.Queue;
import com.solacesystems.jcsmp.ReplayStartLocation;
import com.solacesystems.jcsmp.XMLMessageListener;
import com.solace.samples.features.common.ArgParser;
import com.solace.samples.features.common.SampleApp;
import com.solace.samples.features.common.SampleUtils;
import com.solace.samples.features.common.SessionConfiguration;

public class MessageReplay extends SampleApp implements XMLMessageListener {

    private volatile int replayErrorResponseSubcode = JCSMPErrorResponseSubcodeEx.UNKNOWN;
    class ReplayFlowEventHandler implements FlowEventHandler {
        @Override
        public void handleEvent(Object source, FlowEventArgs event) {
            System.out.println("Consumer received flow event: " + event);
            if (event.getEvent() == FlowEvent.FLOW_DOWN) {
                if (event.getException() instanceof JCSMPErrorResponseException) {
                    JCSMPErrorResponseException ex = (JCSMPErrorResponseException) event.getException();
                    // Store the subcode for the exception handler
                    replayErrorResponseSubcode = ex.getSubcodeEx();
                    // Placeholder for additional event handling
                    // Do not manipulate the session from here
                    // onException() is the correct place for that
                    switch (replayErrorResponseSubcode) {
                        case JCSMPErrorResponseSubcodeEx.REPLAY_STARTED:
                        case JCSMPErrorResponseSubcodeEx.REPLAY_FAILED:
                        case JCSMPErrorResponseSubcodeEx.REPLAY_CANCELLED:
                        case JCSMPErrorResponseSubcodeEx.REPLAY_LOG_MODIFIED:
                        case JCSMPErrorResponseSubcodeEx.REPLAY_START_TIME_NOT_AVAILABLE:
                        case JCSMPErrorResponseSubcodeEx.REPLAY_MESSAGE_UNAVAILABLE:
                        case JCSMPErrorResponseSubcodeEx.REPLAYED_MESSAGE_REJECTED:
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
    ConsumerFlowProperties consumerFlowProps = new ConsumerFlowProperties();
    FlowReceiver consumer = null;

    public MessageReplay() {
        consumerEventHandler = new ReplayFlowEventHandler();
    }

    void createSession(String[] args) {
        ArgParser parser = new ArgParser();

        // Parse command-line arguments
        if (parser.parse(args) == 0)
            conf = parser.getConfig();
        else
            printUsage(parser.isSecure());

        session = SampleUtils.newSession(conf, new PrintingSessionEventHandler(), null);
    }

    void printUsage(boolean secure) {
        String strusage = ArgParser.getCommonUsage(secure);
        strusage += "This sample:\n";
        strusage += "\t[-q queue]\t queue ot topic endpoint. \n";
        strusage += "\t[-d date]\t date string in \"yyyy-MM-dd'T'HH:mm:ss\" format (e.g. \"2018-06-15T01:37:56\"). It specifies replay start date and time in UTC time zone.\n";
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
            System.out.println("FAILED- exiting.");
            finish(1);
        }
    }

    void run(String[] args) {
        createSession(args);
        String queueName = "q";
        String dateStr = null;
        Map<String, String> map = conf.getArgBag();
        if (map != null) {
            dateStr = map.get("-d");
            if (map.containsKey("-q"))
                queueName = map.get("-q");
        }
        Queue queue = JCSMPFactory.onlyInstance().createQueue(queueName);
        consumerFlowProps.setEndpoint(queue);

        try {
            // Connects the Session.
            session.connect();

            // Check REPLAY capability
            checkCapability(CapabilityType.MESSAGE_REPLAY);

            ReplayStartLocation loc = null;
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
            simpleDateFormat.setTimeZone(TimeZone.getTimeZone("UTC")); // This line converts the given date into UTC time zone
            if (dateStr != null) {
                Date date = simpleDateFormat.parse(dateStr);
                loc = JCSMPFactory.onlyInstance().createReplayStartLocationDate(date);
            } else {
                loc = JCSMPFactory.onlyInstance().createReplayStartLocationBeginning();
            }
            consumerFlowProps.setReplayStartLocation(loc);
            /*
             * Create and start a consumer flow
             */
            consumer = session.createFlow(this, consumerFlowProps, null, consumerEventHandler);
            consumer.start();
            System.out.println("Flow (" + consumer + ") created");

            Thread.sleep(600000);       // Idling for 10 minutes
            // Close the flow
            System.out.println("Closing the flow and exiting the application.");
            consumer.close();
            finish(0);
        } catch (JCSMPException ex) {
            System.err.println("Encountered a JCSMPException, closing session... " + ex.getMessage());
            if (consumer != null) {
                consumer.close();
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
        System.out.println("Received Message (" + msgCount + "): " + message.toString());
    }

    @Override
    public void onException(JCSMPException exception) {
        if (exception instanceof JCSMPFlowTransportUnsolicitedUnbindException) {
            try {
                switch (replayErrorResponseSubcode) {
                    case JCSMPErrorResponseSubcodeEx.REPLAY_STARTED:
                        System.out.println("Sample handling of an unsolicited unbind for replay initiated. Recreating the flow.");
                        if (consumerFlowProps.getReplayStartLocation() != null) {
                            consumerFlowProps.setReplayStartLocation(null);
                        }
                        consumer = session.createFlow(this, consumerFlowProps, null, consumerEventHandler);
                        consumer.start();
                        break;
                    case JCSMPErrorResponseSubcodeEx.REPLAY_START_TIME_NOT_AVAILABLE:
                        System.out.println("Start date was before the log creation date, initiating replay for all messages instead.");
                        consumerFlowProps.setReplayStartLocation(JCSMPFactory.onlyInstance().createReplayStartLocationBeginning());
                        consumer = session.createFlow(this, consumerFlowProps, null, consumerEventHandler);
                        consumer.start();
                        break;
                    default:
                        break;
                }
                replayErrorResponseSubcode = JCSMPErrorResponseSubcodeEx.UNKNOWN; // reset after handling
            }
            catch (JCSMPException e) {
                e.printStackTrace();
            }
        } else {
            exception.printStackTrace();
        }
    }
}

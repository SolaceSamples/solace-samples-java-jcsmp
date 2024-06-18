package com.solace.samples.jcsmp.snippets;

import com.solacesystems.jcsmp.JCSMPProperties;
import com.solacesystems.jcsmp.JCSMPSession;
import com.solacesystems.jcsmp.JCSMPException;
import com.solacesystems.jcsmp.JCSMPFactory;

public class HowToEnablePayloadCompression {

    /**
     * This method demonstrates a snippet of how to enable payload compression on a session.
     */
    public void enablePayloadCompression() throws JCSMPException {

        final JCSMPProperties properties = new JCSMPProperties();

        // enable payload compression with a compression level of 9 (max compression)
        properties.setProperty(JCSMPProperties.PAYLOAD_COMPRESSION_LEVEL, 9);

        JCSMPSession session = JCSMPFactory.onlyInstance().createSession(properties);

        session.connect();

        // Now that payload compression is enabled on the session, any message
        // published on the session with a non-empty binary attachment will be
        // automatically compressed. Any receiver that supports payload compression
        // will automatically decompress the message if it is compressed.
    }

}
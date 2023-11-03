/**
 * SecureSession.java
 * 
 * This sample demonstrates how to setup a secure session using SSL.
 * 
 * A server certificate needs to be installed on the appliance and SSL must be
 * enabled on the appliance for this sample to work.
 * Also, in order to connect to the appliance with Certificate Validation enabled
 * (which is enabled by default), the appliance's certificate chain must be signed
 * by one of the root CAs in the trust store used by the sample.
 *
 * For this sample to use CLIENT CERTIFICATE authentication, a trust store has to
 * be set up on the appliance and it must contain the root CA that signed the client
 * certificate. The VPN must also have client-certificate authentication enabled.
 * 
 * Copyright 2009-2022 Solace Corporation. All rights reserved.
 */

package com.solace.samples.jcsmp.features;

import java.io.FileOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

import com.solace.samples.jcsmp.features.common.ArgParser;
import com.solace.samples.jcsmp.features.common.SampleApp;
import com.solace.samples.jcsmp.features.common.SecureSessionConfiguration;
import com.solace.samples.jcsmp.features.common.SessionConfiguration.AuthenticationScheme;
import com.solacesystems.jcsmp.BytesXMLMessage;
import com.solacesystems.jcsmp.DeliveryMode;
import com.solacesystems.jcsmp.FlowReceiver;
import com.solacesystems.jcsmp.InvalidPropertiesException;
import com.solacesystems.jcsmp.JCSMPChannelProperties;
import com.solacesystems.jcsmp.JCSMPException;
import com.solacesystems.jcsmp.JCSMPFactory;
import com.solacesystems.jcsmp.JCSMPProperties;
import com.solacesystems.jcsmp.JCSMPStreamingPublishCorrelatingEventHandler;
import com.solacesystems.jcsmp.JCSMPTransportException;
import com.solacesystems.jcsmp.Queue;
import com.solacesystems.jcsmp.XMLMessageListener;
import com.solacesystems.jcsmp.XMLMessageProducer;

public class SecureSession extends SampleApp implements XMLMessageListener, JCSMPStreamingPublishCorrelatingEventHandler {
    SecureSessionConfiguration conf = null;

    // XMLMessageListener
    public void onException(JCSMPException exception) {
        exception.printStackTrace();
    }

    // XMLMessageListener
    public void onReceive(BytesXMLMessage message) {
        System.out.println("Received Message:\n" + message.dump());
    }

    // JCSMPStreamingPublishEventHandler
    public void handleErrorEx(Object key, JCSMPException cause,
            long timestamp) {
        cause.printStackTrace();
    }

    // JCSMPStreamingPublishEventHandler
    public void responseReceivedEx(Object key) {        
    }

    void createSession(String[] args) throws InvalidPropertiesException {
        // Parse command-line arguments
        ArgParser parser = new ArgParser();
        if (parser.parseSecureSampleArgs(args) == 0)
            conf = (SecureSessionConfiguration)parser.getConfig();
        else
            printUsage();

        // the host must start with "tcps:"
        if (!conf.getHost().toLowerCase().startsWith("tcps:")) {
            System.err.println("Host must start with \"tcps:\"");
            printUsage();
        }
        JCSMPProperties properties = new JCSMPProperties();
        properties.setProperty(JCSMPProperties.HOST, conf.getHost());
        
        if (conf.getRouterUserVpn() != null) {
            if (!conf.getRouterUserVpn().get_user().trim().equals("")) {
                properties.setProperty(JCSMPProperties.USERNAME, conf.getRouterUserVpn().get_user());
            }
            if (conf.getRouterUserVpn().get_vpn() != null) {
                properties.setProperty(JCSMPProperties.VPN_NAME, conf.getRouterUserVpn().get_vpn());
            }
        }
        
        // Channel properties
        JCSMPChannelProperties cp = (JCSMPChannelProperties) properties
			.getProperty(JCSMPProperties.CLIENT_CHANNEL_PROPERTIES);
        if (conf.isCompression()) {
			// Compression is set as a number from 0-9 where 0 means "disable
			// compression" and 9 means max compression. The default is no
			// compression.
			// Selecting a non-zero compression level auto-selects the
			// compressed SMF port on the appliance, as long as no SMF port is
			// explicitly specified.
			cp.setCompressionLevel(9);        	
        }
        properties.setProperty(JCSMPProperties.PASSWORD, conf.getRouterPassword());

        // set the SSL properties
        if (conf.getExcludeProtocols() != null) {
            properties.setProperty(JCSMPProperties.SSL_EXCLUDED_PROTOCOLS, conf.getExcludeProtocols());
        }
        if (conf.getCiphers() != null) {
            properties.setProperty(JCSMPProperties.SSL_CIPHER_SUITES, conf.getCiphers());
        }
        if (conf.getTrustStore() != null) {
            properties.setProperty(JCSMPProperties.SSL_TRUST_STORE, conf.getTrustStore());
        }
        if (conf.getTrustStoreFmt() != null) {
            properties.setProperty(JCSMPProperties.SSL_TRUST_STORE_FORMAT, conf.getTrustStoreFmt());
        }
        if (conf.getTrustStorePwd() != null) {
            properties.setProperty(JCSMPProperties.SSL_TRUST_STORE_PASSWORD, conf.getTrustStorePwd());
        }
        if (conf.getKeyStore() != null) {
            properties.setProperty(JCSMPProperties.SSL_KEY_STORE, conf.getKeyStore());
        }
        if (conf.getKeyStoreFmt() != null) {
            properties.setProperty(JCSMPProperties.SSL_KEY_STORE_FORMAT, conf.getKeyStoreFmt());
        }
        if (conf.getKeyStoreNormalizedFmt() != null) {
            properties.setProperty(JCSMPProperties.SSL_KEY_STORE_NORMALIZED_FORMAT, conf.getKeyStoreNormalizedFmt());
        }
        if (conf.getKeyStorePwd() != null) {
            properties.setProperty(JCSMPProperties.SSL_KEY_STORE_PASSWORD, conf.getKeyStorePwd());
        }
        if (conf.getPrivateKeyAlias() != null) {
            properties.setProperty(JCSMPProperties.SSL_PRIVATE_KEY_ALIAS, conf.getPrivateKeyAlias());
        }
        if (conf.getPrivateKeyPwd() != null ) {
            properties.setProperty(JCSMPProperties.SSL_PRIVATE_KEY_PASSWORD, conf.getPrivateKeyPwd());
        }
        if (conf.getCommonNames() != null) {
            properties.setProperty(JCSMPProperties.SSL_TRUSTED_COMMON_NAME_LIST, conf.getCommonNames());
        }
        if (conf.isValidateCertificates() != null) {
            properties.setProperty(JCSMPProperties.SSL_VALIDATE_CERTIFICATE, conf.isValidateCertificates());
        }
        if (conf.isValidateCertificateDates() != null) {
            properties.setProperty(JCSMPProperties.SSL_VALIDATE_CERTIFICATE_DATE, conf.isValidateCertificateDates());
        }
        if (conf.getAuthenticationScheme().equals(AuthenticationScheme.BASIC)) {
            properties.setProperty(JCSMPProperties.AUTHENTICATION_SCHEME, JCSMPProperties.AUTHENTICATION_SCHEME_BASIC);   
        } else if (conf.getAuthenticationScheme().equals(AuthenticationScheme.CLIENT_CERTIFICATE)) {
            properties.setProperty(JCSMPProperties.AUTHENTICATION_SCHEME, JCSMPProperties.AUTHENTICATION_SCHEME_CLIENT_CERTIFICATE);   
        } else if (conf.getAuthenticationScheme().equals(AuthenticationScheme.KERBEROS)) {
            properties.setProperty(JCSMPProperties.AUTHENTICATION_SCHEME, JCSMPProperties.AUTHENTICATION_SCHEME_GSS_KRB);   
        }
        if (conf.getSslConnetionDowngrade() != null){
        	properties.setProperty(JCSMPProperties.SSL_CONNECTION_DOWNGRADE_TO, conf.getSslConnetionDowngrade());
        }
        // option for in-memory trust stores and key stores:  https://docs.solace.com/API-Developer-Online-Ref-Documentation/java/com/solacesystems/jcsmp/JCSMPProperties.html
        if (conf.getTrustStore() == null && conf.getTrustStorePwd() == null) {
            try {
                KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
                char[] password = "some password".toCharArray();
                ks.load(null, password);
                // initialize keystore here...
                // properties.setProperty(JCSMPProperties.SSL_IN_MEMORY_TRUST_STORE, ks);
            } catch (GeneralSecurityException | IOException e) {
                // deal with it
            }
        }

        session = JCSMPFactory.onlyInstance().createSession(properties);
    }

    void printUsage() {
        StringBuffer buf = new StringBuffer();
        buf.append(ArgParser.getSecureArgUsage());
        System.out.println(buf.toString());
        finish(1);
    }

    public void run(String[] args) {
        FlowReceiver receiver = null;
        try {
            // Create the Session. 
            createSession(args);
            
            // Open the data channel to the appliance.
            System.out.println("About to connect to appliance.");
            session.connect();
            printRouterInfo();
            final String virtRouterName = (String) session.getProperty(JCSMPProperties.VIRTUAL_ROUTER_NAME);
            System.out.printf("Router's virtual router name: '%s'\n", virtRouterName);
            System.out.println("Connected!");

            // Create a Queue to receive messages.
            Queue queue = session.createTemporaryQueue();

            // Create and start the receiver.
            receiver = session.createFlow(queue, null, this);
            receiver.start();

            byte[] data = new byte[] {0x01, 0x02, 0x03, 0x04, 0x05};
            
            XMLMessageProducer producer = session.getMessageProducer(this);
            for (int i = 0; i < 5; i++) {
                BytesXMLMessage msg = JCSMPFactory.onlyInstance().createMessage(BytesXMLMessage.class);
                msg.setDeliveryMode(DeliveryMode.PERSISTENT);
                msg.writeAttachment(data);
                msg.setCorrelationKey(msg);  // correlation key for receiving ACKs
                producer.send(msg, queue);
                Thread.sleep(1000);
            }
            
            // Close the receiver.
            receiver.close();
            finish(0);
        } catch (JCSMPTransportException ex) {
            System.err.println("Encountered a JCSMPTransportException, closing receiver... " + ex.getMessage());
            if (receiver != null) {
                receiver.close();
                // At this point the receiver handle is unusable; a new one
                // should be created.
            }
            finish(1);
        } catch (JCSMPException ex) {
            System.err.println("Encountered a JCSMPException, closing receiver... " + ex.getMessage());
            // Possible causes:
            // - Authentication error: invalid username/password
            // - Invalid or unsupported properties specified
            if (receiver != null) {
                receiver.close();
                // At this point the receiver handle is unusable; a new one
                // should be created.
            }
            finish(1);
        } catch (Exception ex) {
            System.err.println("Encountered an Exception... " + ex.getMessage());
            finish(1);
        }
    }

    public static void main(String[] args) {
        SecureSession app = new SecureSession();
        app.run(args);
    }
}

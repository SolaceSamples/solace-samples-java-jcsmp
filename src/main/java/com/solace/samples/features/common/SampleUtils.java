/**
 * SampleUtils.java
 * 
 * Common utilities to support JCSMP samples.
 * 
 * Copyright 2006-2018 Solace Corporation. All rights reserved.
 */

package com.solace.samples.features.common;

import java.util.Map;
import java.util.Map.Entry;

import com.solacesystems.jcsmp.CacheSession;
import com.solacesystems.jcsmp.CacheSessionProperties;
import com.solacesystems.jcsmp.InvalidPropertiesException;
import com.solacesystems.jcsmp.JCSMPChannelProperties;
import com.solacesystems.jcsmp.JCSMPException;
import com.solacesystems.jcsmp.JCSMPFactory;
import com.solacesystems.jcsmp.JCSMPProperties;
import com.solacesystems.jcsmp.JCSMPSession;
import com.solacesystems.jcsmp.SessionEventHandler;
import com.solace.samples.features.common.SessionConfiguration.AuthenticationScheme;
import com.solacesystems.jcsmp.statistics.StatType;

/**
 * Common utilities to support JCSMP samples.
 */
public class SampleUtils {

	public static final String SAMPLE_TOPIC = "my/sample/topic";
	public static final String SAMPLE_QUEUE = "my_sample_queue";
	public static final String SAMPLE_TOPICENDPOINT = "my_sample_topicendpoint";
	
	/**
	 * Structure representing a Username/VPN combination.
	 */
	public static final class UserVpn {
		private final String _user, _vpn;

		public UserVpn(String user, String vpn) {
			_user = user;
			_vpn = vpn;
		}

		public String get_user() {
			return _user;
		}

		public String get_vpn() {
			return _vpn;
		}

		public static UserVpn parse(final String uservpn) {
			final String[] parts = uservpn.split("@");
			switch (parts.length) {
			case 1:
				return new UserVpn(parts[0], null);
			case 2:
				return new UserVpn(parts[0], parts[1]);
			}
			throw new IllegalArgumentException("Unable to parse " + uservpn);
		}
		
		@Override
		public String toString() {
			if (_vpn == null) {
	            return _user;
			} else {
	            return _user + "@" + _vpn;
			}
		}
	}
	
	public static final String xmldoc = "<sample>1</sample>";
	public static final String xmldocmeta = "<sample><metadata>1</metadata></sample>";
	public static final String attachmentText = "my attached data";
	
	/**
	 * Creates a new JCSMPSession. If session creation fails, this static method will exit the process.
	 * 
	 * @param sc (mandatory) a java bean representing session configuration
	 * @param (optional) evtHdlr a session event handler callback 
	 * @param extra (optional) extra session properties not covered in sc
	 * @return a new JCSMPSession instance
	 */
	public static JCSMPSession newSession(SessionConfiguration sc, SessionEventHandler evtHdlr, Map<String, Object> extra) {
		JCSMPProperties properties = new JCSMPProperties();

		properties.setProperty(JCSMPProperties.HOST, sc.getHost());
		properties.setProperty(JCSMPProperties.PASSWORD, sc.getRouterPassword());
		
		if (sc instanceof SecureSessionConfiguration) {
	        // the host must start with "tcps:"
	        if (!sc.getHost().toLowerCase().startsWith("tcps:")) {
	            System.err.println("Host must start with \"tcps:\"");
	            // TODO: Print usage and exit.
	            return null;
	        }
	        
	        
	        if (sc.getRouterUserVpn() != null) {
	            if (!sc.getRouterUserVpn().get_user().trim().equals("")) {
	                properties.setProperty(JCSMPProperties.USERNAME, sc.getRouterUserVpn().get_user());
	            }
	            if (sc.getRouterUserVpn().get_vpn() != null) {
	                properties.setProperty(JCSMPProperties.VPN_NAME, sc.getRouterUserVpn().get_vpn());
	            }
	        }
	        
	        
	        SecureSessionConfiguration conf = (SecureSessionConfiguration) sc;
	        
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
	            properties.setProperty(JCSMPProperties.AUTHENTICATION_SCHEME, JCSMPProperties.AUTHENTICATION_SCHEME_CLIENT_CERTIFICATE);
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
		} else {
			properties.setProperty(JCSMPProperties.USERNAME, sc.getRouterUserVpn().get_user());
			if (sc.getRouterUserVpn().get_vpn() != null) {
				properties.setProperty(JCSMPProperties.VPN_NAME, sc.getRouterUserVpn().get_vpn());
			}
	        properties.setProperty(JCSMPProperties.MESSAGE_ACK_MODE, JCSMPProperties.SUPPORTED_MESSAGE_ACK_AUTO);
	        
	        // Disable certificate checking
	        properties.setBooleanProperty(JCSMPProperties.SSL_VALIDATE_CERTIFICATE, false);
	        
		}
		
        if (sc.getAuthenticationScheme().equals(AuthenticationScheme.BASIC)) {
            properties.setProperty(JCSMPProperties.AUTHENTICATION_SCHEME, JCSMPProperties.AUTHENTICATION_SCHEME_BASIC);   
        } else if (sc.getAuthenticationScheme().equals(AuthenticationScheme.CLIENT_CERTIFICATE)) {
            properties.setProperty(JCSMPProperties.AUTHENTICATION_SCHEME, JCSMPProperties.AUTHENTICATION_SCHEME_CLIENT_CERTIFICATE);   
        } else if (sc.getAuthenticationScheme().equals(AuthenticationScheme.KERBEROS)) {
            properties.setProperty(JCSMPProperties.AUTHENTICATION_SCHEME, JCSMPProperties.AUTHENTICATION_SCHEME_GSS_KRB);   
        }

		/*
		 * Allow extra properties to supplement / override the above, when set
		 * by a particular sample.
		 */
		if (extra != null) {
			for (Entry<String, Object> extraProp : extra.entrySet()) {
				properties.setProperty(extraProp.getKey(), extraProp.getValue());
			}
		}
		
        // Channel properties
        JCSMPChannelProperties cp = (JCSMPChannelProperties) properties
			.getProperty(JCSMPProperties.CLIENT_CHANNEL_PROPERTIES);
		if (sc.isCompression()) {
			/*
			 * Compression is set as a number from 0-9. 0 means
			 * "disable compression" (the default) and 9 means max compression.
			 * Selecting a non-zero compression level auto-selects the
			 * compressed SMF port on the appliance, as long as no SMF port is
			 * explicitly specified.
			 */
			cp.setCompressionLevel(9);
		}
		
		JCSMPSession session = null;
		try {
			// Create session from JCSMPProperties. Validation is performed by
			// the API and it throws InvalidPropertiesException upon failure.
			System.out.println("About to create session.");
			System.out.println("Configuration: " + sc.toString());
			session = JCSMPFactory.onlyInstance().createSession(properties, null, evtHdlr);
			return session;
		} catch (InvalidPropertiesException ipe) {			
			System.err.println("Error during session creation: ");
			ipe.printStackTrace();
			System.exit(-1);
			return null;
		}
	}

	/**
	 * Prints the most relevant session stats
	 * @param s
     *            parameter
	 */
	static void printSessionStats(JCSMPSession s) {
		if (s == null) return;
		System.out.println("Number of messages sent: "
			+ s.getSessionStats().getStat(StatType.TOTAL_MSGS_SENT));
		System.out.println("Number of messages received: "
			+ s.getSessionStats().getStat(StatType.TOTAL_MSGS_RECVED));
	}

	/**
	 * Creates and returns a CacheSession from an existing JCSMPSession. 
	 */
	public static CacheSession newCacheSession(
		JCSMPSession jcsmpSession,
		CacheSessionConfiguration sc) throws JCSMPException {
		CacheSessionProperties cacheProps = new CacheSessionProperties(sc.getCacheName(), sc
			.getMaxMsgs(), sc.getMaxAge(), sc.getTimeout());
		return jcsmpSession.createCacheSession(cacheProps);
	}
	
}

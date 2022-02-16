package com.solace.samples.jcsmp.snippets;

import com.solacesystems.jcsmp.JCSMPProperties;
import com.solacesystems.jcsmp.InvalidPropertiesException;
import com.solacesystems.jcsmp.JCSMPFactory;
import com.solacesystems.jcsmp.JCSMPSession;

public class HowToConfigureSessionUsingOAUTH2 {


  public void createOauth2Session(JCSMPProperties sessionProperties, String accessToken, String issuerId) throws InvalidPropertiesException
  {
     sessionProperties.setProperty(JCSMPProperties.AUTHENTICATION_SCHEME, JCSMPProperties.AUTHENTICATION_SCHEME_OAUTH2);
     sessionProperties.setProperty(JCSMPProperties.OAUTH2_ACCESS_TOKEN, accessToken);
     sessionProperties.setProperty(JCSMPProperties.OAUTH2_ISSUER_IDENTIFIER, issuerId); 
     final JCSMPFactory sessionFactory = JCSMPFactory.onlyInstance();
     final JCSMPSession session = sessionFactory.createSession(sessionProperties);
  }
    
}

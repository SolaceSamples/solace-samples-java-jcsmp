package com.solace.samples.jcsmp.snippets;

import com.solacesystems.jcsmp.JCSMPProperties;
import com.solacesystems.jcsmp.InvalidPropertiesException;
import com.solacesystems.jcsmp.JCSMPFactory;
import com.solacesystems.jcsmp.JCSMPSession;

public class HowToConfigureSessionUsingOpenIdConnect {

  public void createOIDCSession(JCSMPProperties sessionProperties, String idToken, String accessTokenOptional) throws InvalidPropertiesException
  {
    sessionProperties.setProperty(JCSMPProperties.AUTHENTICATION_SCHEME, JCSMPProperties.AUTHENTICATION_SCHEME_OAUTH2);
    sessionProperties.setProperty(JCSMPProperties.OAUTH2_ACCESS_TOKEN, accessTokenOptional);
    sessionProperties.setProperty(JCSMPProperties.OIDC_ID_TOKEN, idToken);
     
    final JCSMPFactory sessionFactory = JCSMPFactory.onlyInstance();
   
    final JCSMPSession session =sessionFactory.createSession(sessionProperties);
  }
    
}

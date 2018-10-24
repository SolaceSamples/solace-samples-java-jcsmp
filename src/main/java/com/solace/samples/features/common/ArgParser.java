/**
 * ArgParser.java
 * 
 * Copyright 2009-2018 Solace Corporation. All rights reserved.
 */

package com.solace.samples.features.common;

import com.solacesystems.jcsmp.CacheLiveDataAction;
import com.solacesystems.jcsmp.DeliveryMode;
import com.solace.samples.features.common.SampleUtils.UserVpn;
import com.solace.samples.features.common.SessionConfiguration.AuthenticationScheme;

public class ArgParser {
	SessionConfiguration sc;

	public ArgParser() {
	}

	public void setConfig(SessionConfiguration value) {
		this.sc = value;
	}
	
	public SessionConfiguration getConfig() {
		return sc;
	}
	
	public boolean isSecure() {
		return sc instanceof SecureSessionConfiguration;
	}

	/**
	 * Parse command-line: the common params are stored in dedicated
	 * SessionConfiguration fields, while program-specific params go into the
	 * argBag map field.
	 */
	public int parse(String[] args) {
		if (getConfig() == null) {
			setConfig(new SessionConfiguration());
		}
		try {
			for (int i = 0; i < args.length; i++) {
				if (args[i].equals("-h")) {
					i++;
					sc.setHost(args[i]);
				} else if (args[i].equals("-x")) {
					i++;
					if (i >= args.length) return 1;
		
					if (args[i].toLowerCase().equals("client_certificate")) {
					    if (!(sc instanceof SecureSessionConfiguration)) {
	                        System.err.println("Invalid value for -x : Must be either BASIC or KERBEROS");
	                        return 1;
					    }
					    sc.setAuthenticationScheme(AuthenticationScheme.CLIENT_CERTIFICATE);
					} else if (args[i].toLowerCase().equals("basic")) {
                        sc.setAuthenticationScheme(AuthenticationScheme.BASIC);
					} else if (args[i].toLowerCase().equals("kerberos")) {
                        sc.setAuthenticationScheme(AuthenticationScheme.KERBEROS);
					} else {
                        if (sc instanceof SecureSessionConfiguration) {
                            System.err.println("Invalid value for -x : Must be either BASIC or CLIENT_CERTIFICATE or KERBEROS");
                        } else {
                            System.err.println("Invalid value for -x : Must be either BASIC or KERBEROS");
                        }
						return 1;
					}
					// Do nothing, its already been handled
				} else if (args[i].equals("-u")) {
					i++;
					sc.setRouterUsername(UserVpn.parse(args[i]));
				} else if (args[i].equals("-w")) {
					i++;
					sc.setRouterPassword(args[i]);
				} else if (args[i].equals("-z")) {
					sc.setCompression(true);
				} else if (args[i].equals("-t")) {
					i++;
					String dm = args[i].toLowerCase();
					DeliveryMode dmobj = parseDeliveryMode(dm);
					if (dmobj != null)
						sc.setDeliveryMode(dmobj);
					else
						return 1; // err
				} else if (args[i].equals("--help")) {
					return 1; // err: print help
				} else {
					String str_key = args[i];
					String str_value = "";
					if (i + 1 < args.length) {
						String str_tmpvalue = args[i + 1]; // lookahead
						if (!str_tmpvalue.startsWith("-")) {
							// we have a value!
							i++;
							str_value = args[i];
						}
					}
					sc.getArgBag().put(str_key, str_value);
				}
			}
		} catch (Exception e) {
			return 1; // err
		}

		if (sc.getHost() == null || (sc.getRouterUserVpn() == null && sc.getAuthenticationScheme().equals(AuthenticationScheme.BASIC))) {
			return 1; // err
		}

		//Make sure the username if provided when certificate authentication is not used (and -u with @vpn is)
		if (sc.getRouterUserVpn() != null) {
		    if (sc.getRouterUserVpn().get_user().trim().equals("") && (sc.getAuthenticationScheme().equals(AuthenticationScheme.BASIC))) {
		        System.err.println("USER must be specified when using basic authentication scheme.");
		        return 1; //Must provide a username when certificate authentication is not used.
		    }
		}
		
		if (sc.getAuthenticationScheme().equals(AuthenticationScheme.CLIENT_CERTIFICATE) && (((SecureSessionConfiguration)sc).getKeyStore() == null)) {
		    System.err.println("KEY_STORE must be specified when using client certificate authentication scheme.");
		    return 1;
		}
		
		return 0; // success
	}

    public int parseCacheSampleArgs(String[] args) {
        CacheSessionConfiguration cf = new CacheSessionConfiguration();
        this.sc = cf;
        parse(args); //parse common arguments
        
        for (int i = 0; i < args.length; i++) {
        	if (args[i].equals("-c")) {
                i++;
                if (i >= args.length) return 1;
                cf.setCacheName(args[i]);
            } else if (args[i].equals("-m")) {
                i++;
                if (i >= args.length) return 1;
                cf.setMaxMsgs(Integer.valueOf(args[i]));
            } else if (args[i].equals("-a")) {
                i++;
                if (i >= args.length) return 1;
                cf.setMaxAge(Integer.valueOf(args[i]));
            } else if (args[i].equals("-o")) {
                i++;
                if (i >= args.length) return 1;
                cf.setTimeout(Integer.valueOf(args[i]));
            } else if (args[i].equals("-s")) {
                i++;
                if (i >= args.length) return 1;
                cf.setSubscribe(Boolean.parseBoolean(args[i]));
            } else if (args[i].equals("-l")) {
                i++;
                if (i >= args.length) return 1;
                cf.setAction(CacheLiveDataAction.valueOf(args[i]));
            }
        }

        if (cf.getCacheName() == null) {
            System.err.println("No cache name specified");
            return 1;
        }
        return 0;
    }
    
    public int parseSecureSampleArgs(String[] args) {
    	
        //parse common arguments
        if (readSecureArgs(args) != 0 || parse(args) != 0) {
            return 1;
        }
        
        return 0;
    }

    protected int readSecureArgs(String[] args) {
        SecureSessionConfiguration cf = new SecureSessionConfiguration();
        this.sc = cf;
        
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-exclprot")) {
                    i++;
                    if (i >= args.length) return 1;
                    cf.setExcludeProtocols(args[i]);
            } else if (args[i].equals("-ciphers")) {
                i++;
                if (i >= args.length) return 1;
                cf.setCiphers(args[i]);
            } else if (args[i].equals("-ts")) {
                i++;
                if (i >= args.length) return 1;
                cf.setTrustStore(args[i]);
            } else if (args[i].equals("-tsfmt")) {
                i++;
                if (i >= args.length) return 1;
                cf.setTrustStoreFmt(args[i]);
            } else if (args[i].equals("-tspwd")) {
                i++;
                if (i >= args.length) return 1;
                cf.setTrustStorePwd(args[i]);
            } else if (args[i].equals("-ks")) {
                i++;
                if (i >= args.length) return 1;
                cf.setKeyStore(args[i]);
            } else if (args[i].equals("-ksfmt")) {
                i++;
                if (i >= args.length) return 1;
                cf.setKeyStoreFmt(args[i]);
            } else if (args[i].equals("-ksnfmt")) {
                i++;
                if (i >= args.length) return 1;
                cf.setKeyStoreNormalizedFmt(args[i]);
            } else if (args[i].equals("-kspwd")) {
                i++;
                if (i >= args.length) return 1;
                cf.setKeyStorePwd(args[i]);
            } else if (args[i].equals("-pk")) {
                i++;
                if (i >= args.length) return 1;
                cf.setPrivateKeyAlias(args[i]);
            } else if (args[i].equals("-pkpwd")) {
                i++;
                if (i >= args.length) return 1;
                cf.setPrivateKeyPwd(args[i]);
            } else if (args[i].equals("-no_validate_certificates")) {
                cf.setValidateCertificates(false);
            } else if (args[i].equals("-no_validate_dates")) {
                cf.setValidateCertificateDates(false);
            } else if (args[i].equals("-cn")) {
                i++;
                if (i >= args.length) return 1;
                cf.setCommonNames(args[i]);
            } else if (args[i].equals("-downgr")) {
            	i++;
            	if (i >= args.length) return 1;
            	cf.setSslConnetionDowngrade(args[i]);
            }
        }
        
        return 0;
    }

	public static DeliveryMode parseDeliveryMode(String dm) {
		if (dm == null)
			return null;
		dm = dm.toLowerCase();
		if (dm.equals("direct")) {
			return DeliveryMode.DIRECT;
		} else if (dm.equals("persistent")) {
			return DeliveryMode.PERSISTENT;
		} else if (dm.equals("non-persistent")) {
			return DeliveryMode.NON_PERSISTENT;
		} else {
			return null;
		}
	}

	public static String getCommonUsage(boolean secure) {
		String str = "Common parameters:\n";
		str += "\t -h HOST[:PORT]  Appliance IP address [:port, omit for default]\n";
		str += "\t -u USER[@VPN]   Authentication username [@vpn, omit for default]\n";
		str += "\t[-w PASSWORD]    Authentication password\n";
		str += "\t[-z]             Enable compression\n";
        str += "\t[-x AUTH_METHOD] authentication scheme (One of : BASIC, KERBEROS). (Default: BASIC).  Specifying USER is mandatory when BASIC is used.\n";
		
		if (secure) {
			str += getSecureArgUsage();
		}
		
		return str;
	}
	
    public static String getCacheArgUsage(boolean secure) {
        StringBuffer buf = new StringBuffer();
        buf.append(ArgParser.getCommonUsage(secure));
        buf.append("Cache request parameters:\n");
        buf.append("\t -c CACHE_NAME  Cache for the initial cache request\n");
        buf.append("\t[-m MAX_MSGS]   Maximum messages per topic to retrieve (default: 1)\n");
        buf.append("\t[-a MAX_AGE]    Maximum age of messages to retrieve (default: 0)\n");
        buf.append("\t[-o TIMEOUT]    Cache request timeout in ms (default: 5000)\n");
        buf.append("\t[-s SUBSCRIBE]  Subscribe to cache topic (default: false)\n");
        buf.append("\t[-l ACTION]     Live data action (default: FLOW_THRU), one of\n");
        buf.append("\t                  FLOW_THRU (Pass through live data that arrives while a\n");
        buf.append("\t                             cache request is outstanding)\n");
        buf.append("\t                  QUEUE     (Queue live data that arrives that matches \n");
        buf.append("\t                             the topic until the cache request completes)\n");
        buf.append("\t                  FULFILL   (Consider the cache request finished when live\n");
        buf.append("\t                             data arrives that matches the topic)\n");
        return buf.toString();
     }

    public static String getSecureArgUsage() {
        StringBuffer buf = new StringBuffer();
        buf.append("Secure request parameters:\n");
        buf.append("\t -h HOST[:PORT]  Appliance IP address [:port, omit for default]\n");
        buf.append("\t[-u [USER][@VPN]]  Authentication username [USER part is optional with client certificate authentication (which is in use when -x CLIENT_CERTIFICATE is specified)] [@vpn, omit for default]\n");
        buf.append("\t[-x AUTH_METHOD] authentication scheme (One of : BASIC, CLIENT_CERTIFICATE, KERBEROS). (Default: BASIC).  Specifying USER is mandatory when BASIC is used.\n");
        buf.append("\t[-w PASSWORD]    Authentication password\n");
        buf.append("\t[-z]             Enable compression\n");
        buf.append("\t[-exclprot PROTOCOL]  A comma separated list of encryption protocol(s) to exclude - values [sslv3, tlsv1, tlsv1.1, tlsv1.2] (default: none)\n");
        buf.append("\t[-ciphers CIPHERS]   A comma separated list of the cipher suites to enable (default: all supported ciphers)\n");
        buf.append("\t[-ts TRUST_STORE]   The path to a trust store file that contains trusted root CAs.  This parameter is mandatory unless -no_validate_certificates is specified.  Used to validate the appliance's server certificate.\n");
        buf.append("\t[-tsfmt TRUST_STORE_FORMAT]    The format of the specified trust store - values (default: JKS)\n");
        buf.append("\t[-tspwd TRUST_STORE_PASSWORD]    The password for the specified trust store.  This is used to check the trust store's integrity.\n");
        buf.append("\t[-ks KEY_STORE]   The key store file to use for client certificate authentication.  Required when -x CLIENT_CERTIFICATE is specified.\n");
        buf.append("\t[-ksfmt KEY_STORE_FORMAT]   The format of the specified key store - values (default: JKS)\n");
        buf.append("\t[-ksnfmt KEY_STORE_NORMALIZED_FORMAT]   The format of the internal normalized key store - values (default: none - means same as KEY_STORE_NORMALIZED_FORMAT)\n");
        buf.append("\t[-kspwd KEY_STORE_PASSWORD]   The password for the specified key store.  This is used to check the key store's integrity.  This parameter is mandatory when the -pkpwd option is not specified.  It is used to decipher the private key when the -pkpwd option is omitted.\n");
        buf.append("\t[-pk PRIVATE_KEY_ALIAS]   The alias of the private key to use for client certificate authentication \n");
        buf.append("\t[-pkpwd PRIVATE_KEY_PASSWORD]   The password to decipher the private key from the key store (default: the value passed for KEY_STORE_PASSWORD) \n");
        buf.append("\t[-no_validate_certificates]  Disables validation of the server certificate (default: validation enabled)\n");
        buf.append("\t[-no_validate_dates]    Disables validation of the server certificate expiry and not before dates (default: date validation enabled)\n");
        buf.append("\t[-cn COMMON_NAMES]    Specifies the list of acceptable common names for matching in server certificates (default: no validation performed)\n");
        buf.append("\t[-downgr PLAIN_TEXT]  Downgrade SSL connection to 'PLAIN_TEXT' after client authentication.");
        return buf.toString();
    }
}

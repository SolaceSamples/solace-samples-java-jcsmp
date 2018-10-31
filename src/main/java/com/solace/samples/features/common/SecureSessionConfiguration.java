/**
 * SecureSessionConfiguration.java
 * 
 * Copyright 2006-2018 Solace Corporation. All rights reserved.
 */

package com.solace.samples.features.common;


public class SecureSessionConfiguration extends SessionConfiguration {

    private String excludeProtocols;
    private String ciphers;
    private String trustStore;
    private String trustStoreFmt;
    private String trustStorePwd;
    private String commonNames;
    private Boolean validateCertificates;
    private Boolean validateCertificateDates;
    private String keyStore;
    private String keyStoreFmt;
    private String keyStoreNormalizedFmt;
    private String keyStorePwd;
    private String privateKeyAlias;
    private String privateKeyPwd;
    private String sslConnDowngrade;
    
    public SecureSessionConfiguration() {
        ciphers = null;
        trustStore = null;
        trustStoreFmt = null;
        trustStorePwd = null;
        keyStore = null;
        keyStoreFmt = null;
        keyStoreNormalizedFmt = null;
        keyStorePwd = null;
        privateKeyAlias = null;
        privateKeyPwd = null;
        commonNames = null;
        validateCertificates = null;
        validateCertificateDates = null;
        sslConnDowngrade = null;
    }
    
    public String getExcludeProtocols() {
        return excludeProtocols;
    }
    
    public void setExcludeProtocols(String excludeProtocols) {
        this.excludeProtocols = excludeProtocols;
    }

    public String getCiphers() {
        return ciphers;
    }
    
    public void setCiphers(String ciphers) {
        this.ciphers = ciphers;
    }
    
    public String getTrustStore() {
        return trustStore;
    }
    
    public void setTrustStore(String trustStore) {
        this.trustStore = trustStore;
    }
    
    public String getTrustStoreFmt() {
        return trustStoreFmt;
    }
    
    public void setTrustStoreFmt(String trustStoreFmt) {
        this.trustStoreFmt = trustStoreFmt;
    }
    
    public String getTrustStorePwd() {
        return trustStorePwd;
    }
    
    public void setTrustStorePwd(String trustStorePwd) {
        this.trustStorePwd = trustStorePwd;
    }
    
    public String getCommonNames() {
        return commonNames;
    }
    
    public void setCommonNames(String commonNames) {
        this.commonNames = commonNames;
    }
    
    public Boolean isValidateCertificates() {
        return validateCertificates;
    }
    
    public void setValidateCertificates(boolean validateCertificates) {
        this.validateCertificates = validateCertificates;
    }
    
    public Boolean isValidateCertificateDates() {
        return validateCertificateDates;
    }
    
    public void setValidateCertificateDates(boolean validateCertificateDates) {
        this.validateCertificateDates = validateCertificateDates;
    }

    public void setSslConnetionDowngrade(String sslConnDowngrade) {
        this.sslConnDowngrade = sslConnDowngrade;
    }
    
    public String getSslConnetionDowngrade( ) {
        return this.sslConnDowngrade;
    }

    @Override
    public String toString() {
        StringBuilder bldr = new StringBuilder(super.toString());
        bldr.append(", excludeProtocols=");
        bldr.append(excludeProtocols);
        bldr.append(", ciphers=");
        bldr.append(ciphers);
        bldr.append(", trustStore=");
        bldr.append(trustStore);
        bldr.append(", trustStoreFmt=");
        bldr.append(trustStoreFmt);
        bldr.append(", trustStorePwd=");
        bldr.append(trustStorePwd);
        bldr.append(", keyStore=");
        bldr.append(keyStore);
        bldr.append(", keyStoreFmt=");
        bldr.append(keyStoreFmt);
        bldr.append(", keyStoreNormalizedFmt=");
        bldr.append(keyStoreNormalizedFmt);
        bldr.append(", keyStorePwd=");
        bldr.append(keyStorePwd);
        bldr.append(", privateKeyAlias=");
        bldr.append(privateKeyAlias);
        bldr.append(", privateKeyPwd=");
        bldr.append(privateKeyPwd);
        bldr.append(", commonNames=");
        bldr.append(commonNames);
        bldr.append(", validateCertificates=");
        bldr.append(validateCertificates);
        bldr.append(", validateCertificateDates=");
        bldr.append(validateCertificateDates);
        bldr.append(", sslConnDowngrade=");
        bldr.append(sslConnDowngrade);        
        return bldr.toString();
    }

    public void setKeyStore(String keyStore) {
        this.keyStore = keyStore;
    }

    public void setKeyStoreFmt(String keyStoreFmt) {
        this.keyStoreFmt = keyStoreFmt;
    }

    public void setKeyStoreNormalizedFmt(String keyStoreNormalizedFmt) {
        this.keyStoreNormalizedFmt = keyStoreNormalizedFmt;
    }

    public void setKeyStorePwd(String keyStorePwd) {
        this.keyStorePwd = keyStorePwd;
    }
    
    public void setPrivateKeyAlias(String privateKeyAlias) {
        this.privateKeyAlias = privateKeyAlias;
    }
    
    public void setPrivateKeyPwd(String privateKeyPwd) {
        this.privateKeyPwd = privateKeyPwd;
    }

    public String getKeyStore() {
        return this.keyStore;
    }

    public String getKeyStoreFmt() {
        return this.keyStoreFmt;
    }
    
    public String getKeyStoreNormalizedFmt() {
        return keyStoreNormalizedFmt;
    }

    public String getKeyStorePwd() {
        return this.keyStorePwd;
    }
    
    public String getPrivateKeyAlias() {
        return this.privateKeyAlias;
    }
    
    public String getPrivateKeyPwd() {
        return this.privateKeyPwd;
    }
}

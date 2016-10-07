package org.mobicents.tools.configuration;

public class SslConfiguration {
	
	 public static final Boolean TERMINATE_TLS_TRAFFIC = false;
	 public static final String KEY_STORE = null;
	 public static final String KEY_STORE_PASSWORD = null;
	 public static final String TRUST_STORE = null;
	 public static final String TRUST_STORE_PASSWORD = null;
	 public static final String TLS_CLIENT_PROTOCOLS = null;
	 public static final String ENABLED_CIPHER_SUITES = null;
	 
	 private Boolean terminateTLSTraffic;
	 private String keyStore;
	 private String keyStorePassword;
	 private String trustStore;
	 private String trustStorePassword;
	 private String tlsClientProtocols;
	 private String enabledCipherSuites;
	 
	 public SslConfiguration(){
		 
		 this.terminateTLSTraffic = TERMINATE_TLS_TRAFFIC;
		 this.keyStore = KEY_STORE;
		 this.keyStorePassword = KEY_STORE_PASSWORD;
		 this.trustStore = TRUST_STORE;
		 this.trustStorePassword = TRUST_STORE_PASSWORD;
		 this.tlsClientProtocols = TLS_CLIENT_PROTOCOLS;
		 this.enabledCipherSuites = ENABLED_CIPHER_SUITES;
	 }

	public Boolean getTerminateTLSTraffic() {
		return terminateTLSTraffic;
	}

	public void setTerminateTLSTraffic(Boolean terminateTLSTraffic) {
		this.terminateTLSTraffic = terminateTLSTraffic;
	}

	public String getKeyStore() {
		return keyStore;
	}

	public void setKeyStore(String keyStore) {
		this.keyStore = keyStore;
	}

	public String getKeyStorePassword() {
		return keyStorePassword;
	}

	public void setKeyStorePassword(String keyStorePassword) {
		this.keyStorePassword = keyStorePassword;
	}

	public String getTrustStore() {
		return trustStore;
	}

	public void setTrustStore(String trustStore) {
		this.trustStore = trustStore;
	}

	public String getTrustStorePassword() {
		return trustStorePassword;
	}

	public void setTrustStorePassword(String trustStorePassword) {
		this.trustStorePassword = trustStorePassword;
	}

	public String getTlsClientProtocols() {
		return tlsClientProtocols;
	}

	public void setTlsClientProtocols(String tlsClientProtocols) {
		this.tlsClientProtocols = tlsClientProtocols;
	}

	public String getEnabledCipherSuites() {
		return enabledCipherSuites;
	}

	public void setEnabledCipherSuites(String enabledCipherSuites) {
		this.enabledCipherSuites = enabledCipherSuites;
	}
	 

}

package org.mobicents.tools.sip.balancer;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.rmi.RemoteException;
import java.util.Properties;

import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;

/**
 * @deprecated
 * @author deruelle
 *
 */
public class RegisterTest {

	
	static InetAddress addr=null;
	static
	{
		try {
			addr=InetAddress.getByAddress(new byte[]{127,0,0,1});
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	static int registerPort=5100;
	static int externalPort=5010;
	static int internalPort=5020;
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub

		
		testBalancer();
		
	}
	
	private static void testBalancer()
	{
		Properties properties = new Properties();
		properties.setProperty("javax.sip.STACK_NAME", "SipBalancerForwarder");
		properties.setProperty("javax.sip.AUTOMATIC_DIALOG_SUPPORT", "off");
		// You need 16 for logging traces. 32 for debug + traces.
		// Your code will limp at 32 but it is best for debugging.
		properties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "32");
		properties.setProperty("gov.nist.javax.sip.DEBUG_LOG",
				"logs/sipbalancerforwarderdebug.txt");
		properties.setProperty("gov.nist.javax.sip.SERVER_LOG",
				"logs/sipbalancerforwarder.xml");
		properties.setProperty("gov.nist.javax.sip.THREAD_POOL_SIZE", "64");
		properties.setProperty("gov.nist.javax.sip.REENTRANT_LISTENER", "true");
		properties.setProperty("gov.nist.javax.sip.CANCEL_CLIENT_TRANSACTION_CHECKED", "false");
		
		properties.setProperty("host", "127.0.0.1");
		properties.setProperty("internalPort", "5065");
		properties.setProperty("externalPort", "5060");
		
		try {
			NodeRegisterImpl reg = prepareRegister();

			reg.startServer();
			RouterImpl.setRegister(reg);
			SIPBalancerForwarder fwd = new SIPBalancerForwarder(properties, reg);
		} catch (Exception e) {
			e.printStackTrace();
		}
		try {
			System.in.read();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	
	
	private static NodeRegisterImpl prepareRegister()
	{
		
		try {
			
			
			//NodeRegisterImpl reg=new NodeRegisterImpl(addr,registerPort);
			NodeRegisterImpl reg=new NodeRegisterImpl(addr);
			
			MBeanServer server=ManagementFactory.getPlatformMBeanServer();
			ObjectName on=new ObjectName("slee:name=Balancer,type=sip");
			
			if(server.isRegistered(on))
			{
				server.unregisterMBean(on);
			}
			
			server.registerMBean(reg, on);
			
			return reg;
				
		} catch (MalformedObjectNameException e) {
			
			e.printStackTrace();
		} catch (NullPointerException e) {
			
			e.printStackTrace();
		} catch (InstanceAlreadyExistsException e) {
			
			e.printStackTrace();
		} catch (MBeanRegistrationException e) {
			
			e.printStackTrace();
		} catch (NotCompliantMBeanException e) {
			
			e.printStackTrace();
		} catch (InstanceNotFoundException e) {
			
			e.printStackTrace();
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
	
	private static void undoRegister(NodeRegisterImpl reg)
	{
		
		
	
			
			
			try {
				MBeanServer server=ManagementFactory.getPlatformMBeanServer();
				ObjectName on = new ObjectName("slee:name=Balancer,type=sip");
				server.unregisterMBean(on);
				reg.stopServer();
			} catch (MalformedObjectNameException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (NullPointerException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InstanceNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (MBeanRegistrationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
	
		
		
		
	}

}

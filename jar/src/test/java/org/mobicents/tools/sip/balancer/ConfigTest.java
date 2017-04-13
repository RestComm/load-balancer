/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2015, Telestax Inc and individual contributors
 * by the @authors tag.
 *
 * This program is free software: you can redistribute it and/or modify
 * under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */
package org.mobicents.tools.sip.balancer;

import java.io.File;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mobicents.tools.configuration.XmlConfigurationLoader;

/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */

public class ConfigTest{


	XmlConfigurationLoader loader = new XmlConfigurationLoader();
	@Before
	public void setUp() {

		loader.load(new File(ConfigTest.class.getClassLoader().getResource("lb-configuration-test.xml").getFile()));
	}

	@After
	public void tearDown()
	{
	}

	@Test
	public void testGracefulRemovingNode() {

		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
}




/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, Red Hat, Inc. and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.mobicents.tools.sip.balancer.scalability;

import java.util.TreeSet;

import org.mobicents.tools.sip.balancer.PureConsistentHashBalancerAlgorithm;

import junit.framework.TestCase;

public class ConsistentHashDistributionTest extends TestCase{
	TreeSet<Integer> set = new TreeSet<Integer>();
	public void testDistribution() {
		PureConsistentHashBalancerAlgorithm algo = new PureConsistentHashBalancerAlgorithm();
		for(Integer i = 0; i<12199; i++ ) {
			System.out.println(Integer.toString(algo.digest(i.toString()),2));
			if(!set.add(new Integer(algo.digest(i.toString()))))
				fail("duplicate for i " + i); // better not have duplicates easily
		}
		boolean bits[] = new boolean[32];
		for(int q=0;q<31;q++) {
			boolean has1 = false;
			boolean has0 = false;
			for(Integer i : set) {
				boolean isOne = (i&(1<<q))>0;
				if(isOne) has1 = true; else has0 = true;
				if(has1 && has0) break;
			}
			if(has1 && has0) {
				bits[q] = true;
			}
		}
		
		for(int q=0;q<31;q++) {
			if(!bits[q]) fail("Bit " + q + " is never changing");
		}
	}
	
	/*
	 * Demo that string hash is pretty bad. The output here is:
	 * 
11010000000010010110010101111
11010000000010010110010110000
11010000000010010110010110001
11010000000010010110010110010
11010000000010010110010110011
11010000000010010110010110100
11010000000010010110010110101
11010000000010010110010110110
11010000000010010110010110111
11010000000010010110010111000

for i + "" + i
100110001001000110100101100001
100110001001000110100110000001
100110001001000110100110100001
100110001001000110100111000001
100110001001000110100111100001
100110001001000110101000000001
100110001001000110101000100001
100110001001000110101001000001
100110001001000110101001100001
100110001001000110101010000001

	 */
	public void testStringHashDistribution() {
		for(Integer i = 0; i<10; i++ ) {
			System.out.println(Integer.toString(("aaaaaaaaaaaaa"+i + "" + i).hashCode(),2));
		}
	}
}

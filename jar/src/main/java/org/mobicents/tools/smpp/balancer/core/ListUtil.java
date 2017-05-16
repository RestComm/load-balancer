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
package org.mobicents.tools.smpp.balancer.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */
public class ListUtil {

    public static <T> ListDiff<T> diff(List<T> one, List<T> two) {

        List<T> removed = new ArrayList<T>();
        List<T> added = new ArrayList<T>();

        for (int i = 0; i < one.size(); i++) {
            T elementOne = one.get(i);
            if (!two.contains(elementOne)) {
                //element in one is removed from two
                removed.add(elementOne);
            }
        }

        for (int i = 0; i < two.size(); i++) {
            T elementTwo = two.get(i);
            if (!one.contains(elementTwo)) {
                //element in two is added.
                added.add(elementTwo);
            }
        }

        return new ListDiff<T>(removed, added);
    }

    public static <T> ListDiff<T> diff(List<T> one, List<T> two, Comparator<T> comparator) {
        List<T> removed = new ArrayList<T>();
        List<T> added = new ArrayList<T>();

        for (int i = 0; i < one.size(); i++) {
            T elementOne = one.get(i);
            boolean found = false;

            //loop checks if element in one is found in two.
            for (int j = 0; j < two.size(); j++) {
                T elementTwo = two.get(j);
                if (comparator.compare(elementOne, elementTwo) == 0) {
                    found = true;
                    break;
                }
            }
            if (found == false) {
                //element is not found in list two. it is removed.
                removed.add(elementOne);
            }
        }

        for (int i = 0; i < two.size(); i++) {
            T elementTwo = two.get(i);
            boolean found = false;

            //loop checks if element in two is found in one.
            for (int j = 0; j < one.size(); j++) {
                T elementOne = one.get(j);
                if (comparator.compare(elementTwo, elementOne) == 0) {
                    found = true;
                    break;
                }
            }
            if (found == false) {
                //it means element has been added to list two. 
                added.add(elementTwo);
            }

        }

        return new ListDiff<T>(removed, added);
    }
}

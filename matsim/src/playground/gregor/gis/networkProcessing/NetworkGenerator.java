/* *********************************************************************** *
 * project: org.matsim.*
 * NetworkGenerator.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2007 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package playground.gregor.gis.networkProcessing;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import org.apache.log4j.Logger;
import org.geotools.data.FeatureSource;
import org.geotools.feature.Feature;
import org.geotools.feature.FeatureIterator;
import org.matsim.basic.v01.Id;
import org.matsim.basic.v01.IdImpl;
import org.matsim.network.NetworkLayer;
import org.matsim.network.NetworkWriter;
import org.matsim.network.algorithms.NetworkCleaner;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Polygon;


import playground.gregor.gis.utils.ShapeFileReader;

public class NetworkGenerator {
	private static final Logger log = Logger.getLogger(NetworkGenerator.class);
	private NetworkLayer network;
	private Collection<Feature> pn;
	private Collection<Feature> pl;
	
	public NetworkGenerator(Collection<Feature> pn, Collection<Feature> pl,
			NetworkLayer network) {
		this.network = network;
		this.pn = pn;
		this.pl = pl;
	}

	private NetworkLayer constructNetwork() {
		createNodes2();
		createLinks();
		new NetworkCleaner().run(network);
		
		return this.network;
	}
	
	private void createLinks() {
		for (Feature link : this.pl) {
			int id = (Integer) link.getAttribute(1);

			int from = (Integer) link.getAttribute(2);
			int to = (Integer) link.getAttribute(3);
			double minWidth = (Double) link.getAttribute(4);
			double area = (Double) link.getAttribute(5);
			double length = (Double) link.getAttribute(6);
			double avgWidth = Math.max(area/length, minWidth);
			
			if (minWidth < 0.71 ){
				log.warn("wrong flowcap!");
				minWidth = 200;
			}
			double permlanes = Math.max(avgWidth,minWidth) / 0.71;
			double flowcap = Math.max(minWidth / 0.71,1);

			this.network.createLink(Integer.toString(id), Integer.toString(from), Integer.toString(to), Double.toString(length), "1.66", Double.toString(flowcap), Double.toString(permlanes), Integer.toString(id), "");
			
			this.network.createLink(Integer.toString(id+100000), Integer.toString(to), Integer.toString(from), Double.toString(length), "1.66", Double.toString(flowcap), Double.toString(permlanes), Integer.toString(id), "");
			
		}
			
		
	}

	private void createNodes2() {
		for (Feature node : this.pn) {
			Coordinate c = node.getDefaultGeometry().getGeometryN(0).getCoordinate();
			Integer id = (Integer) node.getAttribute(1);
			this.network.createNode(id.toString(),Double.toString(c.x), Double.toString(c.y), "");
		}
		
		
	}
	
	private void createNodes() {
		for (Feature link : this.pn) {
			LineString ls = (LineString) link.getDefaultGeometry().getGeometryN(0); 
			Integer from = (Integer)link.getAttribute(2);
			Integer to = (Integer)link.getAttribute(3);
			Coordinate fromC = ls.getStartPoint().getCoordinate();
			Coordinate toC = ls.getEndPoint().getCoordinate();
			if ( this.network.getNode(from.toString()) == null){
				this.network.createNode(from.toString(),Double.toString(fromC.x), Double.toString(fromC.y), "");
			}

			if ( this.network.getNode(to.toString()) == null){
				this.network.createNode(to.toString(),Double.toString(toC.x), Double.toString(toC.y), "");
			}
			
		}
		
	}

	public static void main(String [] args) {
		String nodes = "./padang/network_v20080608/nodes.shp";
		String links = "./padang/network_v20080608/links.shp";
		
	
		FeatureSource n = null;
		try {
			n = ShapeFileReader.readDataFile(nodes);
		} catch (Exception e) {
			e.printStackTrace();
		}
		Collection<Feature>pn = getPolygons(n);
		
		FeatureSource l = null;
		try {
			l = ShapeFileReader.readDataFile(links);
		} catch (Exception e) {
			e.printStackTrace();
		}
		Collection<Feature> pl = getPolygons(l);
		
		
		NetworkLayer network = new NetworkLayer();
		network.setEffectiveCellSize(0.26);
		network.setEffectiveLaneWidth(0.71);
		network.setCapacityPeriod(1);
		new NetworkGenerator(pn, pl , network).constructNetwork();
		
		new NetworkWriter(network,"pdg_new.xml").write();
		
	}



	private static Collection<Feature> getPolygons(FeatureSource n) {
		Collection<Feature> polygons = new ArrayList<Feature>();
		FeatureIterator it = null;
		try {
			it = n.getFeatures().features();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		while (it.hasNext()) {
			Feature feature = it.next();
//			int id = (Integer) feature.getAttribute(1);
//			MultiPolygon multiPolygon = (MultiPolygon) feature.getDefaultGeometry();
//			if (multiPolygon.getNumGeometries() > 1) {
//				log.warn("MultiPolygons with more then 1 Geometry ignored!");
//				continue;
//			}
//			Polygon polygon = (Polygon) multiPolygon.getGeometryN(0);
			polygons.add(feature);
	}
	
		return polygons;
	}
}

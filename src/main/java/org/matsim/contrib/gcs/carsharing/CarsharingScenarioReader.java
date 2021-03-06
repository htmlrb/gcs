package org.matsim.contrib.gcs.carsharing;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Stack;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.gcs.carsharing.core.CarsharingStation;
import org.matsim.contrib.gcs.carsharing.core.CarsharingVehicle;
import org.matsim.contrib.gcs.carsharing.impl.CarsharingStationFactory;
import org.matsim.contrib.gcs.carsharing.impl.CarsharingVehicleFactory;
import org.matsim.contrib.gcs.utils.CarsharingUtils;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.core.utils.io.MatsimXmlParser;
import org.matsim.core.utils.io.UncheckedIOException;
import org.xml.sax.Attributes;

/**
 * 
 * @author haitam
 *
 */
public class CarsharingScenarioReader extends MatsimXmlParser {
	
	public static Logger logger = Logger.getLogger(CarsharingScenarioReader.class);
	
	public static String CARSHARING_SCENARIO = "carsharingscenario";
	public static String STATIONTYPE = "stationtype";
	public static String VEHICLETYPE = "vehicletype";
	public static String STATIONS = "stations";
	public static String STATION = "station";
	public static String VEHICLES = "vehicles";
	public static String VEHICLE = "vehicle";
	public static String ID = "id";
	public static String NAME = "name";
	public static String X = "x";
	public static String Y = "y";
	public static String CAPACITY = "capacity";
	public static String CLASSNAME = "classname";

	private final CarsharingScenario carsharing;
	private final Network network;
	private final Map<String, CarsharingStation> stations;
	private final Scenario scenario;

	
	public CarsharingScenarioReader(CarsharingScenario carsharing, Scenario scenario) {
		super();
		this.carsharing = carsharing;
		this.scenario = scenario;
		this.network = carsharing.getCarNetwork();
		this.stations = new HashMap<String, CarsharingStation>();
	}

	@Override
	public void startTag(String name, Attributes atts, Stack<String> context) {
		
		// BODY
		if(name.equals(STATION)) {
			Coord coord = new Coord(Double.parseDouble(atts.getValue(X)) , Double.parseDouble(atts.getValue(Y)));
			CarsharingStation newS = CarsharingStationFactory.
					stationBuilder(scenario, atts.getValue(ID), coord).
					setName(atts.getValue(NAME)).
					setType(atts.getValue(STATIONTYPE)).
					setCapacity(Integer.parseInt(atts.getValue(CAPACITY))).
					build();
			this.carsharing.getStations().put(newS.facility().getId(), newS);
			stations.put(atts.getValue(ID), newS);
		}
		
		if(name.equals(VEHICLE)) {
			CarsharingVehicle newV = CarsharingVehicleFactory.
					vehicleBuilder(scenario, atts.getValue(ID)).
					setName(atts.getValue(NAME)).
					setType(atts.getValue(STATIONTYPE)).
					build();
			if(stations.get(atts.getValue(STATION)) != null) {
				carsharing.getVehicles().put(newV.vehicle().getId(), newV);
				stations.get(atts.getValue(STATION)).addToDeployment(newV);
			}
		}


	}
	

	@Override
	public void endTag(String name, String content, Stack<String> context) {
		if (name.equals(CARSHARING_SCENARIO)) {
		}
	}
	
	
	public void readXml(String filename) {
		logger.info("read carsharing scenario");
		this.setValidating(false);
		try {
			parse(new FileInputStream(new File(filename)));
		} catch (UncheckedIOException | FileNotFoundException e) {
			e.printStackTrace();
		}
		logger.info("done");
	}
	
	/**
	 * 
	 * @param rawStationFile
	 */
	public void readRawV1(String rawStationFile, String rawStationCRS, int vehPerStation) {
		CoordinateTransformation CT = TransformationFactory.getCoordinateTransformation(rawStationCRS, scenario.getConfig().global().getCoordinateSystem());
		try {
			String sep = " ";
			BufferedReader reader = IOUtils.getBufferedReader(rawStationFile);
			String s = reader.readLine();
			String[] arr = s.split(sep);
			if(arr.length < 3) {
				sep = "\t";
				arr = s.split(sep);
			}
			
			String key0 = arr[0].replace("\"", "");
			String key1 = arr[1].replace("\"", "");
			String key2 = arr[2].replace("\"", "");
		    ArrayList<HashMap<String, String>> stations = new ArrayList<HashMap<String, String>>();
		    while((s = reader.readLine()) != null) {
		    	HashMap<String, String> l = new HashMap<String, String>();
		    	arr = s.split(sep);
		    	l.put(key0, arr[0]);
		    	l.put(key1, arr[1]);
		    	l.put(key2, arr[2]);
		    	stations.add(l);
		    }
		    
		    for(HashMap<String, String> row : stations) {
		    	double X = Double.parseDouble(row.get("lng")); // Longitude
		    	double Y = Double.parseDouble(row.get("lat")); // Latitude
		    	String id = row.get(key0);
		    	Coord coord = CT.transform(new Coord(X, Y));
		    	CarsharingStation newS = CarsharingStationFactory.
						stationBuilder(scenario, "stat.id." + id, coord).
						setCapacity(vehPerStation * 2).
						build();
		    	for (int k = 0; k < vehPerStation; k++) {
		    		CarsharingVehicle newV = CarsharingVehicleFactory.
							vehicleBuilder(scenario, "veh.id." + id + "." + k).
							build();
		    		this.carsharing.getVehicles().put(newV.vehicle().getId(), newV);
		    		newS.addToDeployment(newV);
		    	}
		    	this.carsharing.getStations().put(newS.facility().getId(), newS);
		    }
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * 
	 * @param rawStationFile
	 */
	public void readRawV2(String rawStationFile, String rawStationCRS, Map<String,Integer> header, String sep, double probVeh) {
		CoordinateTransformation CT = TransformationFactory.getCoordinateTransformation(rawStationCRS, scenario.getConfig().global().getCoordinateSystem());
		try {
			BufferedReader reader = IOUtils.getBufferedReader(rawStationFile);
			String s = reader.readLine();
			String[] arr = s.split(sep);	
			
		    int k = 0;
		    while((s = reader.readLine()) != null) {
		    	arr = s.split(sep);
		    	double X = Double.parseDouble(arr[header.get("lng")]); // Longitude
		    	double Y = Double.parseDouble(arr[header.get("lat")]); // Latitude
		    	Coord coord = CT.transform(new Coord(X, Y));
		    	int capacity = 20;
		    	if(header.get("capacity") != null) {
					capacity = Integer.parseInt(arr[header.get("capacity")]);
				}
		    	CarsharingStation newS = CarsharingStationFactory.
						stationBuilder(scenario, arr[header.get("stat.id")], coord).
						setCapacity(capacity).
						build();
		    	this.carsharing.getStations().put(newS.facility().getId(), newS);
		    	k++;
		    }
		    
		    CarsharingStation[] stations = new CarsharingStation[k];
		    double[] probSet = new double[k];
		    
		    int number_of_vehicles = (int) Math.round(probVeh * 
		    		construct_prob_set(stations, probSet, this.carsharing.getStations().values()));
		    
		    for(int i = 1; i <= number_of_vehicles; i++) {
		    	// Create Vehicle
		    	CarsharingVehicle newV = CarsharingVehicleFactory.
						vehicleBuilder(scenario, "veh.id." + i).
						build();
	    		this.carsharing.getVehicles().put(newV.vehicle().getId(), newV);
	    		
	    		// Assign it with stations randomly
		    	int index = CarsharingUtils.chooseProbability(probSet);
		    	stations[index].addToDeployment(newV);
		    	
		    	if(stations[index].deployment().size() >= stations[index].getCapacity()) {
		    		k--;
		    	}
		    	stations = new CarsharingStation[k];
			    probSet = new double[k];
		    	construct_prob_set(stations, probSet, this.carsharing.getStations().values());
		    }
		    
		    
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * 
	 * @param rawStationFile
	 */
	public void readRawV2(String rawStationFile, String rawStationCRS, Map<String,Integer> header, String sep, int nbrVeh, int staCap, int stop) {
		CoordinateTransformation CT = TransformationFactory.getCoordinateTransformation(rawStationCRS, scenario.getConfig().global().getCoordinateSystem());
		try {
			BufferedReader reader = IOUtils.getBufferedReader(rawStationFile);
			String s = reader.readLine();
			String[] arr = s.split(sep);	
			
		    int k = 0;
		    while((s = reader.readLine()) != null && k < stop) {
		    	arr = s.split(sep);
		    	double X = Double.parseDouble(arr[header.get("lng")]); // Longitude
		    	double Y = Double.parseDouble(arr[header.get("lat")]); // Latitude
		    	Coord coord = CT.transform(new Coord(X, Y));
		    	int capacity = staCap;
		    	if(header.get("capacity") != null) {
					capacity = Integer.parseInt(arr[header.get("capacity")]);
				}
		    	String station_id = "stat.id." + arr[header.get("stat.id")];
		    	String station_name = "stat.name." + arr[header.get("stat.id")];
		    	CarsharingStation newS = CarsharingStationFactory.
						stationBuilder(scenario, station_id, coord).
						setCapacity(capacity).
						setName(station_name).
						build();
		    	this.carsharing.getStations().put(newS.facility().getId(), newS);
		    	k++;
		    }
		    
		    CarsharingStation[] stations = new CarsharingStation[k];
		    double[] probSet = new double[k];
		    
		    construct_prob_set(stations, probSet, this.carsharing.getStations().values());
		    
		    for(int i = 1; i <= nbrVeh; i++) {
		    	// Create Vehicle
		    	String veh_id = "veh.id." + i;
		    	String veh_name = "veh.name." + i;
		    	CarsharingVehicle newV = CarsharingVehicleFactory.
						vehicleBuilder(scenario, veh_id).
						setName(veh_name).
						build();
	    		this.carsharing.getVehicles().put(newV.vehicle().getId(), newV);
	    		
	    		// Assign it with stations randomly
		    	int index = CarsharingUtils.chooseProbability(probSet);
		    	stations[index].addToDeployment(newV);
		    	
		    	if(stations[index].deployment().size() >= stations[index].getCapacity()) {
		    		k--;
		    	}
		    	stations = new CarsharingStation[k];
			    probSet = new double[k];
		    	construct_prob_set(stations, probSet, this.carsharing.getStations().values());
		    }
		    
		    
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	
	public void readRawV3(String rawStationFile, String rawStationCRS, Map<String,Integer> header, String sep, int totVeh, int totPark, boolean israndom) {
		CoordinateTransformation CT = TransformationFactory.getCoordinateTransformation(rawStationCRS, scenario.getConfig().global().getCoordinateSystem());
		try {
			BufferedReader reader = IOUtils.getBufferedReader(rawStationFile);
			String s = reader.readLine();
			String[] arr = s.split(sep);	
			
			HashMap<CarsharingStation, Double> stations_coef_map = new HashMap<CarsharingStation, Double>();
			HashMap<CarsharingStation, Double> stations_fleetcoef_map = new HashMap<CarsharingStation, Double>();
			LinkedList<CarsharingStation> stations_sorted_list = new LinkedList<CarsharingStation>();
		    while((s = reader.readLine()) != null) {
		    	arr = s.split(sep);
		    	double X = Double.parseDouble(arr[header.get("lng")]); // Longitude
		    	double Y = Double.parseDouble(arr[header.get("lat")]); // Latitude
		    	String station_id = arr[header.get("stat.id")];
		    	String station_name = arr[header.get("stat.id")];
		    	Coord coord = CT.transform(new Coord(X, Y));
		    	CarsharingStation newS = CarsharingStationFactory.
						stationBuilder(scenario, station_id, coord).
						setName(station_name).
						setCapacity(8).
						build();
		    	this.carsharing.getStations().put(newS.facility().getId(), newS);
		    	double parking_coef = Double.parseDouble(arr[header.get("parking_coef")]);
		    	double fleet_coef = Double.parseDouble(arr[header.get("fleet_coef")]);
		    	stations_coef_map.put(newS, parking_coef);
		    	stations_fleetcoef_map.put(newS, fleet_coef);
		    	stations_sorted_list.add(newS);
		    }
		    
			Collections.sort(stations_sorted_list, 
					new Comparator<CarsharingStation>() {
						@Override
						public int compare(CarsharingStation s1, CarsharingStation s2) {			
							return -1 * Double.compare(stations_coef_map.get(s1), stations_coef_map.get(s2));
						}});
		    
		    //int dep_size = this.carsharing.getStations().size();
		    //int min_capacity_perstation  = 1;
		    //int min_capacity = dep_size*min_capacity_perstation;
		    //int new_totPark = totPark - min_capacity;
		    int totcapacity = 0;
		    int real_totcapacity = 0;
		    Iterator<CarsharingStation> itcs1 = stations_sorted_list.iterator();
		    while(itcs1.hasNext() && totcapacity < totPark) {
		    	CarsharingStation cs = itcs1.next();
		    	double y_max_avg = stations_coef_map.get(cs); 
		    	double capacity = y_max_avg*totPark;
		    	if(totcapacity + capacity > totPark) {
		    		capacity = Math.abs(totPark-totcapacity);
		    	}
		    	double capacity_temp = Math.max(capacity, cs.getCapacity());
		    	//double capacity_temp = capacity;
		    	cs.setCapacity((int)Math.round(capacity_temp));
		    	totcapacity += capacity;
		    	real_totcapacity += cs.getCapacity();
		    }
		    System.out.println("REAL CAPACITY : " + real_totcapacity);
		    System.out.println("MIN CAPACITY : " + totcapacity);
		    
		    int index = 0;
		    Iterator<CarsharingStation> itcs = stations_sorted_list.iterator();
		    while(itcs.hasNext() && index < totVeh) {
		    	// Create Vehicle
		    	CarsharingStation cs = itcs.next();
		    	int fleet = 0;
		    	if(israndom) {
		    		fleet = MatsimRandom.getRandom().nextInt(cs.getCapacity());
		    	} else {
		    		fleet = (int) Math.round(stations_fleetcoef_map.get(cs)*totVeh);
		    	}
		    	int threshold = index+fleet;
		    	if(threshold > totVeh) {
		    		threshold = totVeh;
		    	}
		    	for(int i = index+1; i <= threshold; i++) {
			    	String veh_id = "veh.id." + i;
			    	String veh_name = "veh.name." + i;
			    	CarsharingVehicle newV = CarsharingVehicleFactory.
							vehicleBuilder(scenario, veh_id).
							setName(veh_name).
							build();
		    		this.carsharing.getVehicles().put(newV.vehicle().getId(), newV);
		    		cs.addToDeployment(newV);
		    	}
		    	index = threshold;
		    }

		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	
	
	private static double construct_prob_set(CarsharingStation[] stations, double[] probSet, Collection<CarsharingStation> allstation) {
		double total_freespace = 0;
		for(CarsharingStation sta : allstation) {
			double freespace = sta.getCapacity() - sta.deployment().size();
			if(freespace > 0) {
				total_freespace += freespace;
			}
		}
		int i = 0;
	    for(CarsharingStation sta : allstation) {
	    	double freespace = sta.getCapacity() - sta.deployment().size();
	    	if(freespace > 0) {
		    	stations[i] = sta;
		    	probSet[i] = freespace / total_freespace;
		    	i++;
	    	}
	    }
	    return total_freespace;
	}

}

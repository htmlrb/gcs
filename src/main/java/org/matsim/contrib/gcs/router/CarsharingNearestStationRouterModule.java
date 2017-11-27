package org.matsim.contrib.gcs.router;

import java.util.ArrayList;
import java.util.List;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contrib.gcs.carsharing.CarsharingManager;
import org.matsim.contrib.gcs.carsharing.core.CarsharingStationMobsim;
import org.matsim.contrib.gcs.replanning.CarsharingPlanModeCst;
import org.matsim.contrib.gcs.utils.CarsharingUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.router.DefaultRoutingModules;
import org.matsim.core.router.costcalculators.OnlyTimeDependentTravelDisutilityFactory;
import org.matsim.core.router.util.DijkstraFactory;
import org.matsim.core.router.util.LeastCostPathCalculatorFactory;
import org.matsim.core.trafficmonitoring.FreeSpeedTravelTime;
import org.matsim.core.utils.collections.QuadTree;
import org.matsim.facilities.Facility;

public class CarsharingNearestStationRouterModule extends CarsharingDefaultRouterModule {

	final double beelineWalkSpeed;
	final double beelineDistanceFactor;
	final double searchDistance;
	final QuadTree<CarsharingStationMobsim> qt;
	
	public CarsharingNearestStationRouterModule(Scenario scenario, CarsharingManager manager, String cssMode) {
		super(scenario, manager, cssMode);
		this.beelineWalkSpeed = manager.getConfig().getAccessWalkCalcRoute().getTeleportedModeSpeed();
		this.beelineDistanceFactor = manager.getConfig().getAccessWalkCalcRoute().getBeelineDistanceFactor();
		this.searchDistance = manager.getConfig().getSearchDistance();
		this.qt = manager.getStations().qtree();
	}
	
	
	@Override
	public List<? extends PlanElement> calcRoute(Facility fromFacility, Facility toFacility, double departureTime, Person person) {	
		
		Network network = scenario.getNetwork();
		LeastCostPathCalculatorFactory leastCostAlgoFactory = new DijkstraFactory();
        final OnlyTimeDependentTravelDisutilityFactory disutilityFactory = new OnlyTimeDependentTravelDisutilityFactory();
        final FreeSpeedTravelTime travelTime = new FreeSpeedTravelTime();
		final List<PlanElement> trip = new ArrayList<PlanElement>();	
		boolean aStation = (cssMode.equals(CarsharingPlanModeCst.directTrip) || cssMode.equals(CarsharingPlanModeCst.startTrip));
		boolean eStation = (cssMode.equals(CarsharingPlanModeCst.directTrip) || cssMode.equals(CarsharingPlanModeCst.endTrip));
		
		// *********************************************
		// NEAREST STATIONS
		CarsharingLocationInfo pickupLocation = getNearestStationToDeparture(fromFacility, aStation);
		CarsharingLocationInfo dropoffLocation = getNearestStationToArrival(toFacility, eStation, pickupLocation);
		if(pickupLocation.station == null || dropoffLocation.station == null) { 
			return new ArrayList<PlanElement>();
		} 
		// **************************************************
		
		//  **************************************************
		// ROUTING
		double routetime = departureTime;
		// Walk
		trip.add(CarsharingUtils.createWalkLeg(
				CarsharingRouterModeCst.cs_access_walk, 
				fromFacility, pickupLocation.facility, 
				pickupLocation.traveltime, 
				pickupLocation.distance));
		routetime += pickupLocation.traveltime;
		
		// Access
		if(aStation) {
			Activity a = CarsharingUtils.createAccessStationActivity(pickupLocation.facility, routetime, this.manager.getConfig().getInteractionOffset());
			trip.add(a);
			routetime += a.getMaximumDuration();
		}
		
		// drive
		List<? extends PlanElement> pes = DefaultRoutingModules.createPureNetworkRouter(
				TransportMode.car, 
				scenario.getPopulation().getFactory(),
          		network,
          		leastCostAlgoFactory.createPathCalculator(network, disutilityFactory.createTravelDisutility(travelTime), travelTime)
          		).calcRoute(pickupLocation.facility, dropoffLocation.facility, routetime, person);
		trip.add(CarsharingUtils.createDriveLeg(pickupLocation.facility, dropoffLocation.facility, pes, null));
		routetime += ((Leg)trip.get(trip.size()-1)).getTravelTime();
		
		// Egress
		if(eStation) {
			Activity a = CarsharingUtils.createEgressStationActivity(dropoffLocation.facility, routetime, this.manager.getConfig().getInteractionOffset());
			trip.add(a);		
			routetime += a.getMaximumDuration();
		}
		
		// Walk
		trip.add(CarsharingUtils.createWalkLeg(
				CarsharingRouterModeCst.cs_egress_walk, 
				dropoffLocation.facility, 
				toFacility, 
				dropoffLocation.traveltime, 
				dropoffLocation.distance));
		routetime += dropoffLocation.traveltime;
		//  **************************************************
		
		return trip;
	}
	
	/**
	 * 
	 * @param coordinate
	 * @param departureTime
	 * @return
	 */
	public CarsharingLocationInfo getNearestStationToDeparture(Facility fromFacility, boolean hasAccessStation) {
		
		CarsharingLocationInfo pickupLocation = new CarsharingLocationInfo();
		CarsharingStationMobsim nearestStation = null;
		double tt = 0.0;
		double td = 0.0;
		if(hasAccessStation) { 
			for(CarsharingStationMobsim station: this.qt.getDisk(fromFacility.getCoord().getX(), fromFacility.getCoord().getY(), this.searchDistance)) {
				final double accessDist = NetworkUtils.getEuclideanDistance(fromFacility.getCoord(), station.facility().getCoord());
				final double deptime = accessDist * this.beelineWalkSpeed;
				final double distance = accessDist * this.beelineDistanceFactor;	
				if(nearestStation == null || td > distance) {
					nearestStation = station;
					td = distance;
					tt = deptime;				
				} 
			}
		} 
		
		pickupLocation.station = nearestStation;
		pickupLocation.facility = fromFacility;
		pickupLocation.distance = td;
		pickupLocation.traveltime = tt;

		return pickupLocation;
	}

	
	/**
	 * 
	 * @param coordinate
	 * @param arrivalTime
	 * @param excludedDepartureStation
	 * @return
	 */
	public CarsharingLocationInfo getNearestStationToArrival(Facility toFacility, boolean hasEgressStation, CarsharingLocationInfo excludedDepartureStation) {
		CarsharingLocationInfo dropoffLocation = new CarsharingLocationInfo();
		CarsharingStationMobsim nearestStation = null;
		double tt = 0.0;
		double td = 0.0;
		if(hasEgressStation) {
			for(CarsharingStationMobsim station: this.qt.getDisk(toFacility.getCoord().getX(), toFacility.getCoord().getY(), this.searchDistance)) {
				if(excludedDepartureStation.station != null && excludedDepartureStation.station.equals(station)) {
					continue;
				}
				final double accessDist = NetworkUtils.getEuclideanDistance(toFacility.getCoord(), station.facility().getCoord());
				final double arrtime = accessDist * this.beelineWalkSpeed;
				final double distance = accessDist * this.beelineDistanceFactor;	
				if(nearestStation == null || td > distance) {
					nearestStation = station;
					td = distance;
					tt = arrtime;		
				} 
			} 
		} 
		dropoffLocation.station = nearestStation;
		dropoffLocation.facility = toFacility;
		dropoffLocation.distance = td;
		dropoffLocation.traveltime = tt;
		return dropoffLocation;
	}

	public static class CarsharingLocationInfo {
		public CarsharingStationMobsim station = null;
		public Facility facility = null;
		public double distance = 0.0;
		public double traveltime = 0.0;
	}
	
	
}
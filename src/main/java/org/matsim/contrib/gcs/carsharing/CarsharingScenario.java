package org.matsim.contrib.gcs.carsharing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.gcs.carsharing.core.CarsharingStation;
import org.matsim.contrib.gcs.carsharing.core.CarsharingVehicle;
import org.matsim.contrib.gcs.config.CarsharingConfigGroup;
import org.matsim.contrib.gcs.router.CarsharingRouterUtils;
import org.matsim.contrib.gcs.utils.CarsharingUtils;
import org.matsim.core.config.Config;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ActivityParams;
import org.matsim.core.config.groups.QSimConfigGroup.VehiclesSource;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.TransportModeNetworkFilter;
import org.matsim.facilities.ActivityFacility;
import org.matsim.vehicles.Vehicle;


public class CarsharingScenario {
	
	// Main Class
	private final Map<Id<ActivityFacility>, CarsharingStation> stations;
	private final Map<Id<Vehicle>, CarsharingVehicle> vehicles;
	
	private final CarsharingConfigGroup csconfig;
	private final Scenario scenario;
	private final Network carNetwork;
	
	private String rootdir = null;
	
	public CarsharingScenario(Scenario scenario, String rootdir) {
		this.carNetwork = NetworkUtils.createNetwork();		
		TransportModeNetworkFilter filter = new TransportModeNetworkFilter(scenario.getNetwork());
		Set<String> modes = new HashSet<>();
		modes.add(TransportMode.car);
		filter.filter(this.carNetwork, modes);
		
		this.rootdir = rootdir;
		this.scenario = scenario;
		CarsharingConfigGroup ccg = (CarsharingConfigGroup) scenario.getConfig().getModule(CarsharingConfigGroup.GROUP_NAME);
		if(ccg != null) {
			this.csconfig = ccg;
		} else {
			this.csconfig = setUpConfig(scenario.getConfig());
		}
		this.stations = new HashMap<Id<ActivityFacility>, CarsharingStation>();
		this.vehicles = new HashMap<Id<Vehicle>, CarsharingVehicle>();
	}
	
	public Map<Id<Vehicle>, CarsharingVehicle> getVehicles() { return this.vehicles; }
	public Map<Id<ActivityFacility>, CarsharingStation> getStations() { return this.stations; }
	public CarsharingConfigGroup getConfig() { return this.csconfig; }
	public Network getCarNetwork() { return this.carNetwork; }
	public Scenario getScenario() { return this.scenario; }
	
	
	
	CarsharingConfigGroup setUpConfig(Config config) {
		// carsharing config
		CarsharingConfigGroup csConf = new CarsharingConfigGroup(config);
		csConf.setActivateModule(true);
		csConf.setSearchDistance(200.0);
		csConf.setRentalRatePerMin(-1.0);
		csConf.setConstantRate(0.0);
		
		csConf.getDriveCalcScore().setMonetaryDistanceRate(0.0);
		csConf.getDriveCalcScore().setMarginalUtilityOfTraveling(-1.0);
		csConf.getDriveCalcScore().setMarginalUtilityOfDistance(0.0);
		csConf.getDriveCalcScore().setConstant(0.0);
		
		csConf.getAccessWalkCalcScore().setMarginalUtilityOfTraveling(-1.0);
		csConf.getAccessWalkCalcScore().setMarginalUtilityOfDistance(0.0);
		csConf.getAccessWalkCalcScore().setConstant(0.0);
		
		csConf.getEgressWalkCalcScore().setMarginalUtilityOfTraveling(-1.0);
		csConf.getEgressWalkCalcScore().setMarginalUtilityOfDistance(0.0);
		csConf.getEgressWalkCalcScore().setConstant(0.0);
		
		csConf.getEgressWalkCalcRoute().setBeelineDistanceFactor(1.3);
		csConf.getEgressWalkCalcRoute().setTeleportedModeSpeed(3.0/3.6);
		csConf.getAccessWalkCalcRoute().setBeelineDistanceFactor(1.3);
		csConf.getAccessWalkCalcRoute().setTeleportedModeSpeed(3.0/3.6);
		
		
		//carsharing
		// log
		csConf.setTripsLogFile(rootdir + "/log/trips.log");
		csConf.setChargingLogFile(rootdir + "/log/charge.log");
		csConf.setBookingLogFile(rootdir + "/log/booking.log");
		csConf.setRelocationLogFile(rootdir + "/log/relocation.log");
		csConf.setLogDir(rootdir+"/log");
		
		
		
		config.addModule(csConf);
		// matsim config
		ActivityParams station = new ActivityParams(CarsharingRouterUtils.ACTIVITY_TYPE_NAME);
		station.setClosingTime(CarsharingUtils.toSecond(23,59,59));
		station.setOpeningTime(CarsharingUtils.toSecond(0,0,0));
		station.setTypicalDuration(CarsharingUtils.toSecond(0,1,0));
		config.planCalcScore().addActivityParams(station);
		ArrayList<String> mainmodes = new ArrayList<String>(config.qsim().getMainModes());
		mainmodes.add(CarsharingRouterUtils.cs_drive);
		config.qsim().setMainModes(mainmodes);
		
		config.qsim().setVehiclesSource(VehiclesSource.modeVehicleTypesFromVehiclesData);
		
		/*scenario.getConfig().planCalcScore().getOrCreateModeParams(CarsharingRouterUtils.cs_walk).setMonetaryDistanceRate(0.0);
		scenario.getConfig().planCalcScore().getOrCreateModeParams(CarsharingRouterUtils.cs_walk).setMarginalUtilityOfTraveling(-0.6*60+6);
		scenario.getConfig().planCalcScore().getOrCreateModeParams(CarsharingRouterUtils.cs_walk).setMarginalUtilityOfDistance(0.0);
		scenario.getConfig().planCalcScore().getOrCreateModeParams(CarsharingRouterUtils.cs_walk).setConstant(11.29);*/
		
		scenario.getConfig().transit().setUseTransit(true);
		HashSet<String> tmodes = new HashSet<String>(scenario.getConfig().transit().getTransitModes());
		tmodes.add(CarsharingRouterUtils.cs_pt);
		scenario.getConfig().transit().setTransitModes(tmodes);
		scenario.getConfig().transitRouter().setAdditionalTransferTime(2*60.0);
		scenario.getConfig().transitRouter().setExtensionRadius(200.0);
		scenario.getConfig().transitRouter().setMaxBeelineWalkConnectionDistance(100.0);
		scenario.getConfig().transitRouter().setSearchRadius(1000.0);
		ArrayList<String> subModes = new ArrayList<String>(Arrays.asList(scenario.getConfig().subtourModeChoice().getModes()));
		subModes.add(CarsharingRouterUtils.cs_pt);
		scenario.getConfig().subtourModeChoice().setModes(subModes.toArray(new String[0]));
		return csConf;
	}

}

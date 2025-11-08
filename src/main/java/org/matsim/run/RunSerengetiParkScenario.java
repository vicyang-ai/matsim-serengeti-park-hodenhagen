/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2017 by the members listed in the COPYING,        *
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

package org.matsim.run;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.RoutingConfigGroup;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryLogging;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.lanes.Lane;
import org.matsim.lanes.LanesFactory;
import org.matsim.lanes.LanesToLinkAssignment;
import org.matsim.prepare.CreatePopulation;

/**
* @author ikaddoura
*/

public final class RunSerengetiParkScenario {

	private static final Logger log = LogManager.getLogger(RunSerengetiParkScenario.class );


	// Demand
	// ts: Besetzungsgrad should be rather 3,4 than 4. Peak days are rather 15000 to 17000 people. Not sure what the 0.9 factor says...

	// tour bus: 0.6*10000 = 6000; by car: 6000 * 0.9 = 5400; cars: 5400/4 = 1350
	// own car: 0.4*10000 = 4000; by car: 4000 * 0.9 = 3600; cars: 3600/4 = 900
	private final static int serengetiParkVehicles = 1000;
	private final static int serengetiCarparkVehicles = 675;
	private final static int wasserlandCarparkVehicles = 675;
	
	// Supply
	// 45 sec per veh --> 3600/45 = 80 veh/h per check-in lane
	private final static int capacityPerCheckInBooth = 80;
	private final static int numberOfNorthCheckInBooths = 4;
	private final static int numberOfSouthCheckInBooths = 3;

	public static void main(String[] args) throws IOException {
		
		for (String arg : args) {
			log.info( arg );
		}
		
		if ( args.length==0 ) {
			args = new String[] {"./scenarios/serengeti-park-v1.0/input/serengeti-park-config-v1.0-with-pt.xml"}  ;
		}

		Config config = prepareConfig( args ) ;
		Scenario scenario = prepareScenario( config ) ;
		Controler controler = prepareControler( scenario ) ;
		controler.run();
	}

	public static Controler prepareControler( Scenario scenario ) {
		
		Gbl.assertNotNull(scenario);
		
		final Controler controler = new Controler( scenario );
		
//		controler.addOverridingModule( new OTFVisLiveModule() ) ;
		
		return controler;
	}
	
	public static Scenario prepareScenario( Config config ) throws IOException {
		Gbl.assertNotNull( config );
		
		final Scenario scenario = ScenarioUtils.createScenario( config );
		ScenarioUtils.loadScenario(scenario);		
		
		Set<Id<Link>> forCarsRestrictedLinks = new HashSet<>(Arrays.asList(
				
				// bus lane!
				Id.createLinkId("3622817410000f"), Id.createLinkId("3622817410000r"),
				Id.createLinkId("3622817520000f"), Id.createLinkId("3622817520000r"),
				
				// longterm parking I guess
				Id.createLinkId("7232641180000f")
				
				));
		
		Set<Id<Link>> kassenLinks = new HashSet<>(Arrays.asList(
				// north
				Id.createLinkId("3624560720003f"),
				Id.createLinkId("3624560680002f"),
				Id.createLinkId("3624560690002f"),
				Id.createLinkId("3624560660002f"),
				
				// south
				Id.createLinkId("5297562640002f"),
				Id.createLinkId("2184588460002f"),
				Id.createLinkId("2184588440002f")));

		for (Link link : scenario.getNetwork().getLinks().values()) {
			
			if (forCarsRestrictedLinks.contains(link.getId())) {
				link.setFreespeed(0.001);
				link.setCapacity(0.);
			}
			
			// use single check-in link instead of several parallel check-in links...
			if (kassenLinks.contains(link.getId())) {
				link.setFreespeed(0.001);
				link.setCapacity(0.);
			}
			
			// keep just one link for the north check-in area
			if (link.getId().toString().equals("3624560720003f")) {
				link.setCapacity(capacityPerCheckInBooth * numberOfNorthCheckInBooths);
				link.setFreespeed(2.7777);
				
				// account for the other check-in links
				link.setLength(30. * (numberOfNorthCheckInBooths - 1));
				link.setNumberOfLanes(numberOfNorthCheckInBooths);

			}
			
			// keep just one link for the south check-in area
			if (link.getId().toString().equals("5297562640002f")) {
				link.setCapacity(capacityPerCheckInBooth * numberOfSouthCheckInBooths);
				link.setFreespeed(2.7777);

				// account for the other check-in links
				link.setLength(40. * (numberOfSouthCheckInBooths - 1));
				link.setNumberOfLanes(numberOfSouthCheckInBooths);
			}					
		}
				
		
		Id<Link> linkIdBeforeIntersection = Id.createLinkId("1325764790002f");
		Id<Link> nextLinkIdLeftTurn = Id.createLinkId("3624560720000f");
		Id<Link> nextLinkIdStraight = Id.createLinkId("1325764790003f");
		Id<Lane> leftTurnLaneId = Id.create("1325764790002f_left", Lane.class);
		Id<Lane> straightLaneId = Id.create("1325764790002f_straight", Lane.class);

		LanesFactory factory = scenario.getLanes().getFactory();
		// add lanes for link "1325764790002f"
		{
			LanesToLinkAssignment laneLinkAssignment = factory.createLanesToLinkAssignment(linkIdBeforeIntersection);

			Lane laneIn = factory.createLane(Id.create("1325764790002f_in", Lane.class));
			laneIn.addToLaneId(leftTurnLaneId);
			laneIn.addToLaneId(straightLaneId);
			laneIn.setStartsAtMeterFromLinkEnd(165.67285516126265);
			laneIn.setCapacityVehiclesPerHour(720. * 4);
			laneIn.setNumberOfRepresentedLanes(4.0);
			laneLinkAssignment.addLane(laneIn);

			// TODO: outgoing lanes must start after the in lane
			Lane lane0 = factory.createLane(leftTurnLaneId);
			lane0.addToLinkId(nextLinkIdLeftTurn); // turn left towards check-in link
			lane0.setStartsAtMeterFromLinkEnd(165.67285516126265 - 1);
			lane0.setCapacityVehiclesPerHour(720.);
			laneLinkAssignment.addLane(lane0);

			Lane lane1 = factory.createLane(straightLaneId);
			lane1.addToLinkId(nextLinkIdStraight); // straight!
			lane1.setStartsAtMeterFromLinkEnd(165.67285516126265 - 1);
			lane1.setCapacityVehiclesPerHour(720. * 3.0);
			lane1.setNumberOfRepresentedLanes(3.0);
			laneLinkAssignment.addLane(lane1);

			scenario.getLanes().addLanesToLinkAssignment(laneLinkAssignment);
		}		
		
		CreatePopulation createPopulation = new CreatePopulation(serengetiParkVehicles, serengetiCarparkVehicles, wasserlandCarparkVehicles);
		createPopulation.run(scenario);
		
		return scenario;
	}

	public static Config prepareConfig( String [] args, ConfigGroup... customModules ){
		
		OutputDirectoryLogging.catchLogEntries();
		
		String[] typedArgs = Arrays.copyOfRange( args, 1, args.length );
		
		final Config config = ConfigUtils.loadConfig( args[ 0 ], customModules );
		
//		config.controler().setRoutingAlgorithmType( FastAStarLandmarks );
				
		config.routing().setRoutingRandomness( 0. );
						
		config.routing().setAccessEgressType(RoutingConfigGroup.AccessEgressType.accessEgressModeToLink);
		config.qsim().setUsingTravelTimeCheckInTeleportation( true );
		
		ConfigUtils.applyCommandline( config, typedArgs ) ;

		return config ;
	}

}


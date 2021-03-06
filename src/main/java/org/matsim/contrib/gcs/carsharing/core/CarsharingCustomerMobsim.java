package org.matsim.contrib.gcs.carsharing.core;

import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contrib.gcs.operation.model.CarsharingUserChoiceModel;

public interface CarsharingCustomerMobsim extends CarsharingAgent {

	CarsharingUserChoiceModel decision();
	CarsharingCustomerStatus status();
	void setStatus(PlanElement pe, CarsharingBookingRecord bookRec);
	public interface CarsharingCustomerStatus {
		PlanElement getPlanElement();
		CarsharingBookingRecord getOngoingRental();
		void setPlanElement(PlanElement planElement);
		void setOngoingRental(CarsharingBookingRecord offer);
	}
}

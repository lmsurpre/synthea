package org.mitre.synthea.modules;

import java.util.Map;

import org.mitre.synthea.engine.Module;
import org.mitre.synthea.helpers.Attributes;
import org.mitre.synthea.helpers.Attributes.Inventory;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Payer;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.behaviors.IPayerFinder;

public class HealthInsuranceModule extends Module {

  // Load properties insurance numbers.
  public static long mandateTime
      = Utilities.convertCalendarYearsToTime(Integer.parseInt(Config
      .get("generate.insurance.mandate.year", "2006")));
  public static double mandateOccupation =
      Config.getAsDouble("generate.insurance.mandate.occupation", 0.2);
  public static double medicaidLevel = 1.33
          * Config.getAsDouble("generate.demographics.socioeconomic.income.poverty", 11000);
  public static String MEDICARE =
      Config.get("generate.payers.insurance_companies.medicare", "Medicare");
  public static String MEDICAID =
      Config.get("generate.payers.insurance_companies.medicaid", "Medicaid");
  public static String DUAL_ELIGIBLE =
      Config.get("generate.payers.insurance_companies.dual_eligible", "Dual Eligible");
  public static String INSURANCE_STATUS = "insurance_status";

  /**
   * HealthInsuranceModule constructor.
   */
  public HealthInsuranceModule() {}

  public Module clone() {
    return this;
  }

  /**
   * Process this HealthInsuranceModule with the given Person at the specified
   * time within the simulation.
   *
   * @param person the person being simulated
   * @param time   the date within the simulated world
   * @return completed : whether or not this Module completed.
   */
  @Override
  public boolean process(Person person, long time) {
    if (!person.alive(time)) {
      return true;
    }

    // If the payerHistory at the current age is null, they must get insurance for the new year.
    // Note: This means the person will check to change insurance yearly, just after their
    // birthday.
    Payer payerAtTime = person.coverage.getPayerAtTime(time);
    if (payerAtTime == null) {
      // Update their last payer with person's QOLS for that year.
      Payer lastPayer = person.coverage.getLastPayer();
      if (lastPayer != null) {
        lastPayer.addQols(person.getQolsForYear(Utilities.getYear(time) - 1));
      }
      // Determine the insurance for this person at this time.
      Payer newPayer = determineInsurance(person, time);
      Payer secondaryPayer = Payer.noInsurance;

      // If the payer is Medicare, they may buy supplemental insurance.
      if (Payer.getGovernmentPayer(MEDICARE) == newPayer && (person.rand() <= 0.8)) {
        // Buy supplemental insurance if it is affordable
        secondaryPayer = Payer.findPayer(person, null, time);
      }

      // Set this new payer at the current time for the person.
      person.coverage.setPayerAtTime(time, newPayer, secondaryPayer);

      // Update the new Payer's customer statistics.
      newPayer.incrementCustomers(person);
      if (Payer.noInsurance != secondaryPayer) {
        secondaryPayer.incrementCustomers(person);
      }

      // Set insurance attribute for module access
      String insuranceStatus = null;
      if (newPayer == Payer.noInsurance) {
        insuranceStatus = "none";
      } else if (Payer.getGovernmentPayers().contains(newPayer)) {
        insuranceStatus = "medicare"; // default to medicare when government payer
        if (newPayer.getName().equalsIgnoreCase("Medicaid")) {
          insuranceStatus = "medicaid";
        }
      } else {
        insuranceStatus = "private";
      }
      person.attributes.put(INSURANCE_STATUS, insuranceStatus);
    }

    // Checks if person has paid their premium this month. If not, they pay it.
    person.checkToPayMonthlyPremium(time);

    // java modules will never "finish"
    return false;
  }

  /**
   * Determine what insurance a person will get based on their attributes.
   *
   * @param person the person to cover
   * @param time   the current time to consider
   * @return the insurance that this person gets
   */
  private Payer determineInsurance(Person person, long time) {
    // Government payers
    Payer medicare = Payer.getGovernmentPayer(MEDICARE);
    Payer medicaid = Payer.getGovernmentPayer(MEDICAID);
    Payer dualPayer = Payer.getGovernmentPayer(DUAL_ELIGIBLE);

    Payer payerAtTime = person.coverage.getPayerAtTime(time);

    // If Medicare/Medicaid will accept this person, then it takes priority.
    if (medicare != null && medicaid != null
        && medicare.accepts(person, time)
        && medicaid.accepts(person, time)) {
      return dualPayer;
    } else if (medicare != null && medicare.accepts(person, time)) {
      return medicare;
    } else if (medicaid != null && medicaid.accepts(person, time)) {
      return medicaid;
    } else if (payerAtTime != null
        && IPayerFinder.meetsBasicRequirements(payerAtTime, person, null, time)) {
      // People will keep their previous year's insurance if they can.
      return payerAtTime;
    } else {
      // Randomly choose one of the remaining private payers.
      // Returns no_insurance if a person cannot afford any of them.
      return Payer.findPayer(person, null, time);
    }
  }

  /**
   * Populate the given attribute map with the list of attributes that this module
   * reads/writes with example values when appropriate.
   *
   * @param attributes Attribute map to populate.
   */
  public static void inventoryAttributes(Map<String, Inventory> attributes) {
    String m = HealthInsuranceModule.class.getSimpleName();
    Attributes.inventory(attributes, m, "pregnant", true, false, "Boolean");
    Attributes.inventory(attributes, m, "blindness", true, false, "Boolean");
    Attributes.inventory(attributes, m, "end_stage_renal_disease", true, false, "Boolean");
    Attributes.inventory(attributes, m, Person.GENDER, true, false, "F");
    Attributes.inventory(attributes, m, Person.OCCUPATION_LEVEL, true, false, "Low");
    Attributes.inventory(attributes, m, Person.INCOME, true, false, "1.0");
  }
}
package moa.classifiers.spdisc.meta;

import java.util.ArrayList;
import java.util.Date;

import org.joda.time.Days;
import org.joda.time.Instant;

import com.github.javacliparser.IntOption;
import com.yahoo.labs.samoa.instances.Instance;

import moa.core.Measurement;

public class WFL_OO_ORB_Oza extends OO_ORB_Oza{

	@Override
	public String getPurposeString() {
		return "Variation of oversampling on-line bagging of Wang et al IJCAI 2016 to use an example for training only once "
				+ "waitingTime days have passed since its creation. This waitingTime is the delay for receiving the label "
				+ "of this example."
				+ "One of the input attributes **after** the class attribute in the examples must be the date of the commit.";
	}

	private static final long serialVersionUID = 1L;

	public IntOption waitingTime = new IntOption("waitingTime", 'w',
			"The time (in days) we have to wait before using a commit as a training example in order to know whether "
					+ "it led to a defect or not.",
			90, 1, Integer.MAX_VALUE);

	public IntOption unixTimeStampIndex = new IntOption("unixTimeStampIndex", 'i',
			"The index of the input attribute containing the unix time stamp when the example was created (starting from 0). "
					+ "It must be an attribute index ***after*** the class attribute index.",
			15, 0, Integer.MAX_VALUE);

	// training examples that are waiting for waitingTime days before they are
	// used for training
	// PS: this is probably not the best data structure to be used here
	protected ArrayList<Instance> trainingExamplesQueue = null;

	public WFL_OO_ORB_Oza() {
		super();
		trainingExamplesQueue = new ArrayList<Instance>(waitingTime.getValue());
	}

	@Override
	public void resetLearningImpl() {
		super.resetLearningImpl();
		trainingExamplesQueue = new ArrayList<Instance>(waitingTime.getValue());
	}

	// This method does not do any training here. It just stores instances for
	// training later on.
	// We only train right before giving a prediction, so that
	// we can update the models with all examples produced at lest waitingTime
	// days ago.
	@Override
	public void trainOnInstanceImpl(Instance inst) {
		
		if(inst.classValue() == 1){
			Instance trainInst = inst.copy();
			int numberRepetitions = 0;
			for (int i = 0; i < pastNonDefectiveInstances.size(); i++) {
				if (isSameInstance(trainInst, pastNonDefectiveInstances.get(i))) {
					numberRepetitions++;
				}
			}
			// if more than 3 instances are similar, this defective
			// instance is noisy and then discarded
			if (numberRepetitions < 3) {
				//trainInst.deleteAttributeAt(unixTimeStampIndex.getValue()); // remove the time stamp before using the example for training
				
				validationPool.add(trainInst.copy());
				
				super.trainOnInstanceImpl(trainInst);
				
			}
			
		}else{
			trainingExamplesQueue.add(inst.copy());	
		}
	}

	// Method to allow classes that inherit from this one to call the super
	// method from this class
	public void superTrainOnInstanceImpl(Instance inst) {
		super.trainOnInstanceImpl(inst);
	}

	// Method to allow classes that inherit from this one to call the super
	// method from this class
	public double[] superGetVotesForInstance(Instance inst) {
		return super.getVotesForInstance(inst);
	}

	@Override
	public double[] getVotesForInstance(Instance inst) {

		if(idxTimestamp == -1){
			idxTimestamp = unixTimeStampIndex.getValue();
		}
		
		if (firstTimeStamp < 0) {
			firstTimeStamp = (long) inst.value(unixTimeStampIndex.getValue());
		}

		this.currentTimeStamp = (long) inst.value(unixTimeStampIndex.getValue());
		
		//inst.deleteAttributeAt(16);
		
		trainOnInstancesWaitingTime(inst);
		
		// Attribute attTmp = inst.attribute(unixTimeStampIndex.getValue());
		Instance instTmp = inst.copy();
		long instant = (long) instTmp.value(unixTimeStampIndex.getValue());
		instTmp.deleteAttributeAt(unixTimeStampIndex.getValue()); // remove the
																	// time
																	// stamp
																	// attribute
																	// before
																	// predicting
																	// the
																	// instance

		double[] ret = super.getVotesForInstance(instTmp); 
		
		return ret;
	}

	// inst is the current instance to be predicted. It is used here to
	// determine which examples
	// can already be used for training, based on the waitingTime between the
	// production of those examples
	// and of the new example to be predicted.
	protected void trainOnInstancesWaitingTime(Instance inst) {

		while (trainingExamplesQueue.size() != 0) {

			Instance trainingExampleToPop = trainingExamplesQueue.get(0);
			Instant timeTrainingExampleToPop = new Instant(
					(long) trainingExampleToPop.value(unixTimeStampIndex.getValue()) * 1000);
			
			Instant timeTestingInstance = new Instant(currentTimeStamp * 1000);

			Days daysWaited = Days.daysBetween(timeTrainingExampleToPop, timeTestingInstance);

			if (daysWaited.getDays() >= waitingTime.getValue()) {
				
				//TODO uncomment
				// add this instance to the validation pool
				validationPool.addElement(trainingExampleToPop.copy());

				// remove older instances from validation pool
				int idx = 0;
				while (idx <= (validationPool.size() - 1) && !validationPool.isEmpty()) {

					Instant timePoolInstance = new Instant(
							(long) validationPool.elementAt(idx).value(unixTimeStampIndex.getValue()) * 1000);

					Days daysWaitedValPool = Days.daysBetween(timePoolInstance, timeTestingInstance);
					// remove instances older than twice the waiting time
					if (daysWaitedValPool.getDays() <= (2 * waitingTime.getValue())) {
						break;
					}

					idx++;
				}

				for (int i = 0; i < idx; i++) {
					validationPool.remove(0);
				}

				// if it is non defective instance, store in the array of non
				// defective reference instances
				if (trainingExampleToPop.classValue() == 0) {
					pastNonDefectiveInstances.add(trainingExampleToPop.copy());
					super.trainOnInstanceImpl(trainingExampleToPop);
					trainingExamplesQueue.remove(0);
				} else {
					
					// check how many non defective instances are similar to the
					// current defective instance
					int numberRepetitions = 0;
					for (int i = 0; i < pastNonDefectiveInstances.size(); i++) {
						if (isSameInstance(trainingExampleToPop, pastNonDefectiveInstances.get(i))) {
							numberRepetitions++;
						}
					}
					// if more than 3 instances are similar, this defective
					// instance is noisy and then discarded
					if (numberRepetitions < 3) {
						super.trainOnInstanceImpl(trainingExampleToPop);
						trainingExamplesQueue.remove(0);
					}else{
						trainingExamplesQueue.remove(0);
						idxTr++;
					}
				
					
				}
			} else { // if the current training example has less than
						// waitingTime, all examples after it will also have.
						// So, we can return.
				break;
			}

		}
	}

	// receive instances with the unixtimestamp
	private static boolean isSameInstance(Instance ins, Instance ins2) {

		boolean ret = true;

		for (int i = 0; i < ins.numAttributes() - 2; i++) {
			if (ins.value(i) != ins2.value(i)) {
				ret = false;
				break;
			}
		}

		return ret;
	}

	@Override
	protected Measurement[] getModelMeasurementsImpl() {
		Measurement[] measure = super.getModelMeasurementsImpl();
		Measurement[] measurePlus = new Measurement[measure.length + 4];
		for (int i = 0; i < measure.length; ++i) {
			measurePlus[i] = measure[i];
		}

		measurePlus[measure.length] = new Measurement("training queue size", trainingExamplesQueue.size());
		measurePlus[measure.length + 1] = new Measurement("recall0 Validation", super.recallVal0);
		measurePlus[measure.length + 2] = new Measurement("recall1 Validation", super.recallVal1);
		measurePlus[measure.length + 3] = new Measurement("time stamp", super.currentTimeStamp);

		return measurePlus;

	}
	
}
